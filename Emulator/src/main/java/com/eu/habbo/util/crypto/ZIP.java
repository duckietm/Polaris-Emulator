package com.eu.habbo.util.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class ZIP {
    public static byte[] inflate(byte[] data) {
        try {
            byte[] buffer = new byte[data.length * 5];
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            byte[] output = outputStream.toByteArray();

            inflater.end();
            return output;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * Inflates zlib data, refusing more than {@code maxInflatedBytes} of output and truncated or
     * malformed input, so a small payload cannot expand into a large allocation.
     */
    public static byte[] inflate(byte[] data, int maxInflatedBytes) throws IOException {
        byte[] buffer = new byte[8192];
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        try (ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream(Math.max(16, Math.min(data.length * 4, maxInflatedBytes)))) {
            int total = 0;
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException e) {
                    throw new IOException("Malformed compressed payload", e);
                }
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IOException("Truncated compressed payload");
                    }
                    break;
                }
                if (total + count > maxInflatedBytes) {
                    throw new IOException("Inflated payload exceeds limit: " + maxInflatedBytes);
                }
                outputStream.write(buffer, 0, count);
                total += count;
            }
            return outputStream.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
