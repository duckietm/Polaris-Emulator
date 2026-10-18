package com.eu.habbo.habbohotel.wired.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveLinkMessage;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightsManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiredLinkTransportTest {
    private static final int OWNER = 5;

    private final Map<Integer, Room> rooms = new HashMap<>();
    private final List<String> delivered = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(10_000);
    private WiredLinkService service;
    private WiredLinkTransport transport;
    private String networkId;

    @BeforeEach
    void setUp() {
        this.service = new WiredLinkService(new MemoryWiredLinkRepository());
        this.transport = new WiredLinkTransport(
                this.service,
                this.rooms::get,
                Runnable::run,
                this.clock::get,
                (room, trigger, event) ->
                        this.delivered.add(room.getId() + ":" + trigger.getId() + ":" + event.getLinkHops()));
        this.networkId =
                this.service.create(OWNER, 1, 100, "Net").networks().get(0).id();
    }

    /** A loaded room with one Link and one receive-message trigger listening on it. */
    private InteractionWiredLink room(int roomId, int roomOwner, int linkId, int linkOwner, boolean listening) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getOwnerId()).thenReturn(roomOwner);
        RoomRightsManager rights = mock(RoomRightsManager.class);
        when(rights.hasRights(anyInt())).thenAnswer(call -> call.getArgument(0, Integer.class) == roomOwner);
        when(room.getRightsManager()).thenReturn(rights);

        InteractionWiredLink link = mock(InteractionWiredLink.class);
        when(link.getId()).thenReturn(linkId);
        when(link.getUserId()).thenReturn(linkOwner);
        when(link.getRoomId()).thenReturn(roomId);
        when(room.getHabboItem(linkId)).thenReturn(link);

        WiredTriggerReceiveLinkMessage trigger = mock(WiredTriggerReceiveLinkMessage.class);
        when(trigger.getId()).thenReturn(linkId + 1);
        when(trigger.linkKind()).thenReturn(WiredLinkPayload.Kind.MESSAGE);
        when(trigger.listensTo(any(HabboItem.class))).thenReturn(listening);
        when(trigger.receiveEvent(any(), any(), any(), anyInt()))
                .thenAnswer(call -> WiredEvent.builder(WiredEvent.Type.LINK_MESSAGE_RECEIVED, room)
                        .linkHops(call.getArgument(3, Integer.class))
                        .build());
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        Set<InteractionWiredTrigger> triggers = new LinkedHashSet<>(List.of(trigger));
        when(types.getTriggers(WiredTriggerType.RECEIVE_LINK_MESSAGE)).thenReturn(triggers);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        this.rooms.put(roomId, room);
        return link;
    }

    @Test
    void aMessageReachesThePeersAndTheSenderWhenItReceivesLocally() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, OWNER, true);
        this.service.save(OWNER, 1, 100, this.networkId, true);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        int sent = this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("hi"), 0);

        assertEquals(2, sent);
        assertEquals(
                List.of("1:101:1", "2:201:1"), this.delivered.stream().sorted().toList());
    }

    @Test
    void theHopLimitStopsPingPong() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, OWNER, true);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        assertEquals(1, this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("a"), 2));
        assertEquals(0, this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("a"), 3));
        assertEquals(List.of("2:201:3"), this.delivered);
    }

    @Test
    void aRoomCannotSendMoreThanItsLimitPerSecond() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, OWNER, true);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        int sent = 0;
        for (int i = 0; i < 50; i++) {
            sent += this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0);
        }
        assertEquals(WiredLinkTransport.DEFAULT_SENDS_PER_SECOND, sent);

        this.clock.addAndGet(1000);
        assertEquals(1, this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0));
    }

    @Test
    void aRoomTheOwnerNoLongerControlsReceivesNothing() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        // The Link still stands in room 2, but room 2 now belongs to someone else and the owner lost rights.
        this.room(2, 99, 200, OWNER, true);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0);
        assertTrue(this.delivered.isEmpty());
    }

    @Test
    void aLinkThatChangedHandsReceivesNothing() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, 42, true);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0);
        assertTrue(this.delivered.isEmpty());
    }

    @Test
    void onlyTriggersThatPickedTheLinkFire() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, OWNER, false);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);

        assertEquals(1, this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0));
        assertTrue(this.delivered.isEmpty());
    }

    @Test
    void unloadedRoomsAreSkipped() {
        InteractionWiredLink sender = this.room(1, OWNER, 100, OWNER, true);
        this.room(2, OWNER, 200, OWNER, true);
        this.service.save(OWNER, 1, 100, this.networkId, false);
        this.service.save(OWNER, 2, 200, this.networkId, false);
        this.rooms.remove(2);

        this.transport.send(this.rooms.get(1), sender, WiredLinkPayload.message("x"), 0);
        assertTrue(this.delivered.isEmpty());
    }

    @Test
    void payloadsAreCapped() {
        assertEquals(
                1000,
                WiredLinkPayload.message("é".repeat(800))
                        .message()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        .length);
        Map<String, String> rows = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 20; i++) rows.put("k" + i, "v".repeat(300));
        WiredLinkPayload dataframe = WiredLinkPayload.dataframe(rows);
        assertEquals(12, dataframe.values().size());
        assertEquals(200, dataframe.values().get("k0").length());
    }
}
