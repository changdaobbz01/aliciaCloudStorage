package com.alicia.cloudstorage.ragexecution.domain.action;

public record ActionOutputReference(
        String stepKey,
        String outputField
) {
    public ActionOutputReference {
        stepKey = ActionPayloadRules.requireIdentifier(stepKey, "stepKey");
        outputField = ActionPayloadRules.requireText(outputField, "outputField", 64);
        if (!outputField.matches("[a-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("outputField is not a safe output name.");
        }
    }
}
