package com.eu.habbo.habbohotel.soundboard;

import java.util.regex.Pattern;

/**
 * A soundboard pad is addressed by classname, resolved client-side against
 * gamedata/SoundData.json — the same shape furniture uses.
 *
 * The alphabet is deliberately narrow: the classname ends up in a URL path
 * segment, so anything that could traverse directories or need escaping is
 * rejected outright rather than sanitised.
 */
public final class SoundboardClassnamePolicy {
    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_-]{1,64}$");

    private SoundboardClassnamePolicy() {}

    public static boolean isAllowed(String value) {
        return value != null && ALLOWED.matcher(value.trim()).matches();
    }

    /** A pad must resolve somehow: through the asset manifest, or an explicit URL. */
    public static boolean isAddressable(String classname, String url) {
        boolean hasClassname = classname != null && !classname.isBlank();
        boolean hasUrl = url != null && !url.isBlank();

        if (hasClassname && !isAllowed(classname)) return false;
        if (hasUrl && !SoundboardUrlPolicy.isAllowed(url)) return false;

        return hasClassname || hasUrl;
    }
}
