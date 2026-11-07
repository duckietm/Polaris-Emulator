package polaris.hotel.games.uno;

import java.util.List;
import java.util.Random;

/** How a robot plays: an action card of the colour first, then a number card, a wild last. */
public final class UnoBot {

    private UnoBot() {}

    /** The card to play among the playable ones. */
    public static String choose(List<String> playable, char colour) {
        String best = playable.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (String card : playable) {
            int score = score(card, colour);

            if (score > bestScore) {
                best = card;
                bestScore = score;
            }
        }

        return best;
    }

    private static int score(String card, char colour) {
        if ("WF".equals(card)) return 0;
        if ("WC".equals(card)) return 10;

        boolean sameColour = card.charAt(0) == colour;
        boolean action = !UnoCards.isNumber(card);

        return (sameColour ? 40 : 20) + (action ? 15 : 0) + (UnoCards.isNumber(card) ? card.charAt(1) - '0' : 0);
    }

    /** The colour asked for with a wild: the one the robot holds most of, otherwise any. */
    public static char favouriteColour(List<String> hand, String played, Random random) {
        int[] counts = new int[UnoCards.COLOURS.length()];
        boolean skipped = false;

        for (String card : hand) {
            if (!skipped && card.equals(played)) {
                skipped = true;
                continue;
            }

            int index = UnoCards.COLOURS.indexOf(card.charAt(0));

            if (index >= 0) counts[index]++;
        }

        int best = random.nextInt(counts.length);

        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > counts[best]) best = index;
        }

        return UnoCards.COLOURS.charAt(best);
    }
}
