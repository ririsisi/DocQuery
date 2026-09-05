package com.docquery.document.model;

/**
 * 问答分流：CHAT 不检索；KB 走知识库。默认 KB，避免问候空检索被当成故障。
 * 模式由页面传入，不是模型自己决定去不去检索。
 */
public enum AskMode {
    CHAT,
    KB;

    public static AskMode fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return KB;
        }
        String value = raw.trim().toUpperCase().replace('-', '_');
        return switch (value) {
            case "CHAT" -> CHAT;
            case "KB", "KB_SEARCH" -> KB;
            default -> throw new IllegalArgumentException("mode 只支持 CHAT 或 KB");
        };
    }
}
