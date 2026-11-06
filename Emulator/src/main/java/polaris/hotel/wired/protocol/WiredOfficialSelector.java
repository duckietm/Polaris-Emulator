package polaris.hotel.wired.protocol;

/**
 * A selector that writes the official definition itself: its sources and its filter and invert
 * switches go in the official fields instead of its int params.
 */
public interface WiredOfficialSelector extends WiredOfficialSources {
    boolean selectorFilter();

    boolean selectorInvert();
}
