package polaris.hotel;

import java.util.concurrent.TimeUnit;

public class LatencyTracker {

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
