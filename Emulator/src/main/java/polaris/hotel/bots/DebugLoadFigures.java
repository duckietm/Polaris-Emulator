package polaris.hotel.bots;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Looks for the load test bots: every one different, so the client composes and caches a picture per bot as it
 * does for a room full of real users. The parts are the classic default sets and colours that every figuredata
 * has, so the looks are valid in any hotel.
 */
public final class DebugLoadFigures {
    /** A look and the gender it belongs to. */
    public record Look(String figure, boolean male) {}

    private static final int[] SKIN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private static final int[] HAIR_COLOURS = {31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 42, 45, 61};
    private static final int[] CLOTH_COLOURS = {
        62, 63, 64, 66, 68, 70, 72, 73, 74, 76, 78, 80, 82, 84, 85, 88, 90, 91, 92, 96, 100, 102, 104, 106, 110
    };

    private static final int[] MALE_HEAD = {180, 185, 190, 195, 200, 205};
    private static final int[] FEMALE_HEAD = {600, 605, 610, 615, 620, 625};
    private static final int[] MALE_HAIR = {100, 105, 110, 115, 125, 135, 145, 155, 165, 170};
    private static final int[] FEMALE_HAIR = {500, 505, 510, 515, 520, 525, 530, 535, 540, 545};
    private static final int[] MALE_CHEST = {210, 215, 220, 225, 230, 235, 240, 245, 250, 255};
    private static final int[] FEMALE_CHEST = {630, 635, 640, 645, 650, 655, 660, 665, 670, 675};
    private static final int[] MALE_LEGS = {270, 275, 280, 285, 290};
    private static final int[] FEMALE_LEGS = {695, 700, 705, 710, 715, 716, 720};
    private static final int[] MALE_SHOES = {290, 295, 300, 305};
    private static final int[] FEMALE_SHOES = {725, 730, 735, 740};

    /** Worn by some: hats, waist, eyewear and face accessories that both genders have. */
    private static final int[] HATS = {1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010};

    private static final int[] WAIST = {2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009};
    private static final int[] EYEWEAR = {1401, 1402, 1403, 1404, 1405, 1406};
    private static final int[] FACE = {1201, 1202, 1203, 1204, 1205, 1206};

    private DebugLoadFigures() {}

    /** {@code count} looks, no two the same. */
    public static List<Look> different(int count, Random random) {
        Set<String> seen = new LinkedHashSet<>();
        List<Look> looks = new ArrayList<>();

        // The parts give millions of combinations; the bound only guards against a broken random source.
        for (int attempt = 0; looks.size() < count && attempt < count * 50; attempt++) {
            Look look = one(random);
            if (seen.add(look.figure())) looks.add(look);
        }
        return looks;
    }

    static Look one(Random random) {
        boolean male = random.nextBoolean();
        StringBuilder figure = new StringBuilder();

        part(figure, "hd", pick(random, male ? MALE_HEAD : FEMALE_HEAD), pick(random, SKIN));
        part(figure, "hr", pick(random, male ? MALE_HAIR : FEMALE_HAIR), pick(random, HAIR_COLOURS));
        part(figure, "ch", pick(random, male ? MALE_CHEST : FEMALE_CHEST), pick(random, CLOTH_COLOURS));
        part(figure, "lg", pick(random, male ? MALE_LEGS : FEMALE_LEGS), pick(random, CLOTH_COLOURS));
        part(figure, "sh", pick(random, male ? MALE_SHOES : FEMALE_SHOES), pick(random, CLOTH_COLOURS));

        if (random.nextInt(4) == 0) part(figure, "ha", pick(random, HATS), pick(random, CLOTH_COLOURS));
        if (random.nextInt(5) == 0) part(figure, "wa", pick(random, WAIST), pick(random, CLOTH_COLOURS));
        if (random.nextInt(5) == 0) part(figure, "ea", pick(random, EYEWEAR), pick(random, CLOTH_COLOURS));
        if (random.nextInt(8) == 0) part(figure, "fa", pick(random, FACE), pick(random, CLOTH_COLOURS));

        return new Look(figure.toString(), male);
    }

    private static int pick(Random random, int[] values) {
        return values[random.nextInt(values.length)];
    }

    private static void part(StringBuilder figure, String type, int set, int colour) {
        if (figure.length() > 0) figure.append('.');
        figure.append(type).append('-').append(set).append('-').append(colour);
    }
}
