package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.util.HotelDateTimeUtil;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredRoomTimeAndBotReachTest {

    @Test
    void aRoomsWiredTimezoneWinsAndABadOneFallsBackToTheHotels() {
        Room tokyo = mock(Room.class);
        when(tokyo.getWiredTimezone()).thenReturn("Asia/Tokyo");
        Room broken = mock(Room.class);
        when(broken.getWiredTimezone()).thenReturn("Not/AZone");
        Room unset = mock(Room.class);
        when(unset.getWiredTimezone()).thenReturn("");

        assertEquals(ZoneId.of("Asia/Tokyo"), WiredRoomTime.zoneFor(tokyo));
        assertEquals(HotelDateTimeUtil.getZoneId(), WiredRoomTime.zoneFor(broken));
        assertEquals(HotelDateTimeUtil.getZoneId(), WiredRoomTime.zoneFor(unset));
    }

    @Test
    void theTriggeringUserOfABotReachingSomeoneIsTheBotAndTheReachedUserIsThePersonReached() {
        Room room = mock(Room.class);
        RoomUnit bot = mock(RoomUnit.class);
        when(bot.getRoomUnitType()).thenReturn(RoomUnitType.BOT);
        RoomUnit reached = mock(RoomUnit.class);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.BOT_REACHED_HABBO, room)
                        .actor(bot)
                        .targetUnit(reached)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        assertEquals(List.of(bot), WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER));
        assertEquals(List.of(reached), WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_REACHED_USER));
    }

    @Test
    void aUserHandingSomeoneAnItemStaysTheTriggeringUser() {
        Room room = mock(Room.class);
        RoomUnit giver = mock(RoomUnit.class);
        when(giver.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        RoomUnit receiver = mock(RoomUnit.class);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.BOT_REACHED_HABBO, room)
                        .actor(giver)
                        .targetUnit(receiver)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        assertEquals(List.of(giver), WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER));
    }
}
