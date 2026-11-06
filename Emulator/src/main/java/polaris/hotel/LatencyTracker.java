package polaris.hotel;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.gameclients.GameClient;

public class LatencyTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameClient.class);

    private boolean initialPing;

    private long last;
    private long average;

    public LatencyTracker() {
        this.initialPing = true;
        this.average = 0;
    }

    public void update(long latencyInNano) {
        this.last = latencyInNano;

        if (this.initialPing) {
            this.initialPing = false;
            this.average = latencyInNano;
            return;
        }

        this.average = (long) (this.average * .7f + latencyInNano * .3f);
    }

    public boolean hasInitialized() {
        return !this.initialPing;
    }

    public long getLastMs() {
        return TimeUnit.NANOSECONDS.toMillis(this.last);
    }

    public long getAverageMs() {
        return TimeUnit.NANOSECONDS.toMillis(this.average);
    }
}
