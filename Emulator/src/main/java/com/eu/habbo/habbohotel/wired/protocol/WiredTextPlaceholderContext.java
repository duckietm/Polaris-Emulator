package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTalk;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTalkToHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWhisper;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputFurniName;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredExecutionOrderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                || box instanceof WiredEffectBotTalkToHabbo;
    }

    /** The placeholder and capturer names of the stack the box stands in, or null when it gets no context. */
    static Names namesFor(Object box) {
        if (!appliesTo(box) || !(box instanceof HabboItem item)) return null;
        Room room = roomOf(item);
        if (room == null || room.getRoomSpecialTypes() == null) return new Names(List.of(), List.of());
        return namesFor(room, item.getX(), item.getY());
    }

    static Names namesFor(Room room, int x, int y) {
        List<String> placeholders = new ArrayList<>();
        List<String> capturers = new ArrayList<>();

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(x, y)) {
            if (trigger instanceof WiredTriggerHabboSaysKeyword) {
                placeholders.add(TRIGGER_TEXT);
                break;
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
    public static void sendTriggerTextNotice(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getClient() == null || !usesTriggerText(room)) return;

        Map<String, String> keys = new HashMap<>();
        keys.put("image", "${image.library.url}album1584/WIRD1.png");
        keys.put("message", "${room.error.wired.trigger_text_capture}");
        habbo.getClient().sendResponse(new BubbleAlertComposer("wired.error", keys));
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

    private static Room roomOf(HabboItem item) {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }
        return WiredPlatform.gameEnvironment().getRoomManager().getRoom(item.getRoomId());
    }

    record Names(List<String> placeholders, List<String> capturers) {}
}
