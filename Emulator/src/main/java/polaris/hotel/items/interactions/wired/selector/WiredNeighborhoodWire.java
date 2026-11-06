package polaris.hotel.items.interactions.wired.selector;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * The official neighborhood selectors' wire form (AIR {@code InNeighborhood}, {@code SpiralUtils}):
 * {@code [isUser, rootX, rootY, ...drawing]}, the drawing a bit per tile of a radius-10 floor, in
 * spiral order from its centre, packed into ints from the lowest bit up. The source is the official
 * one (0 trigger, 11 clicked user, 100 picks, 201 signal) in the furni or user source slot.
 *
 * <p>The boxes keep their own model (a private source number, the root as target offset and the drawn
 * tiles as offsets from the centre); this class converts between the two.
 */
public final class WiredNeighborhoodWire {
    public static final int RADIUS = 10;
    private static final int DIMENSION = RADIUS * 2 + 1;
    /** Ints the drawing takes: one bit per tile, rounded up to whole ints. */
    public static final int MASK_INTS = (DIMENSION * DIMENSION + 31) / 32;
    /** The root may lie off the drawing, as far as the official inputs go. */
    public static final int MAX_ROOT = 64;

    // The boxes' private source numbers.
    static final int SOURCE_USER_TRIGGER = 0;
    static final int SOURCE_USER_SIGNAL = 1;
    static final int SOURCE_USER_CLICKED = 2;
    static final int SOURCE_FURNI_TRIGGER = 3;
    static final int SOURCE_FURNI_PICKED = 4;
    static final int SOURCE_FURNI_SIGNAL = 5;

    /** Spiral position {@code rank -> {x, y}}, from the centre. */
    private static final int[][] SPIRAL = walkSpiral();

    private WiredNeighborhoodWire() {}

    /** The tile offsets drawn in {@code mask} (the int params after root). */
    public static List<int[]> decode(int[] params, int from) {
        List<int[]> tiles = new ArrayList<>();
        for (int rank = 0; rank < SPIRAL.length; rank++) {
            int index = from + (rank >> 5);
            if (index >= params.length) break;
            if ((params[index] >>> (rank & 31) & 1) == 1) {
                tiles.add(new int[] {SPIRAL[rank][0], SPIRAL[rank][1]});
            }
        }
        return tiles;
    }

    /** The drawing of {@code tiles}; offsets outside the floor are dropped. */
    public static int[] encode(List<int[]> tiles) {
        int[] mask = new int[MASK_INTS];
        for (int rank = 0; rank < SPIRAL.length; rank++) {
            for (int[] tile : tiles) {
                if (tile[0] == SPIRAL[rank][0] && tile[1] == SPIRAL[rank][1]) {
                    mask[rank >> 5] |= 1 << (rank & 31);
                    break;
                }
            }
        }
        return mask;
    }

    public static boolean isUserSource(int privateSource) {
        return privateSource <= SOURCE_USER_CLICKED;
    }

    /** The official source of a private source number. */
    public static int officialSource(int privateSource) {
        return switch (privateSource) {
            case SOURCE_USER_SIGNAL, SOURCE_FURNI_SIGNAL -> WiredSourceUtil.SOURCE_SIGNAL;
            case SOURCE_USER_CLICKED -> WiredSourceUtil.SOURCE_CLICKED_USER;
            case SOURCE_FURNI_PICKED -> WiredSourceUtil.SOURCE_SELECTED;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    /** The private source number of an official source on the user or the furni side. */
    public static int privateSource(boolean user, int officialSource) {
        if (user) {
            return switch (officialSource) {
                case WiredSourceUtil.SOURCE_SIGNAL -> SOURCE_USER_SIGNAL;
                case WiredSourceUtil.SOURCE_CLICKED_USER -> SOURCE_USER_CLICKED;
                default -> SOURCE_USER_TRIGGER;
            };
        }
        return switch (officialSource) {
            case WiredSourceUtil.SOURCE_SIGNAL -> SOURCE_FURNI_SIGNAL;
            case WiredSourceUtil.SOURCE_SELECTED -> SOURCE_FURNI_PICKED;
            default -> SOURCE_FURNI_TRIGGER;
        };
    }

    public static int clampRoot(int value) {
        return Math.max(-MAX_ROOT, Math.min(MAX_ROOT, value));
    }

    /** {@code SpiralUtils.walkSpiral}: right 1, up 1, left 2, down 2, right 3, ... */
    private static int[][] walkSpiral() {
        int total = DIMENSION * DIMENSION;
        int[][] spiral = new int[total][];
        int rank = 0;
        int x = 0;
        int y = 0;
        int dx = 1;
        int dy = 0;
        for (int length = 1; length <= DIMENSION; length++) {
            for (int leg = 0; leg < 2; leg++) {
                for (int step = 0; step < length; step++) {
                    spiral[rank++] = new int[] {x, y};
                    x += dx;
                    y += dy;
                    if (rank == total) return spiral;
                }
                // nextDirection: (1,0)->(0,-1)->(-1,0)->(0,1)->(1,0).
                int nextDx;
                int nextDy;
                if (dx == 1 && dy == 0) {
                    nextDx = 0;
                    nextDy = -1;
                } else if (dx == 0 && dy == -1) {
                    nextDx = -1;
                    nextDy = 0;
                } else if (dx == -1 && dy == 0) {
                    nextDx = 0;
                    nextDy = 1;
                } else {
                    nextDx = 1;
                    nextDy = 0;
                }
                dx = nextDx;
                dy = nextDy;
            }
        }
        return spiral;
    }
}
