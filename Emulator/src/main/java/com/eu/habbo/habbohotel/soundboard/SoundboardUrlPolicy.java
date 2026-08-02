package com.eu.habbo.habbohotel.soundboard;

import java.net.URI;
import java.net.URISyntaxException;

public final class SoundboardUrlPolicy {
    private static final int MAX_URL_LENGTH = 255;

    private SoundboardUrlPolicy() {}

    public static boolean isAllowed(String value) {
        if (value == null) {
            return false;
        }

        String url = value.trim();
        if (url.isEmpty() || url.length() > MAX_URL_LENGTH) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme == null || scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
