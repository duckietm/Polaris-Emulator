package com.eu.habbo.messages.outgoing;

/** A composer whose packet may carry a secret; packet logging prints no body for it. */
public interface SecretBearingComposer {
    boolean carriesSecret();
}
