package polaris.hotel.wired.protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.WiredPlatform;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotTalk;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotTalkToUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSendLinkMessage;
import polaris.hotel.items.interactions.wired.effects.WiredEffectWhisper;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputFurniName;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveLinkDataframe;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveLinkMessage;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserSaysKeyword;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredExecutionOrderUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;

/**
 * The official text-placeholder context (wired context type 7) of the message boxes (Show Message, Bot Talk and Bot
 * Talk to Avatar): the placeholder names of the stack's text outputs, then the names of its text capturers. A stack
 * on a User Says Keyword trigger also offers {@code trigger_text}, the whole message that fired it.
 */
public final class WiredTextPlaceholderContext {
    static final int CONTEXT_TYPE = 7;
    public static final String TRIGGER_TEXT = "trigger_text";

    private WiredTextPlaceholderContext() {}

    /** Whether the box's editor gets the context. */
    static boolean appliesTo(Object box) {
        return box instanceof WiredEffectWhisper
                || box instanceof WiredEffectBotTalk
                || box instanceof WiredEffectBotTalkToUser
                || box instanceof WiredEffectSendLinkMessage;
    }

    /** The placeholder and capturer names of the stack the box stands in, or null when it gets no context. */
    static Names namesFor(Object box) {
        if (!appliesTo(box) || !(box instanceof UserItem item)) return null;
        Room room = roomOf(item);
        if (room == null || room.getRoomSpecialTypes() == null) return new Names(List.of(), List.of());
        return namesFor(room, item.getX(), item.getY());
    }

    static Names namesFor(Room room, int x, int y) {
        List<String> placeholders = new ArrayList<>();
        List<String> capturers = new ArrayList<>();

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(x, y)) {
            if (trigger instanceof WiredTriggerUserSaysKeyword || trigger instanceof WiredTriggerReceiveLinkMessage) {
                if (!placeholders.contains(TRIGGER_TEXT)) placeholders.add(TRIGGER_TEXT);
            } else if (trigger instanceof WiredTriggerReceiveLinkDataframe dataframe) {
                for (String name : dataframe.placeholderNames()) {
                    if (!placeholders.contains(name)) placeholders.add(name);
                }
            }
        }

        for (InteractionWiredExtra extra :
                WiredExecutionOrderUtil.sort(room.getRoomSpecialTypes().getExtras(x, y))) {
            String name =
                    switch (extra) {
                        case WiredExtraTextOutputUsername username -> username.getPlaceholderName();
                        case WiredExtraTextOutputFurniName furni -> furni.getPlaceholderName();
                        case WiredExtraTextOutputVariable variable -> variable.getPlaceholderName();
                        default -> null;
                    };
            if (name != null && !name.isEmpty() && !placeholders.contains(name)) placeholders.add(name);

            if (extra instanceof WiredExtraTextInputVariable input) {
                String capturer = input.getCapturerName();
                if (capturer != null && !capturer.isEmpty() && !capturers.contains(capturer)) capturers.add(capturer);
            }
        }

        // Listed in name order, not stack order (observed on AIR: ptr, cx, ev, ct, nv lists as ct, cx, ev, nv, ptr).
        placeholders.sort(java.util.Comparator.naturalOrder());
        capturers.sort(java.util.Comparator.naturalOrder());
        return new Names(placeholders, capturers);
    }

    /** One context entry: the count, the type, the placeholder names, then the capturer names. */
    static void write(ServerMessage out, Names names) {
        out.appendInt(1);
        out.appendInt(CONTEXT_TYPE);
        writeStrings(out, names.placeholders());
        writeStrings(out, names.capturers());
    }

    private static void writeStrings(ServerMessage out, List<String> values) {
        out.appendInt(values.size());
        for (String value : values) out.appendString(value);
    }

    /**
     * Official: when any message box of the room uses {@code $(trigger_text)}, everyone entering is told that the
     * room's wired can use the full text of the messages that fire it.
     */
    public static void sendTriggerTextNotice(Room room, User user) {
        if (room == null || user == null || user.getClient() == null || !usesTriggerText(room)) return;

        Map<String, String> keys = new HashMap<>();
        keys.put("image", "${image.library.url}album1584/WIRD1.png");
        keys.put("message", "${room.error.wired.trigger_text_capture}");
        user.getClient().sendResponse(new BubbleAlertComposer("wired.error", keys));
    }

    static boolean usesTriggerText(Room room) {
        if (room.getRoomSpecialTypes() == null) return false;

        for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects()) {
            if (!appliesTo(effect)) continue;
            String data = effect.getWiredData();
            if (data != null && data.contains(WiredTextPlaceholderUtil.TRIGGER_TEXT_TOKEN)) return true;
        }
        return false;
    }

    private static Room roomOf(UserItem item) {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }
        return WiredPlatform.gameEnvironment().getRoomManager().getRoom(item.getRoomId());
    }

    record Names(List<String> placeholders, List<String> capturers) {}
}
