package com.eu.habbo.messages.incoming.modtool;

final class ModToolReportInputGuard {
    static final int MAX_REPORT_MESSAGE_LENGTH = 1000;
    static final int MAX_PRIVATE_CHAT_LOGS = 100;
    static final int MAX_PRIVATE_CHAT_MESSAGE_LENGTH = 500;

    private ModToolReportInputGuard() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isValidReportMessage(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_REPORT_MESSAGE_LENGTH;
    }

    static boolean isValidChatLogMessage(String value) {
        return value != null && value.length() <= MAX_PRIVATE_CHAT_MESSAGE_LENGTH;
    }

    static boolean isValidPrivateChatLogCount(int count) {
        return count > 0 && count <= MAX_PRIVATE_CHAT_LOGS;
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }
}
