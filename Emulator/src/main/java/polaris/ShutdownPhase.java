package polaris;

enum ShutdownPhase {
    RUNNING,
    ANNOUNCE,
    QUIESCE,
    DRAIN,
    CHECKPOINT,
    STOP
}
