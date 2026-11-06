package polaris.crypto.utils;

import java.math.BigInteger;
import java.util.Arrays;

public class BigIntegerUtils {

    public static byte[] toUnsignedByteArray(BigInteger bigInteger) {
        byte[] bytes = bigInteger.toByteArray();
        if (bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }

        return bytes;
    }
}
