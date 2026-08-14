package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSlotResolutionServiceTest {

    private final PendingSlotResolutionService service = new PendingSlotResolutionService();
    private IntentRecognitionService recognitionService;

    @BeforeEach
    void setUp() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRouter intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        recognitionService = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader
        );
    }

    @Test
    void resolvesStandaloneNameOnlyInsidePendingTargetSlot() {
        IntentRecognitionResponse pending = recognitionService.recognize("删除");
        IntentRecognitionResponse current = recognitionService.recognize("临时截图");

        PendingSlotResolutionService.Resolution resolution = service.resolve(
                "临时截图",
                conversation(pending),
                current
        );

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.values()).containsEntry("target_name", "临时截图");
        assertThat(resolution.semanticFrame().relation()).isEqualTo("SLOT_FILL");
        assertThat(resolution.semanticFrame().operation()).isEqualTo("DELETE");
        assertThat(resolution.semanticFrame().query().nameSurface()).isEqualTo("临时截图");
    }

    @Test
    void rejectsNewMutationCommandAndCollectionPhraseAsSlotValue() {
        IntentRecognitionResponse pendingDelete = recognitionService.recognize("删除");
        IntentRecognitionResponse ambiguousDelete = recognitionService.recognize("删除图片");
        IntentRecognitionResponse pendingShare = recognitionService.recognize("分享文件");
        IntentRecognitionResponse batchShare = recognitionService.recognize("分享根目录下所有文件");

        assertThat(service.resolve(
                "删除图片",
                conversation(pendingDelete),
                ambiguousDelete
        ).resolved()).isFalse();
        assertThat(service.resolve(
                "分享根目录下所有文件",
                conversation(pendingShare),
                batchShare
        ).resolved()).isFalse();
    }

    @Test
    void rejectsGenericTypeWordsAsAtomicNodeNames() {
        IntentRecognitionResponse pending = recognitionService.recognize("删除");

        PendingSlotResolutionService.Resolution resolution = service.resolve(
                "图片",
                conversation(pending),
                recognitionService.recognize("图片")
        );

        assertThat(resolution.resolved()).isFalse();
    }

    @Test
    void rejectsStructuredTargetWhenSemanticFrameRequiresHardClarification() {
        IntentRecognitionResponse pending = recognitionService.recognize("分享文件");
        IntentRecognitionResponse ready = recognitionService.recognize("分享合同.pdf");
        SemanticFrame readyFrame = ready.semanticFrame();
        SemanticFrame blockedFrame = new SemanticFrame(
                readyFrame.schemaVersion(),
                readyFrame.relation(),
                readyFrame.operation(),
                readyFrame.query(),
                readyFrame.scope(),
                readyFrame.reference(),
                readyFrame.confidence(),
                List.of("batch_share_unsupported"),
                new SemanticFrame.Clarification(
                        "batch_share_unsupported",
                        "目前一次只能分享一个真实文件或文件夹。",
                        List.of("分享合同.pdf")
                )
        );
        IntentRecognitionResponse contradictory = ready.withSemanticFrame(
                blockedFrame,
                Map.of("target_name", "分享根目录下所有文件"),
                ready.actionDraft()
        );

        PendingSlotResolutionService.Resolution resolution = service.resolve(
                "分享根目录下所有文件",
                conversation(pending),
                contradictory
        );

        assertThat(resolution.resolved()).isFalse();
    }

    @Test
    void rejectsStructuredNewNameFromUnsupportedBatchRenameCommand() {
        IntentRecognitionResponse pending = recognitionService.recognize("重命名合同.pdf");
        IntentRecognitionResponse batchRename = recognitionService.recognize("把所有图片统一重命名为归档");

        PendingSlotResolutionService.Resolution resolution = service.resolve(
                "把所有图片统一重命名为归档",
                conversation(pending),
                batchRename
        );

        assertThat(pending.missingSlots()).containsExactly("new_name");
        assertThat(batchRename.semanticFrame().clarification().reason())
                .isEqualTo("batch_rename_strategy_unsupported");
        assertThat(resolution.resolved()).isFalse();
    }

    @Test
    void rejectsDestinationFromFullSameIntentCommand() {
        IntentRecognitionResponse pending = recognitionService.recognize("把合同.pdf移动到");
        IntentRecognitionResponse newCommand = recognitionService.recognize("移动文件夹到资料");

        PendingSlotResolutionService.Resolution resolution = service.resolve(
                "移动文件夹到资料",
                conversation(pending),
                newCommand
        );

        assertThat(pending.missingSlots()).containsExactly("target_folder");
        assertThat(resolution.resolved()).isFalse();
    }

    private AssistantConversationState conversation(IntentRecognitionResponse pending) {
        return new AssistantConversationState(
                "conversation-test",
                1,
                pending.intentId(),
                pending.entities(),
                pending.missingSlots(),
                pending.actionDraft(),
                pending.actionPlan(),
                pending.candidateBinding(),
                AssistantConversationFocus.empty(),
                pending.semanticFrame(),
                "",
                Instant.now().plusSeconds(300)
        );
    }
}
