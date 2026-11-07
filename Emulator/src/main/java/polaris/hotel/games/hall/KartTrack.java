package polaris.hotel.games.hall;

/**
 * HabboKart's circuit: a closed loop through {@link #CONTROLS} (world pixels), smoothed as a uniform Catmull-Rom curve
 * of {@link #STEPS} samples a section, driven in the order of the points; the start line is on the first point. The
 * client draws the same curve from the same points (KartTrack.ts). Distances along the road are in pixels.
 */
public final class KartTrack {

    public static final int[][] CONTROLS = {
        {500, 1500}, {520, 900}, {800, 520}, {1350, 430}, {1800, 620}, {2250, 580}, {2750, 450}, {3150, 680},
        {3250, 1150}, {2950, 1520}, {2400, 1520}, {2000, 1700}, {1900, 2050}, {1600, 2330}, {1050, 2320}, {620, 2050}
    };
    public static final int STEPS = 24;
    /** The road's width (the asphalt), in pixels. */
    public static final int WIDTH = 220;

    private final double[] xs;
    private final double[] ys;
    /** The distance from the start line to each sample. */
    private final double[] along;

    private final double length;

    public static final KartTrack MAIN = new KartTrack(CONTROLS);

    KartTrack(int[][] controls) {
        int count = controls.length * STEPS;

        this.xs = new double[count];
        this.ys = new double[count];
        this.along = new double[count + 1];

        for (int section = 0; section < controls.length; section++) {
            int[] p0 = controls[Math.floorMod(section - 1, controls.length)];
            int[] p1 = controls[section];
            int[] p2 = controls[(section + 1) % controls.length];
            int[] p3 = controls[(section + 2) % controls.length];

            for (int step = 0; step < STEPS; step++) {
                double t = (double) step / STEPS;
                int index = section * STEPS + step;

                this.xs[index] = curve(p0[0], p1[0], p2[0], p3[0], t);
                this.ys[index] = curve(p0[1], p1[1], p2[1], p3[1], t);
            }
        }

        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;

            this.along[index + 1] =
                    this.along[index] + Math.hypot(this.xs[next] - this.xs[index], this.ys[next] - this.ys[index]);
        }

        this.length = this.along[count];
    }

    private static double curve(double p0, double p1, double p2, double p3, double t) {
        return 0.5
                * (2 * p1
                        + (-p0 + p2) * t
                        + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                        + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    public double length() {
        return this.length;
    }

    /** The point at a distance along the road (any distance: it loops), moved sideways: (x, y, heading). */
    public double[] pointAt(double distance, double side) {
        double wrapped = ((distance % this.length) + this.length) % this.length;
        int low = 0;
        int high = this.xs.length - 1;

        while (low < high) {
            int middle = (low + high + 1) / 2;

            if (this.along[middle] <= wrapped) low = middle;
            else high = middle - 1;
        }

        int next = (low + 1) % this.xs.length;
        double span = this.along[low + 1] - this.along[low];
        double t = span <= 0 ? 0 : (wrapped - this.along[low]) / span;
        double dx = this.xs[next] - this.xs[low];
        double dy = this.ys[next] - this.ys[low];
        double heading = Math.atan2(dy, dx);

        return new double[] {
            this.xs[low] + dx * t - Math.sin(heading) * side, this.ys[low] + dy * t + Math.cos(heading) * side, heading
        };
    }

    /** Where a point is along the road (0 to the length) and how far from its middle. */
    public double[] locate(double x, double y) {
        double best = Double.MAX_VALUE;
        double bestAlong = 0;

        for (int index = 0; index < this.xs.length; index++) {
            int next = (index + 1) % this.xs.length;
            double ax = this.xs[index];
            double ay = this.ys[index];
            double dx = this.xs[next] - ax;
            double dy = this.ys[next] - ay;
            double square = dx * dx + dy * dy;
            double t = square == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / square));
            double distance = Math.hypot(ax + dx * t - x, ay + dy * t - y);

            if (distance < best) {
                best = distance;
                bestAlong = this.along[index] + t * Math.sqrt(square);
            }
        }

        return new double[] {bestAlong, best};
    }
}
