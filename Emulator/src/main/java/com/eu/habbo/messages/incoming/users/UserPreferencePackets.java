package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.ClientMessage;

/**
 * Field layouts and sanitizing rules of the official AIR 13 per-user preference packets
 * (SetChatPreferences 2506, SetOnlineIndicatorPreference 818, wired menu preferences 1226).
 * The ranges mirror HabboFreeFlowChat.sanitizeChat* and OtherSettingsView: an unknown chat mode
 * falls back to free flow, an unknown width or speed to normal, an unknown notification preference
 * to everyone.
 */
public final class UserPreferencePackets {
    public static final int CHAT_MODE_FREE_FLOW = 0;
    public static final int CHAT_MODE_LINE_BY_LINE = 1;
    public static final int CHAT_BUBBLE_WIDTH_NORMAL = 1;
    public static final int CHAT_SCROLL_SPEED_NORMAL = 1;
    public static final int ONLINE_INDICATOR_EVERYONE = 0;
    public static final int ONLINE_INDICATOR_NOBODY = 2;

    public record ChatPreferences(int chatMode, int chatBubbleWidth, int chatScrollSpeed) {}

    private UserPreferencePackets() {}

    public static int sanitizeChatMode(int value) {
        return value == CHAT_MODE_LINE_BY_LINE ? value : CHAT_MODE_FREE_FLOW;
    }

    public static int sanitizeChatBubbleWidth(int value) {
        return value >= 0 && value <= 2 ? value : CHAT_BUBBLE_WIDTH_NORMAL;
    }

    public static int sanitizeChatScrollSpeed(int value) {
        return value >= 0 && value <= 2 ? value : CHAT_SCROLL_SPEED_NORMAL;
    }

    public static int sanitizeOnlineIndicatorPreference(int value) {
        return value >= ONLINE_INDICATOR_EVERYONE && value <= ONLINE_INDICATOR_NOBODY
                ? value
                : ONLINE_INDICATOR_EVERYONE;
    }

    /** Official layout: a leading boolean the client always sends as false, then mode, width, speed. */
    public static ChatPreferences parseChatPreferences(ClientMessage packet) {
        packet.readBoolean();
        int chatMode = sanitizeChatMode(packet.readInt());
        int chatBubbleWidth = sanitizeChatBubbleWidth(packet.readInt());
        int chatScrollSpeed = sanitizeChatScrollSpeed(packet.readInt());
        return new ChatPreferences(chatMode, chatBubbleWidth, chatScrollSpeed);
    }

    public static int parseOnlineIndicatorPreference(ClientMessage packet) {
        return sanitizeOnlineIndicatorPreference(packet.readInt());
    }

    /**
     * Official layout: menu button, inspect button, play-test mode, a reserved int, wired whisper
     * disabled, show all notifications, ui style. Kept for plugins; the handler reads the whole packet
     * with {@link #parseWiredMenuPreferences}.
     */
    public static boolean parseWiredWhisperDisabled(ClientMessage packet) {
        packet.readBoolean();
        packet.readBoolean();
        packet.readBoolean();
        packet.readInt();
        boolean wiredWhisperDisabled = packet.readBoolean();
        packet.readBoolean();
        packet.readString();
        return wiredWhisperDisabled;
    }

    /** The official wired menu preferences (1226) as the server keeps them. */
    public record WiredMenuPreferences(
            boolean menuButton,
            boolean inspectButton,
            boolean playTestMode,
            boolean wiredWhisperDisabled,
            boolean showAllNotifications,
            String uiStyle) {
        static final int MAX_STYLE_LENGTH = 24;

        /** {@code menu,inspect,playtest,showAll,style}; the whisper switch has its own column. */
        public String encode() {
            return bit(this.menuButton) + "," + bit(this.inspectButton) + "," + bit(this.playTestMode) + ","
                    + bit(this.showAllNotifications) + "," + this.uiStyle;
        }

        private static String bit(boolean value) {
            return value ? "1" : "0";
        }
    }

    /** The playtest switch of stored wired menu preferences ({@link WiredMenuPreferences#encode}); off when unset. */
    public static boolean isPlayTestModeStored(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return false;
        }

        String[] parts = encoded.split(",", -1);
        return parts.length > 2 && "1".equals(parts[2]);
    }

    /** Reads the whole official wired menu preferences packet; the style is kept to a short name. */
    public static WiredMenuPreferences parseWiredMenuPreferences(ClientMessage packet) {
        boolean menuButton = packet.readBoolean();
        boolean inspectButton = packet.readBoolean();
        boolean playTestMode = packet.readBoolean();
        packet.readInt();
        boolean wiredWhisperDisabled = packet.readBoolean();
        boolean showAllNotifications = packet.readBoolean();
        String style = packet.readString();
        style = style == null ? "" : style.replaceAll("[^A-Za-z0-9_]", "");
        if (style.length() > WiredMenuPreferences.MAX_STYLE_LENGTH) {
            style = style.substring(0, WiredMenuPreferences.MAX_STYLE_LENGTH);
        }
        return new WiredMenuPreferences(
                menuButton, inspectButton, playTestMode, wiredWhisperDisabled, showAllNotifications, style);
    }
}
