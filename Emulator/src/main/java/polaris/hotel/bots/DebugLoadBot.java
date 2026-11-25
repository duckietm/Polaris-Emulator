package polaris.hotel.bots;

import java.util.concurrent.ThreadLocalRandom;
import polaris.Emulator;
import polaris.hotel.users.UserGender;

/**
 * A bot of the load test ({@code :debugload}): it lives in the room's memory only. It has no row in {@code bots},
 * so nothing about it is ever written, and it is gone when the room unloads or the test is cleared.
 *
 * <p>It speaks on a clock of its own, at a moment of its own. An ordinary bot speaks on the room's half-second
 * tick, so a crowd of them delivers its lines in bursts of dozens at once; real users speak whenever they like,
 * and that is what a load test has to look like.
 */
public class DebugLoadBot extends Bot {
    /** Ids no stored bot has: the test counts down from the top of the range. */
    public static final int FIRST_ID = Integer.MAX_VALUE - 1;

    public DebugLoadBot(
            int id, String name, String motto, String figure, UserGender gender, int ownerId, String ownerName) {
        super(id, name, motto, figure, gender, ownerId, ownerName);
    }

    /** Nothing to save. */
    @Override
    public void run() {}

    /** Nothing to save. */
    @Override
    public void onPlaceUpdate() {}

    /**
     * Says one of {@code lines} every {@code periodMs}, starting at a random moment within the first period, for as
     * long as the bot is in a room.
     */
    public void speakOnItsOwnClock(String[] lines, int periodMs) {
        if (lines == null || lines.length == 0 || periodMs <= 0) return;

        Emulator.getThreading()
                .run(
                        () -> this.speak(lines, periodMs),
                        nextPause(periodMs, ThreadLocalRandom.current().nextDouble()));
    }

    /** The first line comes anywhere in the period, so a crowd is spread over the whole of it. */
    static long nextPause(int periodMs, double fraction) {
        return Math.max(1L, (long) (periodMs * Math.min(Math.max(fraction, 0d), 1d)));
    }

    private void speak(String[] lines, int periodMs) {
        // Cleared, or the room unloaded: the clock stops with the bot.
        if (this.getRoom() == null || this.getRoomUnit() == null) return;

        this.talk(lines[ThreadLocalRandom.current().nextInt(lines.length)]);
        Emulator.getThreading().run(() -> this.speak(lines, periodMs), periodMs);
    }
}
