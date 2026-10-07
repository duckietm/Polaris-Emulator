package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;

public class ShutdownEmulator implements Runnable {
    public static volatile boolean instantiated = false;
    public static volatile int timestamp = 0;

    // The shutdown moment this task was scheduled for; 0 means it always exits.
    private final int deadline;

    public ShutdownEmulator(ServerMessage message) {
        this(message, 0);
    }

    public ShutdownEmulator(ServerMessage message, int deadline) {
        this.deadline = deadline;

        if (!instantiated) {
            instantiated = true;

            if (message != null) {
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(message);
            }
        }
    }

    /**
     * A countdown is superseded once it was cancelled (timestamp reset) or replaced by a newer one.
     */
    static boolean isSuperseded(int deadline, int currentTimestamp) {
        return deadline > 0 && currentTimestamp != deadline;
    }

    /**
     * Forgets the pending countdown so its task no longer exits and a new countdown is announced again.
     */
    public static void clearPending() {
        timestamp = 0;
        instantiated = false;
    }

    @Override
    public void run() {
        if (isSuperseded(this.deadline, timestamp)) {
            return;
        }

        Emulator.getRuntime().exit(0);
    }
}
