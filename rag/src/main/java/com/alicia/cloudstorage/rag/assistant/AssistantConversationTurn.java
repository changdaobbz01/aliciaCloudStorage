package com.alicia.cloudstorage.rag.assistant;

import java.util.ArrayList;
import java.util.List;

record AssistantConversationTurn(String userText, String assistantText) {

    private static final int MAX_TURNS = 8;
    private static final int MAX_TEXT_CODE_POINTS = 512;

    AssistantConversationTurn {
        userText = limit(userText);
        assistantText = limit(assistantText);
    }

    static List<AssistantConversationTurn> append(List<AssistantConversationTurn> previous, String user, String assistant) {
        var recent = new ArrayList<>(previous.subList(Math.max(0, previous.size() - MAX_TURNS + 1), previous.size()));
        recent.add(new AssistantConversationTurn(user, assistant));
        return List.copyOf(recent);
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }
        int count = text.codePointCount(0, text.length());
        return count <= MAX_TEXT_CODE_POINTS ? text : text.substring(0, text.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS));
    }
}
