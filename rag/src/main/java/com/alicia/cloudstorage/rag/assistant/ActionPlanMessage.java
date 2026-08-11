package com.alicia.cloudstorage.rag.assistant;

public record ActionPlanMessage(
        String level,
        String code,
        String text
) {
    public ActionPlanMessage {
        level = level == null || level.isBlank() ? "info" : level;
        code = code == null ? "" : code;
        text = text == null ? "" : text;
    }
}
