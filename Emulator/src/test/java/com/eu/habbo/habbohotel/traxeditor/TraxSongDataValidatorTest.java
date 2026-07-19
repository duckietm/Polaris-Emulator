package com.eu.habbo.habbohotel.traxeditor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraxSongDataValidatorTest {

    @Test
    void acceptsTheEmptySongTemplate() {
        assertEquals(2, TraxSongDataValidator.validatedLength(TraxEditorManager.EMPTY_SONG_DATA));
    }

    @Test
    void acceptsAClassicFourChannelSong() {
        // Base-database soundtrack "bossa_nova"; longest channel is 24 units = 48 seconds.
        String bossaNova = "1:317,6;318,4;319,4;317,4;319,4;0,2:2:0,2;316,4;0,4;316,4;0,4;316,4;0,2:"
                + "3:0,6;321,4;323,4;322,10:4:0,18;321,2;324,2;0,2:";
        assertEquals(48, TraxSongDataValidator.validatedLength(bossaNova));
    }

    @Test
    void lengthComesFromTheLongestChannel() {
        assertEquals(20, TraxSongDataValidator.validatedLength("1:1,4:2:0,10:3:0,1:4:0,1:"));
    }

    @Test
    void rejectsMalformedData() {
        assertEquals(-1, TraxSongDataValidator.validatedLength(null));
        assertEquals(-1, TraxSongDataValidator.validatedLength(""));
        assertEquals(-1, TraxSongDataValidator.validatedLength("not a song"));
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:1,2:orphan")); // dangling segment
        assertEquals(-1, TraxSongDataValidator.validatedLength("2:1,2:")); // channels must start at 1
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:1,2:3:1,2:")); // non-sequential channel ids
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:1,0:")); // zero-length item
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:1,-2:")); // negative length
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:99999,2:")); // sample id out of range
    }

    @Test
    void rejectsMoreThanFourChannels() {
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:0,4:2:0,4:3:0,4:4:0,4:5:0,4:"));
    }

    @Test
    void rejectsOverlongChannels() {
        assertEquals(-1, TraxSongDataValidator.validatedLength("1:0,301:"));
        assertEquals(600, TraxSongDataValidator.validatedLength("1:1,300:"));
    }

    @Test
    void rejectsOversizedPayloads() {
        String longSong = "1:" + "1,1;".repeat(4000);
        assertEquals(-1, TraxSongDataValidator.validatedLength(longSong));
    }
}
