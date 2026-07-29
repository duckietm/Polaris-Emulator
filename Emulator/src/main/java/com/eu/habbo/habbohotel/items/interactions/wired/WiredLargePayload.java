package com.eu.habbo.habbohotel.items.interactions.wired;

/** Marker for bounded Wired editors whose structured settings legitimately exceed legacy text limits. */
public interface WiredLargePayload {
    int MAX_STRING_PARAM_LENGTH = 32_768;
}
