package polaris.hotel.wired.protocol;

/**
 * A box that writes its own official source fields, one entry per furni or user slot, instead of the
 * single furniSource / userSource field {@link WiredSourceFields} reads.
 */
public interface WiredOfficialSources {
    int[] officialFurniSources();

    int[] officialUserSources();
}
