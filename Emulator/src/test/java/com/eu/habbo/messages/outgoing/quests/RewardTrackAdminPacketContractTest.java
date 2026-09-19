package com.eu.habbo.messages.outgoing.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.quests.RewardTrack;
import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The staff editor packets, field by field: the manifest cannot verify loop-driven payloads. */
class RewardTrackAdminPacketContractTest {

    @Test
    void adminDataListsTheChoicesThenEveryTrackWithItsRows() {
        RewardTrack track = new RewardTrack("season_1", "blue", 2, 100, 200, true, 1.5, 50, 25, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(2, 10, false));
        talk.addLevel(new RewardTrack.Level(4, 20, true));
        track.addTask(talk);
        track.addPrize(new RewardTrack.Prize("p1", 10, 7, "badge", "ACH_1", 1, true, 3));

        ByteBuf payload = new RewardTrackAdminDataComposer(
                        List.of("chat_with_someone", "place_item"),
                        List.of("duckets", "badge"),
                        List.of(new RewardTrackManager.LoadedTrack(track, false)),
                        Map.of("season_1/p1", 7),
                        Map.of("season_1", Map.of("name", "Season 1")))
                .compose()
                .get();

        assertHeader(payload, Outgoing.RewardTrackAdminDataComposer);
        assertEquals(2, payload.readInt());
        assertEquals("chat_with_someone", readString(payload));
        assertEquals("place_item", readString(payload));
        assertEquals(2, payload.readInt());
        assertEquals("duckets", readString(payload));
        assertEquals("badge", readString(payload));
        assertEquals(1, payload.readInt());
        assertEquals("season_1", readString(payload));
        assertEquals("blue", readString(payload));
        assertEquals(2, payload.readInt());
        assertEquals(100, payload.readInt());
        assertEquals(200, payload.readInt());
        assertTrue(payload.readBoolean());
        assertEquals(150, payload.readInt(), "the boost travels in hundredths");
        assertEquals(50, payload.readInt());
        assertEquals(25, payload.readInt());
        assertEquals(0, payload.readInt());
        assertFalse(payload.readBoolean(), "enabled");
        assertEquals(1, payload.readInt());
        assertEquals("talk", readString(payload));
        assertEquals("chat_with_someone", readString(payload));
        assertEquals("", readString(payload));
        assertFalse(payload.readBoolean());
        assertEquals(1, payload.readInt());
        assertEquals(2, payload.readInt());
        assertEquals(2, payload.readInt());
        assertEquals(10, payload.readInt());
        assertFalse(payload.readBoolean());
        assertEquals(4, payload.readInt());
        assertEquals(20, payload.readInt());
        assertTrue(payload.readBoolean());
        assertEquals(1, payload.readInt());
        assertEquals("p1", readString(payload));
        assertEquals(10, payload.readInt());
        assertEquals(7, payload.readInt());
        assertEquals("badge", readString(payload));
        assertEquals("ACH_1", readString(payload));
        assertEquals(1, payload.readInt());
        assertTrue(payload.readBoolean());
        assertEquals(3, payload.readInt());
        assertEquals(7, payload.readInt(), "how many users claimed it");
        assertEquals(1, payload.readInt(), "the track's texts");
        assertEquals("name", readString(payload));
        assertEquals("Season 1", readString(payload));
        assertEquals(0, payload.readableBytes());
    }

    @Test
    void furniSearchResultEchoesTheQueryThenEveryMatch() {
        ByteBuf payload = new RewardTrackFurniSearchResultComposer(
                        "sofa", List.of(new RewardTrackAdmin.FurniMatch("club_sofa", 1234, "s")))
                .compose()
                .get();

        assertHeader(payload, Outgoing.RewardTrackFurniSearchResultComposer);
        assertEquals("sofa", readString(payload));
        assertEquals(1, payload.readInt());
        assertEquals("club_sofa", readString(payload));
        assertEquals(1234, payload.readInt());
        assertEquals("s", readString(payload));
        assertEquals(0, payload.readableBytes());
    }

    @Test
    void textsTravelAsFullKeys() {
        ByteBuf payload = new RewardTrackTextsComposer(Map.of("reward_track.season_1.name", "Season 1"))
                .compose()
                .get();

        assertHeader(payload, Outgoing.RewardTrackTextsComposer);
        assertEquals(1, payload.readInt());
        assertEquals("reward_track.season_1.name", readString(payload));
        assertEquals("Season 1", readString(payload));
        assertEquals(0, payload.readableBytes());
    }

    @Test
    void resultCarriesTheOutcomeAndWhatWasTouched() {
        ByteBuf payload = new RewardTrackAdminResultComposer(false, "Unknown action type: x", "task", "season_1", "t")
                .compose()
                .get();

        assertHeader(payload, Outgoing.RewardTrackAdminResultComposer);
        assertFalse(payload.readBoolean());
        assertEquals("Unknown action type: x", readString(payload));
        assertEquals("task", readString(payload));
        assertEquals("season_1", readString(payload));
        assertEquals("t", readString(payload));
        assertEquals(0, payload.readableBytes());
    }

    private static void assertHeader(ByteBuf payload, int expectedHeader) {
        payload.skipBytes(4);
        assertEquals(expectedHeader, payload.readUnsignedShort());
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
