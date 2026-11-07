package polaris.hotel.games.uno;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Habbi's UNO cards, as two-letter codes: a colour (R red, Y yellow, G green, B blue, W wild) and a value (0 to 9,
 * S skip, R reverse, D draw two; for a wild, C colour change or F draw four). The 108 cards of a deck.
 */
public final class UnoCards {

    public static final String COLOURS = "RYGB";
    public static final char WILD = 'W';

    private UnoCards() {}

    /** A full deck: per colour one 0, two of each 1 to 9, skip, reverse and draw two; four wilds and four draw fours. */
    public static List<String> deck() {
        List<String> cards = new ArrayList<>(108);

        for (char colour : COLOURS.toCharArray()) {
            cards.add(colour + "0");

            for (int copy = 0; copy < 2; copy++) {
                for (char value = '1'; value <= '9'; value++) cards.add("" + colour + value);

                cards.add(colour + "S");
                cards.add(colour + "R");
                cards.add(colour + "D");
            }
        }

        for (int copy = 0; copy < 4; copy++) {
            cards.add("WC");
            cards.add("WF");
        }

        return cards;
    }

    public static boolean isCard(String code) {
        if (code == null || code.length() != 2) return false;

        char colour = code.charAt(0);
        char value = code.charAt(1);

        if (colour == WILD) return value == 'C' || value == 'F';

        return COLOURS.indexOf(colour) >= 0
                && ((value >= '0' && value <= '9') || value == 'S' || value == 'R' || value == 'D');
    }

    public static boolean isWild(String code) {
        return code.charAt(0) == WILD;
    }

    public static boolean isNumber(String code) {
        char value = code.charAt(1);

        return !isWild(code) && value >= '0' && value <= '9';
    }

    public static boolean isColour(char colour) {
        return COLOURS.indexOf(colour) >= 0;
    }

    /**
     * Whether a card goes on the pile: a wild always (a draw four only without a card of the colour asked for, which
     * the caller checks with {@link #mayPlayDrawFour}), otherwise the same colour or the same value as the top card.
     */
    public static boolean matches(String card, String top, char colour) {
        if (isWild(card)) return true;

        return card.charAt(0) == colour || (!isWild(top) && card.charAt(1) == top.charAt(1));
    }

    /** A draw four may be played only by a player who has no card of the colour asked for. */
    public static boolean mayPlayDrawFour(Collection<String> hand, char colour) {
        for (String card : hand) {
            if (!isWild(card) && card.charAt(0) == colour) return false;
        }

        return true;
    }

    /** Whether this card of the hand can be played now. */
    public static boolean playable(String card, Collection<String> hand, String top, char colour) {
        if (!matches(card, top, colour)) return false;

        return !"WF".equals(card) || mayPlayDrawFour(hand, colour);
    }
}
