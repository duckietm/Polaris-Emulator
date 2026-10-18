package com.eu.habbo.habbohotel.wired.link;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Storage of the WIRED Link networks and endpoints ({@code wired_link_networks}, {@code wired_link_endpoints}). */
public interface WiredLinkRepository {
    /** Every network the user owns, in id order; null when they could not be read. */
    List<WiredLinkNetwork> networksOf(int ownerId);

    /** Every saved endpoint of the user's Links; null when they could not be read. */
    List<WiredLinkEndpoint> endpointsOf(int ownerId);

    /** Creates a network and answers it with its id, or null when it could not be stored. */
    WiredLinkNetwork createNetwork(int ownerId, String name);

    boolean renameNetwork(int networkId, String name);

    /** Deletes the network and disconnects its Links (they keep their receive setting). */
    boolean deleteNetwork(int networkId);

    /** Stores the endpoint, replacing what the Link had. */
    boolean saveEndpoint(WiredLinkEndpoint endpoint);

    boolean deleteEndpoint(int itemId);

    /** The names of the rooms, for the connections list; rooms that are gone are left out. */
    Map<Integer, String> roomNames(Collection<Integer> roomIds);
}
