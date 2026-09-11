package com.eu.habbo.messages.incoming.modtool;

final class ModToolReportInputGuard {
    static final int MAX_REPORT_MESSAGE_LENGTH = 1000;
    static final int MAX_PRIVATE_CHAT_LOGS = 100;
    static final int MAX_PRIVATE_CHAT_MESSAGE_LENGTH = 500;
    static final int MAX_REPORTER_CONTACT_LENGTH = 255;

    private ModToolReportInputGuard() {}

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

    static boolean isPrivateChatParticipant(int authorId, int reporterId, int reportedUserId) {
        return authorId > 0 && (authorId == reporterId || authorId == reportedUserId);
    }

    /**
     * The official CallForHelp composers (class_2390 and friends) end with two strings: the
     * reporter name and e-mail, filled in only by the unlawful-activity branch of
     * TopicsFlowHelpController. They are appended to the ticket message so a moderator sees the
     * contact details the reporter typed.
     */
    static String withReporterContact(String message, String reporterName, String reporterEmail) {
        String name = truncateContact(normalize(reporterName));
        String email = truncateContact(normalize(reporterEmail));

        if (name.isEmpty() && email.isEmpty()) {
            return message == null ? "" : message;
        }

        StringBuilder builder = new StringBuilder(message == null ? "" : message);

        if (builder.length() > 0) {
            builder.append("\n\n");
        }

        builder.append("Reporter: ").append(name);

        if (!email.isEmpty()) {
            builder.append(" <").append(email).append(">");
        }

        return builder.toString();
    }

    private static String truncateContact(String value) {
        return value.length() <= MAX_REPORTER_CONTACT_LENGTH ? value : value.substring(0, MAX_REPORTER_CONTACT_LENGTH);
    }
}
