package polaris.hotel.wired.protocol;

/**
 * A box with the official second furni selection: its picks are the definition's {@code stuffIds2}
 * (the others write none), and come back in the save packet's second id list.
 */
public interface WiredOfficialSecondaryPicks {
    int[] officialFurniIds2();
}
