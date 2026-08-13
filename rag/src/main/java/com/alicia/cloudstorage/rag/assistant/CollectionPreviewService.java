package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CollectionPreviewService {

    private final CollectionPreviewPort collectionPreviewPort;
    private final int maxPreviewItems;
    private final int maxScanItems;
    private final CollectionActionSnapshotStore snapshotStore;

    @Autowired
    public CollectionPreviewService(
            CollectionPreviewPort collectionPreviewPort,
            CollectionActionSnapshotStore snapshotStore,
            @Value("${alicia.rag.collection-preview.max-items:20}") int maxPreviewItems,
            @Value("${alicia.rag.collection-preview.max-scan-items:500}") int maxScanItems
    ) {
        this.collectionPreviewPort = collectionPreviewPort;
        this.snapshotStore = snapshotStore;
        this.maxPreviewItems = Math.max(1, Math.min(500, maxPreviewItems));
        this.maxScanItems = Math.max(this.maxPreviewItems, maxScanItems);
    }

    public CollectionPreviewService(
            CollectionPreviewPort collectionPreviewPort,
            int maxPreviewItems,
            int maxScanItems
    ) {
        this(collectionPreviewPort, null, maxPreviewItems, maxScanItems);
    }

    public IntentRecognitionResponse apply(IntentRecognitionResponse response, String authorizationHeader) {
        if (response == null || response.actionPlan() == null) {
            return response;
        }
        ActionPlan plan = response.actionPlan();
        if (!"collection".equals(plan.planKind()) || !"collection_review_required".equals(plan.status())) {
            return response;
        }

        Map.Entry<String, ActionPlanBinding> sourceEntry = sourceCollectionBinding(plan);
        if (sourceEntry == null || sourceEntry.getValue().filter().isEmpty()) {
            return response;
        }

        CollectionPreviewResult preview = collectionPreviewPort.preview(new CollectionPreviewRequest(
                plan.actionType(),
                sourceEntry.getValue().filter(),
                maxPreviewItems,
                maxScanItems,
                authorizationHeader
        ));
        if ("not_requested".equals(preview.status())) {
            return response;
        }
        return response.withActionPlan(enrichPlan(
                plan,
                sourceEntry.getKey(),
                preview,
                authorizationHeader
        ));
    }

    private Map.Entry<String, ActionPlanBinding> sourceCollectionBinding(ActionPlan plan) {
        for (Map.Entry<String, ActionPlanBinding> entry : plan.bindings().entrySet()) {
            if ("source_collection".equals(entry.getValue().kind())) {
                return entry;
            }
        }
        return null;
    }

    private ActionPlan enrichPlan(
            ActionPlan plan,
            String sourceKey,
            CollectionPreviewResult preview,
            String authorizationHeader
    ) {
        Map<String, ActionPlanBinding> bindings = new LinkedHashMap<>(plan.bindings());
        ActionPlanBinding source = bindings.get(sourceKey);
        Map<String, Object> filter = new LinkedHashMap<>(source.filter());
        List<CandidateItem> displayCandidates = preview.candidates();
        if (snapshotStore != null && "preview_ready".equals(preview.status()) && preview.exactCount()) {
            String snapshotId = snapshotStore.save(plan.planId(), authorizationHeader, preview.candidates());
            filter.put("snapshotId", snapshotId);
            filter.put("snapshotCount", preview.totalCount());
            displayCandidates = preview.candidates().stream().limit(maxPreviewItems).toList();
        }
        bindings.put(sourceKey, new ActionPlanBinding(
                source.key(),
                source.kind(),
                bindingStatus(preview.status()),
                source.query(),
                null,
                displayCandidates,
                preview.totalCount(),
                Map.copyOf(filter)
        ));

        String status = planStatus(plan.status(), preview, filter);
        List<ActionPlanMessage> messages = new ArrayList<>(plan.messages());
        if (!preview.message().isBlank()) {
            messages.add(new ActionPlanMessage(messageLevel(preview.status()), preview.status(), preview.message()));
        }

        return new ActionPlan(
                plan.version(),
                plan.planId(),
                status,
                plan.planKind(),
                plan.actionType(),
                plan.risk(),
                plan.confirmationLevel(),
                plan.locale(),
                bindings,
                plan.steps(),
                plan.requiredClientFields(),
                summary(plan.summary(), preview),
                List.copyOf(messages)
        );
    }

    private String planStatus(
            String currentStatus,
            CollectionPreviewResult preview,
            Map<String, Object> filter
    ) {
        if ("preview_ready".equals(preview.status())
                && preview.totalCount() > preview.candidates().size()
                && !filter.containsKey("snapshotId")) {
            return "binding_required";
        }
        return switch (preview.status()) {
            case "preview_ready" -> currentStatus;
            case "no_candidates", "missing_filter", "unsupported_filter", "preview_incomplete",
                 "missing_authorization", "storage_api_not_configured", "storage_api_error" -> "binding_required";
            default -> currentStatus;
        };
    }

    private String bindingStatus(String previewStatus) {
        return switch (previewStatus) {
            case "preview_ready" -> "resolved";
            case "no_candidates" -> "no_candidates";
            default -> "unresolved";
        };
    }

    private String messageLevel(String previewStatus) {
        return "preview_ready".equals(previewStatus) ? "info" : "warning";
    }

    private String summary(String currentSummary, CollectionPreviewResult preview) {
        if ("preview_ready".equals(preview.status())) {
            if (preview.totalCount() > preview.candidates().size()) {
                return "已匹配 " + preview.totalCount() + " 项，但当前只返回 "
                        + preview.candidates().size() + " 项预览，暂不生成批量执行草稿。";
            }
            return currentSummary + " 已匹配 " + preview.totalCount() + " 项，当前返回 "
                    + preview.candidates().size() + " 项预览。";
        }
        if (!preview.message().isBlank()) {
            return preview.message();
        }
        return currentSummary;
    }
}
