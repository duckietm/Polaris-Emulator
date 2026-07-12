package com.eu.habbo.messages.incoming.handshake;

import java.util.Arrays;

final class ClientReleaseGuard {
    static final String DEFAULT_ALLOWED_RELEASES = "NITRO-3-6-0";
    private static final int MAX_RELEASE_LENGTH = 64;

    private ClientReleaseGuard() {
    }

    static String normalize(String release) {
        return release == null ? "" : release.trim();
    }

    static boolean isAllowed(String release, String configuredReleases) {
        String normalized = normalize(release);
        if (normalized.isEmpty() || normalized.length() > MAX_RELEASE_LENGTH
                || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            return false;
        }

        String allowed = normalize(configuredReleases);
        if (allowed.isEmpty()) {
            allowed = DEFAULT_ALLOWED_RELEASES;
        }

        return Arrays.stream(allowed.split("[;,]"))
                .map(ClientReleaseGuard::normalize)
                .anyMatch(normalized::equalsIgnoreCase);
    }
}
