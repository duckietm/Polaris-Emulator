package com.eu.habbo.habbohotel.items.interactions.mysterybox;

/**
 * A box waiting for its key: which furniture, whose it is, and when the wait started.
 */
public record MysteryBoxWait(int boxItemId, int boxOwnerId, int keyItemId, int keyOwnerId, int openedAt) {}
