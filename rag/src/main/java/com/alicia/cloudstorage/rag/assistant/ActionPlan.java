package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ActionPlan(
        String version,
        String planId,
        String status,
        String planKind,
        String actionType,
        String risk,
        String confirmationLevel,
        String locale,
        Map<String, ActionPlanBinding> bindings,
        List<ActionPlanStep> steps,
        List<String> requiredClientFields,
        String summary,
        List<ActionPlanMessage> messages
) {
    public ActionPlan {
        version = version == null || version.isBlank() ? "action_plan_v1" : version;
        planId = planId == null ? "" : planId;
        status = status == null || status.isBlank() ? "understanding" : status;
        planKind = planKind == null || planKind.isBlank() ? "atomic" : planKind;
        actionType = actionType == null || actionType.isBlank() ? "none" : actionType;
        risk = risk == null || risk.isBlank() ? "none" : risk;
        confirmationLevel = confirmationLevel == null || confirmationLevel.isBlank() ? "none" : confirmationLevel;
        locale = locale == null || locale.isBlank() ? "zh-CN" : locale;
        bindings = bindings == null || bindings.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
        steps = steps == null ? List.of() : List.copyOf(steps);
        requiredClientFields = requiredClientFields == null ? List.of() : List.copyOf(requiredClientFields);
        summary = summary == null ? "" : summary;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static ActionPlan skipped(String status, String message) {
        return new ActionPlan(
                "action_plan_v1",
                "",
                status,
                "atomic",
                "none",
                "none",
                "none",
                "zh-CN",
                Map.of(),
                List.of(),
                List.of(),
                "",
                List.of(new ActionPlanMessage("info", status, message))
        );
    }
}
