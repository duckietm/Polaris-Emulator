package com.eu.habbo.imaging.camera;

/**
 * Checks the status, timestamp and checksum the official client appends to its RenderRoom JSON.
 *
 * <p>The client builds the JSON as one string with fixed keys: {@code { "planes" : ...,"sprites" :
 * ...,"modifiers" : ...,"filters" : ...,"roomid" : N[,"zoom" : N],"status" : N} and then, with
 * {@code time} its clock in milliseconds rounded down to 100 and {@code lastDigits} the two digits
 * it removed:
 *
 * <ul>
 *   <li>{@code status = time/100 % 23 + topSecurityLevel};
 *   <li>{@code check = (length of the string so far + time/100 * 17) % 1493};
 *   <li>{@code timestamp = time + fletcher100(low byte of each char so far, check, roomId)};
 *   <li>{@code checksum = (lastDigits + 13) * (check + 29)}, then {@code " }"} closes the string.
 * </ul>
 *
 * The server can recompute everything except {@code lastDigits} (it only has to fit 0..99) and the
 * user's security level (it only has to be non-negative and small).
 */
public final class AirRenderChecksum {
    static final String TIMESTAMP_KEY = ",\"timestamp\" : ";
    static final String CHECKSUM_KEY = ",\"checksum\" : ";
    static final String STATUS_KEY = ",\"status\" : ";
    static final String JSON_END = " }";
    private static final int MAX_SECURITY_LEVEL = 1000;

    /** Why a payload failed the check, or {@link #OK}. */
    public enum Result {
        OK,
        MISSING_FIELDS,
        BAD_TIMESTAMP,
        BAD_CHECKSUM,
        BAD_STATUS
    }

    private AirRenderChecksum() {}

    /** AIR {@code CryptoTools.fletcher100}: two running sums mod 255 over the bytes, mod 100. */
    public static int fletcher100(byte[] bytes, int seedA, int seedB) {
        int a = seedA;
        int b = seedB;
        for (byte value : bytes) {
            a = (a + (value & 0xFF)) % 255;
            b = (a + b) % 255;
        }
        return (a + b) % 100;
    }

    /** AIR {@code CryptoTools.stringToByteArray}: the low byte of each UTF-16 char. */
    public static byte[] lowBytes(String text, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) text.charAt(i);
        }
        return bytes;
    }

    public static Result verify(String json, int roomId) {
        if (json == null || !json.endsWith(JSON_END)) {
            return Result.MISSING_FIELDS;
        }
        int timestampAt = json.lastIndexOf(TIMESTAMP_KEY);
        int checksumAt = json.lastIndexOf(CHECKSUM_KEY);
        int statusAt = json.lastIndexOf(STATUS_KEY);
        if (timestampAt < 0 || checksumAt < timestampAt || statusAt < 0 || statusAt > timestampAt) {
            return Result.MISSING_FIELDS;
        }

        long timestamp;
        long checksum;
        long status;
        try {
            status = Long.parseLong(json.substring(statusAt + STATUS_KEY.length(), timestampAt));
            timestamp = Long.parseLong(json.substring(timestampAt + TIMESTAMP_KEY.length(), checksumAt));
            checksum = Long.parseLong(
                    json.substring(checksumAt + CHECKSUM_KEY.length(), json.length() - JSON_END.length()));
        } catch (NumberFormatException e) {
            return Result.MISSING_FIELDS;
        }
        if (timestamp <= 0 || checksum <= 0) {
            return Result.BAD_TIMESTAMP;
        }

        long time = timestamp - (timestamp % 100);
        int fletcher = (int) (timestamp % 100);
        long hundreds = time / 100;
        int check = (int) ((timestampAt + hundreds * 17) % 1493);

        if (fletcher100(lowBytes(json, timestampAt), check, roomId) != fletcher) {
            return Result.BAD_TIMESTAMP;
        }

        long divisor = check + 29L;
        if (checksum % divisor != 0) {
            return Result.BAD_CHECKSUM;
        }
        long lastDigits = checksum / divisor - 13;
        if (lastDigits < 0 || lastDigits > 99) {
            return Result.BAD_CHECKSUM;
        }

        long securityLevel = status - (hundreds % 23);
        if (securityLevel < 0 || securityLevel > MAX_SECURITY_LEVEL) {
            return Result.BAD_STATUS;
        }
        return Result.OK;
    }

    /**
     * Builds the tail the client appends, for tests and tools: {@code body} is everything up to and
     * including the room id (and zoom), {@code timeMillis} the client clock.
     */
    public static String seal(String body, int roomId, long timeMillis, int securityLevel) {
        long lastDigits = timeMillis % 100;
        long time = timeMillis - lastDigits;
        long status = (time / 100) % 23 + securityLevel;
        String withStatus = body + STATUS_KEY + status;
        int check = (int) ((withStatus.length() + (time / 100) * 17) % 1493);
        int fletcher = fletcher100(lowBytes(withStatus, withStatus.length()), check, roomId);
        return withStatus
                + TIMESTAMP_KEY
                + (time + fletcher)
                + CHECKSUM_KEY
                + ((lastDigits + 13) * (check + 29))
                + JSON_END;
    }

    /** The client clock (milliseconds) the timestamp field encodes, rounded down to 100. */
    public static long clientTimeMillis(long timestampField) {
        return timestampField - (timestampField % 100);
    }
}
