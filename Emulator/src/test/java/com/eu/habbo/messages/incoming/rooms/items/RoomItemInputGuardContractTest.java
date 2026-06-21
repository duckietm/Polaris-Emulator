package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomItemInputGuardContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/" + name + ".java"));
    }

    @Test
    void itemMutationHandlersRejectInvalidIdsBeforeRoomLookups() throws Exception {
        for (String handler : new String[]{"RoomPickupItemEvent", "RotateMoveItemEvent", "UpdateFurniturePositionEvent", "MoveWallItemEvent", "ToggleFloorItemEvent", "ToggleWallItemEvent", "AdvertisingSaveEvent", "MannequinSaveNameEvent", "MannequinSaveLookEvent", "FootballGateSaveLookEvent", "PostItSaveDataEvent", "PostItPlaceEvent", "PostItDeleteEvent"}) {
            String source = source(handler);
            int idRead = source.indexOf("this.packet.readInt()");
            int guard = source.indexOf("RoomItemInputGuard.isPositiveId", idRead);
            int lookup = source.indexOf("getHabboItem", guard);

            assertTrue(guard > idRead, handler + " should validate item ids after reading them");
            assertTrue(lookup == -1 || guard < lookup, handler + " should validate item ids before room item lookups");
        }
    }

    @Test
    void specialItemHandlersRejectInvalidIdsBeforeLookups() throws Exception {
        for (String handler : new String[]{
                "rentablespace/RentSpaceEvent",
                "rentablespace/RentSpaceCancelEvent",
                "lovelock/LoveLockStartConfirmEvent",
                "youtube/YoutubeRequestPlaylistChange",
                "youtube/YoutubeRequestPlaylists",
                "youtube/YoutubeRequestStateChange",
                "jukebox/JukeBoxAddSoundTrackEvent",
                "RedeemItemEvent",
                "RedeemClothingEvent"
        }) {
            String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/" + handler + ".java"));
            int idRead = source.indexOf("this.packet.readInt()");
            int guard = source.indexOf("RoomItemInputGuard.isPositiveId", idRead);

            assertTrue(guard > idRead, handler + " should validate item ids after reading them");
        }
    }

    @Test
    void roomPlacementParsesClientPayloadSafely() throws Exception {
        String source = source("RoomPlaceItemEvent");

        assertTrue(source.contains("RoomItemInputGuard.parseInt(values[0])"),
                "item placement should parse item id without throwing on malformed packets");
        assertTrue(source.contains("values.length < 4"),
                "item placement should require complete coordinate payloads");
        assertTrue(source.contains("RoomItemInputGuard.parseShort(values[1])"),
                "floor placement should parse x coordinate safely");
        assertTrue(source.contains("RoomItemInputGuard.parseShort(values[2])"),
                "floor placement should parse y coordinate safely");
        assertTrue(source.contains("RoomItemInputGuard.parseInt(values[3])"),
                "floor placement should parse rotation safely");
    }

    @Test
    void advertisingCustomValuesAreBoundedBeforeMutation() throws Exception {
        String source = source("AdvertisingSaveEvent");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("RoomItemInputGuard.isValidCustomValueCount(count)", count);
        int loop = source.indexOf("for (int i = 0; i < count / 2; i++)", guard);
        int mutate = source.indexOf(".values.put(key, value)", loop);

        assertTrue(guard > count && guard < loop,
                "custom value pair count should be bounded before reading key/value pairs");
        assertTrue(source.contains("RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_KEY_LENGTH)"),
                "custom value keys should be trimmed and capped");
        assertTrue(source.contains("RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_VALUE_LENGTH)"),
                "custom value values should be trimmed and capped");
        assertTrue(mutate > loop,
                "custom values should only mutate after bounded reads");
    }

    @Test
    void stickyPoleMultiCommandPayloadIsBounded() throws Exception {
        String source = source("SavePostItStickyPoleEvent");

        int split = source.indexOf("String[] commands = this.packet.readString().split");
        int countGuard = source.indexOf("commands.length > RoomItemInputGuard.MAX_STICKY_POLE_COMMANDS", split);
        int trim = source.indexOf("RoomItemInputGuard.trimToMax(command.replace", countGuard);
        int execute = source.indexOf("CommandHandler.handleCommand", trim);

        assertTrue(split > -1 && countGuard > split,
                "sticky-pole multi-command packets should cap command count before looping");
        assertTrue(trim > countGuard && trim < execute,
                "sticky-pole multi-command packets should cap each command before execution");
    }

    @Test
    void specialLookPayloadsAreValidatedBeforeMutation() throws Exception {
        String football = source("FootballGateSaveLookEvent");
        String mannequinName = source("MannequinSaveNameEvent");
        String moodlight = source("MoodLightSaveSettingsEvent");

        assertTrue(football.contains("RoomItemInputGuard.isValidGender(gender)"),
                "football gates should reject unknown gender keys instead of defaulting to male");
        assertTrue(football.contains("RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_LOOK_LENGTH)"),
                "football gate looks should be capped before persistence");
        assertTrue(mannequinName.contains("RoomItemInputGuard.trimToMax(this.packet.readString(), 32)"),
                "mannequin names should be capped before extradata persistence");
        assertTrue(moodlight.contains("if (room == null)"),
                "moodlight saves should null-check current room before inspecting rights");
    }

    @Test
    void youtubeAndJukeboxInputsAreBoundedBeforeListAccess() throws Exception {
        String playlistChange = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/youtube/YoutubeRequestPlaylistChange.java"));
        String jukeboxRemove = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/jukebox/JukeBoxRemoveSoundTrackEvent.java"));
        String jukeboxRequest = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/jukebox/JukeBoxRequestPlayListEvent.java"));

        int playlistTrim = playlistChange.indexOf("RoomItemInputGuard.trimToMax");
        int emptyVideos = playlistChange.indexOf("playlist.get().getVideos().isEmpty()");
        int getFirst = playlistChange.indexOf("playlist.get().getVideos().get(0)");

        assertTrue(playlistTrim > -1,
                "youtube playlist ids should be capped before lookup");
        assertTrue(emptyVideos > playlistTrim && emptyVideos < getFirst,
                "youtube playlist changes should reject empty playlists before get(0)");
        assertTrue(jukeboxRemove.contains("index < 0 || index >= room.getTraxManager().getSongs().size()"),
                "jukebox remove should bound client-provided indexes before list access");
        assertTrue(jukeboxRequest.contains("if (room == null)"),
                "jukebox playlist requests should null-check current room");
    }

    @Test
    void helperRejectsMalformedValues() {
        assertFalse(RoomItemInputGuard.isPositiveId(0));
        assertTrue(RoomItemInputGuard.isPositiveId(1));
        assertFalse(RoomItemInputGuard.isValidCustomValueCount(0));
        assertFalse(RoomItemInputGuard.isValidCustomValueCount(3));
        assertTrue(RoomItemInputGuard.isValidCustomValueCount(RoomItemInputGuard.MAX_CUSTOM_VALUE_PAIRS * 2));
        assertFalse(RoomItemInputGuard.isValidCustomValueCount((RoomItemInputGuard.MAX_CUSTOM_VALUE_PAIRS + 1) * 2));
        assertEquals(123, RoomItemInputGuard.parseInt("123"));
        assertNull(RoomItemInputGuard.parseInt("abc"));
        assertEquals((short) 12, RoomItemInputGuard.parseShort("12"));
        assertNull(RoomItemInputGuard.parseShort("40000"));
        assertTrue(RoomItemInputGuard.isValidGender("m"));
        assertTrue(RoomItemInputGuard.isValidGender("F"));
        assertFalse(RoomItemInputGuard.isValidGender("x"));
    }
}
