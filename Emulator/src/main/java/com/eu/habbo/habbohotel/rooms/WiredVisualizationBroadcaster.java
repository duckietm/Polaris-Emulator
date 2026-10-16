package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniHighlightComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniVisualizationStateComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredUserHighlightComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredUserTransparencyComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredUserVisualizationStateComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Applies the official wired highlights and avatar transparency to the room's state and sends each
 * viewer what they now see: everyone when the audience is the whole room, otherwise only the picked
 * viewers. A viewer's own state wins over the room-wide one, so a room-wide change reaches them as
 * whatever they actually see.
 */
public final class WiredVisualizationBroadcaster {

    private WiredVisualizationBroadcaster() {}

    /** Highlights the furni and avatars for {@code viewers}, or for everyone when it is null. */
    public static void highlight(
            Room room,
            Collection<Habbo> viewers,
            Collection<HabboItem> furni,
            Collection<RoomUnit> users,
            WiredHighlight highlight) {
        WiredVisualizationService service = service(room);
        if (service == null || highlight == null) {
            return;
        }

        if (viewers == null) {
            List<Integer> furniIds = service.highlightFurni(WiredVisualizationService.EVERYONE, furni, highlight);
            List<Integer> unitIds = service.highlightUsers(WiredVisualizationService.EVERYONE, users, highlight);
            if (furniIds.isEmpty() && unitIds.isEmpty()) {
                return;
            }
            for (Habbo recipient : room.getHabbos()) {
                int viewerId = viewerId(recipient);
                if (viewerId > 0) {
                    sendHighlights(
                            recipient,
                            service.effectiveFurniHighlights(viewerId, furniIds),
                            service.effectiveUserHighlights(viewerId, unitIds));
                }
            }
            return;
        }

        for (Habbo recipient : viewers) {
            int viewerId = viewerId(recipient);
            if (viewerId <= 0) {
                continue;
            }
            List<Integer> furniIds = service.highlightFurni(viewerId, furni, highlight);
            List<Integer> unitIds = service.highlightUsers(viewerId, users, highlight);
            sendHighlights(
                    recipient,
                    service.effectiveFurniHighlights(viewerId, furniIds),
                    service.effectiveUserHighlights(viewerId, unitIds));
        }
    }

    /** Sets the avatars' transparency for {@code viewers}, or for everyone when it is null. */
    public static void setUserTransparency(
            Room room, Collection<Habbo> viewers, Collection<RoomUnit> users, WiredUserTransparency transparency) {
        WiredVisualizationService service = service(room);
        if (service == null || transparency == null) {
            return;
        }

        if (viewers == null) {
            List<Integer> unitIds =
                    service.setUserTransparency(WiredVisualizationService.EVERYONE, users, transparency);
            if (unitIds.isEmpty()) {
                return;
            }
            for (Habbo recipient : room.getHabbos()) {
                int viewerId = viewerId(recipient);
                if (viewerId > 0) {
                    send(
                            recipient,
                            service.effectiveUserTransparency(viewerId, unitIds),
                            WiredUserTransparencyComposer::new);
                }
            }
            return;
        }

        for (Habbo recipient : viewers) {
            int viewerId = viewerId(recipient);
            if (viewerId > 0) {
                List<Integer> unitIds = service.setUserTransparency(viewerId, users, transparency);
                send(
                        recipient,
                        service.effectiveUserTransparency(viewerId, unitIds),
                        WiredUserTransparencyComposer::new);
            }
        }
    }

    /**
     * On entering the room: every highlight and avatar transparency the user sees, room-wide and their
     * own. Furni transparency is not in it; it is replayed as our opacity packet.
     */
    public static void sendState(Room room, Habbo habbo) {
        WiredVisualizationService service = service(room);
        int viewerId = viewerId(habbo);
        if (service == null || viewerId <= 0) {
            return;
        }

        habbo.getClient()
                .sendResponse(
                        new WiredFurniVisualizationStateComposer(List.of(), service.furniHighlightSnapshot(viewerId)));
        habbo.getClient()
                .sendResponse(new WiredUserVisualizationStateComposer(
                        service.userTransparencySnapshot(viewerId), service.userHighlightSnapshot(viewerId)));
    }

    private static void sendHighlights(
            Habbo recipient,
            List<WiredVisualizationEntry<WiredHighlight>> furni,
            List<WiredVisualizationEntry<WiredHighlight>> users) {
        send(recipient, furni, WiredFurniHighlightComposer::new);
        send(recipient, users, WiredUserHighlightComposer::new);
    }

    /** One packet per distinct value, as the official packets carry a single value for all their ids. */
    private static <V> void send(
            Habbo recipient,
            List<WiredVisualizationEntry<V>> entries,
            BiFunction<Collection<Integer>, V, MessageComposer> composer) {
        if (entries.isEmpty()) {
            return;
        }
        Map<V, List<Integer>> byValue = new LinkedHashMap<>();
        for (WiredVisualizationEntry<V> entry : entries) {
            byValue.computeIfAbsent(entry.value(), ignored -> new ArrayList<>()).add(entry.id());
        }
        byValue.forEach((value, ids) -> recipient.getClient().sendResponse(composer.apply(ids, value)));
    }

    private static WiredVisualizationService service(Room room) {
        RoomWiredRuntime runtime = room == null ? null : room.getWiredRuntime();
        return runtime == null ? null : runtime.visualization();
    }

    private static int viewerId(Habbo habbo) {
        return habbo == null || habbo.getClient() == null || habbo.getHabboInfo() == null
                ? 0
                : habbo.getHabboInfo().getId();
    }
}
