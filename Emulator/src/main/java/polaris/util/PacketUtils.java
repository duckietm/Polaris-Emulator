package polaris.util;

import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;

public class PacketUtils {

    public static String formatPacket(ByteBuf buffer) {
        String result = buffer.toString(Charset.defaultCharset());

        for (int i = -1; i < 31; i++) {
            result = result.replace(Character.toString((char) i), "[" + i + "]");
        }

        return result;
    }
}
