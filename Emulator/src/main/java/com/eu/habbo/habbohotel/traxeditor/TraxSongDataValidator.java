package com.eu.habbo.habbohotel.traxeditor;

public final class TraxSongDataValidator {

    public static final int MAX_DATA_LENGTH = 4096;
    public static final int MAX_CHANNELS = 4;
    public static final int MAX_SAMPLE_ID = 648;
    public static final int MAX_CHANNEL_UNITS = 120;

    private TraxSongDataValidator() {
    }

    public static int validatedLength(String data) {
        if (data == null || data.isEmpty() || data.length() > MAX_DATA_LENGTH) return -1;

        String[] parts = data.split(":", -1);
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
