package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A wired pass sends its texts after its flashes and movements; during a say, the texts of stacks its signals
 * reached follow the speaker's own line (observed on AIR WIN63-202610151745).
 */
class WiredSayPassOrderTest {

    @AfterEach
    void clear() {
        WiredBoxFlashes.clearForCurrentThread();
        WiredFurniStateBatch.clearForCurrentThread();
    }

    @Test
    void outsideAPassATextGoesOutAtOnce() {
        List<String> sent = new ArrayList<>();
        WiredBoxFlashes.sendAfterFlash(() -> sent.add("now"), false);
        assertEquals(List.of("now"), sent);
    }

    @Test
    void aPassHoldsItsTextsUntilItCloses() {
        List<String> sent = new ArrayList<>();
        WiredBoxFlashes.openPass();
        WiredBoxFlashes.openPass();
        WiredBoxFlashes.sendAfterFlash(() -> sent.add("text"), false);
        WiredBoxFlashes.closePass();
        assertEquals(List.of(), sent, "an inner close does not send");
        WiredBoxFlashes.closePass();
        assertEquals(List.of("text"), sent);
    }

    @Test
    void aSayPutsItsSignalTextsAfterTheSpeakersLine() {
        List<String> sent = new ArrayList<>();
        WiredBoxFlashes.runSayPass(() -> {
            WiredBoxFlashes.sendAfterFlash(() -> sent.add("direct"), false);
            WiredBoxFlashes.sendAfterFlash(() -> sent.add("reply"), true);
        });
        sent.add("speaker");
        WiredBoxFlashes.releaseAfterSpeaker();

        assertEquals(List.of("direct", "speaker", "reply"), sent);
    }

    @Test
    void aSignalTextOutsideASayIsAnOrdinaryText() {
        List<String> sent = new ArrayList<>();
        WiredBoxFlashes.openPass();
        WiredBoxFlashes.sendAfterFlash(() -> sent.add("signal"), true);
        WiredBoxFlashes.closePass();
        assertEquals(List.of("signal"), sent);
    }

    @Test
    void oneFailingSendDoesNotStopTheOthers() {
        List<String> sent = new ArrayList<>();
        WiredBoxFlashes.openPass();
        WiredBoxFlashes.sendAfterFlash(
                () -> {
                    throw new IllegalStateException("gone");
                },
                false);
        WiredBoxFlashes.sendAfterFlash(() -> sent.add("second"), false);
        WiredBoxFlashes.closePass();
        assertEquals(List.of("second"), sent);
    }
}
