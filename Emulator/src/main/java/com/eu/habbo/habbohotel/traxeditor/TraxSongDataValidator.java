package com.eu.habbo.habbohotel.traxeditor;

/**
 * Structural validation of client-composed Trax song data.
 *
 * <p>The wire format matches the classic Habbo Traxmachine strings stored in
 * {@code soundtracks.track}: colon-separated pairs of a channel id and its
 * items, each item {@code sampleId,lengthUnits} separated by semicolons, e.g.
 * {@code 1:317,6;0,4:2:0,10:}. One length unit is two seconds of playback.
 */
public final class TraxSongDataValidator {

    public static final int MAX_DATA_LENGTH = 12288;
    public static final int MAX_CHANNELS = 4;
    /** 72 sound sets of 9 samples; 0 = silence. */
    public static final int MAX_SAMPLE_ID = 648;
    /** Per-channel song length cap in 2-second units (= 10 minutes). */
    public static final int MAX_CHANNEL_UNITS = 300;

    private TraxSongDataValidator() {
    }

    /**
     * @return the song length in seconds derived from the longest channel, or
     * -1 when the data is not a valid user-composed song.
     */
    public static int validatedLength(String data) {
        if (data == null || data.isEmpty() || data.length() > MAX_DATA_LENGTH) return -1;

        String[] parts = data.split(":", -1);
        // channelId ':' items pairs, with an optional single trailing empty
        // segment from the canonical trailing ':'.
        int segments = parts.length;
        if (segments > 0 && parts[segments - 1].isEmpty()) segments--;
        if (segments < 2 || segments % 2 != 0) return -1;

        int channels = segments / 2;
        if (channels > MAX_CHANNELS) return -1;

        int longestChannelUnits = -1;

        for (int channel = 0; channel < channels; channel++) {
            String channelId = parts[channel * 2];
            String items = parts[channel * 2 + 1];

            if (!channelId.equals(String.valueOf(channel + 1))) return -1;

            int channelUnits = validatedChannelUnits(items);
            if (channelUnits < 0) return -1;

            longestChannelUnits = Math.max(longestChannelUnits, channelUnits);
        }

        if (longestChannelUnits <= 0) return -1;

        return longestChannelUnits * 2;
    }

    private static int validatedChannelUnits(String items) {
        if (items.isEmpty()) return -1;

        int units = 0;

        for (String item : items.split(";", -1)) {
            if (item.isEmpty()) return -1;

            int comma = item.indexOf(',');
            if (comma <= 0 || comma == item.length() - 1) return -1;

            int sampleId = parsePositiveInt(item.substring(0, comma));
            int length = parsePositiveInt(item.substring(comma + 1));

            if (sampleId < 0 || sampleId > MAX_SAMPLE_ID) return -1;
            if (length < 1) return -1;

            units += length;
            if (units > MAX_CHANNEL_UNITS) return -1;
        }

        return units;
    }

    private static int parsePositiveInt(String value) {
        if (value.isEmpty() || value.length() > 4) return -1;

        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return -1;
            result = result * 10 + (c - '0');
        }

        return result;
    }
}
