package com.eu.habbo;

enum ShutdownPhase {
    RUNNING,
    ANNOUNCE,
    QUIESCE,
    DRAIN,
    CHECKPOINT,
    STOP
}
