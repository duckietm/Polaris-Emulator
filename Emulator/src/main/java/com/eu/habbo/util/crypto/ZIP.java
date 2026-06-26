package com.eu.habbo.util.crypto;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

public class ZIP {
    public static byte[] inflate(byte[] data) {
        try {
            byte[] buffer = new byte[data.length * 5];
            var inflater = new Inflater();
            inflater.setInput(data);

            var outputStream = new ByteArrayOutputStream(data.length);
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
}