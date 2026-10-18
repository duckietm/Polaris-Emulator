package com.eu.habbo.habbohotel.wired.link;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The WIRED Link networks (AIR {@code WiredLinkController}): a user names networks, and connects each
 * of their Link furni to at most one of them, one Link per room per network. A message sent through a
 * Link reaches the other Links of its network; a Link that receives locally also gets its own room's.
 *
 * <p>Everything is scoped to one owner: a network belongs to the user who made it, and only Links that
 * user owns join it. The packet handlers check that the user owns the Link and controls its room; this
 * class checks the network side. Result codes are the official {@code wiredlink.error.1..8}.
 */
public final class WiredLinkService {
    public static final int OP_SAVE = 1;
    public static final int OP_CREATE = 2;
    public static final int OP_RENAME = 3;
    public static final int OP_DELETE = 4;
    public static final int OP_CONNECTIONS = 5;

    public static final int OK = 0;
    /** Only the furni owner can configure the Link. */
    public static final int NOT_OWNER = 1;
    /** The network is gone or is not the user's. */
    public static final int NETWORK_GONE = 2;
    /** Blank name, or longer than 255. */
    public static final int BAD_NAME = 3;
    /** The user has a network with that name (case-insensitive). */
    public static final int DUPLICATE_NAME = 4;
    /** Another Link of the room is on the network. */
    public static final int LINKED_IN_ROOM = 5;
    /** Not a network id. */
    public static final int BAD_ID = 6;
    /** The Link is no longer in the room. */
    public static final int LINK_GONE = 7;
    /** The user is at the network limit. */
    public static final int NETWORK_LIMIT = 8;

    public static final String NO_NETWORK = "0";
    public static final int MAX_NAME_LENGTH = 255;
    public static final String MAX_NETWORKS_SETTING = "wired.link.max_networks";
    public static final int DEFAULT_MAX_NETWORKS = 50;
    static final int MAX_NETWORKS_LIMIT = 1000;
    static final int MAX_CONNECTIONS_LISTED = 200;

    private static final Pattern NON_BLANK = Pattern.compile("\\S");
    private static final Pattern NETWORK_ID = Pattern.compile("[0-9]{1,10}");

    private static final WiredLinkService INSTANCE = new WiredLinkService(new JdbcWiredLinkRepository());

    private final WiredLinkRepository repository;
    private final Map<Integer, OwnerState> owners = new ConcurrentHashMap<>();
    private final Map<Integer, OpenTarget> openTargets = new ConcurrentHashMap<>();

    public WiredLinkService(WiredLinkRepository repository) {
        this.repository = repository;
    }

    public static WiredLinkService get() {
        return INSTANCE;
    }

    /** One network as the dialog lists it. */
    public record NetworkView(String id, String name, int connectionCount, boolean linkedInCurrentRoom) {}

    /** The dialog's opening state (AIR 3405). */
    public record Configuration(
            int roomId, int furniId, String selectedNetworkId, boolean receiveLocal, List<NetworkView> networks) {}

    /** The answer to a save, create, rename or delete (AIR 3961). */
    public record Result(
            int roomId,
            int furniId,
            int operation,
            int resultCode,
            boolean hasConfiguration,
            String selectedNetworkId,
            boolean receiveLocal,
            String removedNetworkId,
            List<NetworkView> networks) {
        public static Result failure(int roomId, int furniId, int operation, int resultCode) {
            return new Result(roomId, furniId, operation, resultCode, false, NO_NETWORK, false, NO_NETWORK, List.of());
        }

        public boolean succeeded() {
            return this.resultCode == OK;
        }
    }

    /** One connected Link of a network (AIR WiredLinkConnection). */
    public record Connection(int roomId, String roomName, int furniId, boolean buildersClub, boolean receiveLocal) {}

    /** The connected Links of a network (AIR 3962). */
    public record Connections(int roomId, int furniId, String networkId, List<Connection> connections) {}

    /** The Link whose dialog a user last opened. */
    public record OpenTarget(int roomId, int furniId) {}

    /** Whether the name is one the official dialog accepts: at most 255 characters with something not blank. */
    public static boolean validName(String name) {
        return name != null
                && name.length() <= MAX_NAME_LENGTH
                && NON_BLANK.matcher(name).find();
    }

    /** A network id from the wire: 0 for none, -1 when it is not an id at all. */
    static int parseNetworkId(String text) {
        if (text == null || !NETWORK_ID.matcher(text).matches()) {
            return -1;
        }
        long value = Long.parseLong(text);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    public Configuration configuration(int ownerId, int roomId, int itemId) {
        OwnerState state = this.state(ownerId);
        this.openTargets.put(ownerId, new OpenTarget(roomId, itemId));
        synchronized (state) {
            WiredLinkEndpoint endpoint = state.endpoints.get(itemId);
            String selected =
                    endpoint != null && endpoint.connected() ? Integer.toString(endpoint.networkId()) : NO_NETWORK;
            boolean receiveLocal = endpoint != null && endpoint.receiveLocal();
            List<NetworkView> views = new ArrayList<>();
            for (WiredLinkNetwork network : state.networks.values()) {
                views.add(state.view(network, roomId));
            }
            return new Configuration(roomId, itemId, selected, receiveLocal, views);
        }
    }

    public OpenTarget openTarget(int ownerId) {
        return this.openTargets.get(ownerId);
    }

    public void forgetOpenTarget(int ownerId) {
        this.openTargets.remove(ownerId);
    }

    public Result save(int ownerId, int roomId, int itemId, String networkIdText, boolean receiveLocal) {
        int networkId = parseNetworkId(networkIdText);
        if (networkId < 0) {
            return Result.failure(roomId, itemId, OP_SAVE, BAD_ID);
        }
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            if (!state.loaded) {
                return Result.failure(roomId, itemId, OP_SAVE, NETWORK_GONE);
            }
            if (networkId > 0 && !state.networks.containsKey(networkId)) {
                return Result.failure(roomId, itemId, OP_SAVE, NETWORK_GONE);
            }
            if (networkId > 0) {
                for (WiredLinkEndpoint other : state.endpoints.values()) {
                    if (other.itemId() != itemId && other.networkId() == networkId && other.roomId() == roomId) {
                        return Result.failure(roomId, itemId, OP_SAVE, LINKED_IN_ROOM);
                    }
                }
            }

            WiredLinkEndpoint previous = state.endpoints.get(itemId);
            WiredLinkEndpoint next = new WiredLinkEndpoint(itemId, networkId, roomId, ownerId, receiveLocal);
            if (!this.repository.saveEndpoint(next)) {
                return Result.failure(roomId, itemId, OP_SAVE, LINK_GONE);
            }
            state.endpoints.put(itemId, next);

            Set<Integer> changed = new LinkedHashSet<>();
            if (previous != null && previous.connected()) changed.add(previous.networkId());
            if (next.connected()) changed.add(next.networkId());
            List<NetworkView> views = new ArrayList<>();
            for (Integer id : changed) {
                WiredLinkNetwork network = state.networks.get(id);
                if (network != null) views.add(state.view(network, roomId));
            }
            return new Result(
                    roomId,
                    itemId,
                    OP_SAVE,
                    OK,
                    true,
                    next.connected() ? Integer.toString(networkId) : NO_NETWORK,
                    receiveLocal,
                    NO_NETWORK,
                    views);
        }
    }

    public Result create(int ownerId, int roomId, int itemId, String name) {
        if (!validName(name)) {
            return Result.failure(roomId, itemId, OP_CREATE, BAD_NAME);
        }
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            if (!state.loaded) {
                return Result.failure(roomId, itemId, OP_CREATE, NETWORK_GONE);
            }
            if (state.hasName(name, 0)) {
                return Result.failure(roomId, itemId, OP_CREATE, DUPLICATE_NAME);
            }
            if (state.networks.size() >= maxNetworks()) {
                return Result.failure(roomId, itemId, OP_CREATE, NETWORK_LIMIT);
            }
            WiredLinkNetwork network = this.repository.createNetwork(ownerId, name);
            if (network == null) {
                return Result.failure(roomId, itemId, OP_CREATE, NETWORK_LIMIT);
            }
            state.networks.put(network.id(), network);
            return success(roomId, itemId, OP_CREATE, NO_NETWORK, List.of(state.view(network, roomId)));
        }
    }

    public Result rename(int ownerId, int roomId, int itemId, String networkIdText, String name) {
        int networkId = parseNetworkId(networkIdText);
        if (networkId <= 0) {
            return Result.failure(roomId, itemId, OP_RENAME, BAD_ID);
        }
        if (!validName(name)) {
            return Result.failure(roomId, itemId, OP_RENAME, BAD_NAME);
        }
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            WiredLinkNetwork network = state.networks.get(networkId);
            if (network == null) {
                return Result.failure(roomId, itemId, OP_RENAME, NETWORK_GONE);
            }
            if (state.hasName(name, networkId)) {
                return Result.failure(roomId, itemId, OP_RENAME, DUPLICATE_NAME);
            }
            if (!this.repository.renameNetwork(networkId, name)) {
                return Result.failure(roomId, itemId, OP_RENAME, NETWORK_GONE);
            }
            WiredLinkNetwork renamed = new WiredLinkNetwork(networkId, ownerId, name);
            state.networks.put(networkId, renamed);
            return success(roomId, itemId, OP_RENAME, NO_NETWORK, List.of(state.view(renamed, roomId)));
        }
    }

    public Result delete(int ownerId, int roomId, int itemId, String networkIdText) {
        int networkId = parseNetworkId(networkIdText);
        if (networkId <= 0) {
            return Result.failure(roomId, itemId, OP_DELETE, BAD_ID);
        }
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            if (!state.networks.containsKey(networkId)) {
                return Result.failure(roomId, itemId, OP_DELETE, NETWORK_GONE);
            }
            if (!this.repository.deleteNetwork(networkId)) {
                return Result.failure(roomId, itemId, OP_DELETE, NETWORK_GONE);
            }
            state.networks.remove(networkId);
            for (WiredLinkEndpoint endpoint : new ArrayList<>(state.endpoints.values())) {
                if (endpoint.networkId() == networkId) {
                    state.endpoints.put(
                            endpoint.itemId(),
                            new WiredLinkEndpoint(
                                    endpoint.itemId(), 0, endpoint.roomId(), ownerId, endpoint.receiveLocal()));
                }
            }
            return success(roomId, itemId, OP_DELETE, Integer.toString(networkId), List.of());
        }
    }

    /** The connected Links of one of the user's networks, or a failed result when it is not theirs. */
    public Object connections(int ownerId, int roomId, int itemId, String networkIdText) {
        int networkId = parseNetworkId(networkIdText);
        if (networkId <= 0) {
            return Result.failure(roomId, itemId, OP_CONNECTIONS, BAD_ID);
        }
        OwnerState state = this.state(ownerId);
        List<WiredLinkEndpoint> members = new ArrayList<>();
        synchronized (state) {
            if (!state.networks.containsKey(networkId)) {
                return Result.failure(roomId, itemId, OP_CONNECTIONS, NETWORK_GONE);
            }
            for (WiredLinkEndpoint endpoint : state.endpoints.values()) {
                if (endpoint.networkId() == networkId) members.add(endpoint);
            }
        }
        members.sort(Comparator.comparingInt(WiredLinkEndpoint::roomId).thenComparingInt(WiredLinkEndpoint::itemId));
        if (members.size() > MAX_CONNECTIONS_LISTED) {
            members = members.subList(0, MAX_CONNECTIONS_LISTED);
        }
        Set<Integer> roomIds = new LinkedHashSet<>();
        for (WiredLinkEndpoint endpoint : members) roomIds.add(endpoint.roomId());
        Map<Integer, String> names = this.repository.roomNames(roomIds);
        List<Connection> connections = new ArrayList<>();
        for (WiredLinkEndpoint endpoint : members) {
            connections.add(new Connection(
                    endpoint.roomId(),
                    names.getOrDefault(endpoint.roomId(), ""),
                    endpoint.itemId(),
                    false,
                    endpoint.receiveLocal()));
        }
        return new Connections(roomId, itemId, Integer.toString(networkId), connections);
    }

    /** A Link left its room (picked up or deleted): it is no longer on any network. */
    public void removeLink(int ownerId, int itemId) {
        OwnerState state = this.owners.get(ownerId);
        if (state != null) {
            synchronized (state) {
                if (state.endpoints.remove(itemId) == null) {
                    return;
                }
            }
        }
        this.repository.deleteEndpoint(itemId);
    }

    /** The saved settings of one of the user's Links, or null. */
    public WiredLinkEndpoint endpoint(int ownerId, int itemId) {
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            return state.endpoints.get(itemId);
        }
    }

    /**
     * Where a message sent through the Link goes: the Link itself when it receives locally, then the
     * Links of its network in the other rooms.
     */
    public List<WiredLinkEndpoint> receivers(int ownerId, int itemId) {
        OwnerState state = this.state(ownerId);
        synchronized (state) {
            WiredLinkEndpoint sender = state.endpoints.get(itemId);
            if (sender == null) {
                return List.of();
            }
            List<WiredLinkEndpoint> receivers = new ArrayList<>();
            if (sender.receiveLocal()) {
                receivers.add(sender);
            }
            if (sender.connected() && state.networks.containsKey(sender.networkId())) {
                for (WiredLinkEndpoint peer : state.endpoints.values()) {
                    if (peer.itemId() != itemId
                            && peer.networkId() == sender.networkId()
                            && peer.roomId() != sender.roomId()) {
                        receivers.add(peer);
                    }
                }
            }
            return receivers;
        }
    }

    /** Drops what is cached for the user; the next use reads the database again. */
    public void evict(int ownerId) {
        this.owners.remove(ownerId);
        this.openTargets.remove(ownerId);
    }

    private static Result success(int roomId, int itemId, int operation, String removed, List<NetworkView> views) {
        return new Result(roomId, itemId, operation, OK, false, NO_NETWORK, false, removed, views);
    }

    /** {@code wired.link.max_networks}, 1 to 1000; 50 when unset. */
    static int maxNetworks() {
        ConfigurationManager config = WiredPlatform.configuration();
        int value = config == null ? DEFAULT_MAX_NETWORKS : config.getInt(MAX_NETWORKS_SETTING, DEFAULT_MAX_NETWORKS);
        return Math.max(1, Math.min(MAX_NETWORKS_LIMIT, value));
    }

    private OwnerState state(int ownerId) {
        OwnerState state = this.owners.computeIfAbsent(ownerId, id -> new OwnerState());
        synchronized (state) {
            if (!state.loaded) {
                List<WiredLinkNetwork> networks = this.repository.networksOf(ownerId);
                List<WiredLinkEndpoint> endpoints = this.repository.endpointsOf(ownerId);
                if (networks != null && endpoints != null) {
                    state.load(networks, endpoints);
                }
            }
        }
        return state;
    }

    /** One user's networks and Links. Guarded by its own monitor. */
    private static final class OwnerState {
        private final Map<Integer, WiredLinkNetwork> networks = new LinkedHashMap<>();
        private final Map<Integer, WiredLinkEndpoint> endpoints = new HashMap<>();
        private boolean loaded;

        void load(Collection<WiredLinkNetwork> networks, Collection<WiredLinkEndpoint> endpoints) {
            this.networks.clear();
            this.endpoints.clear();
            for (WiredLinkNetwork network : networks) this.networks.put(network.id(), network);
            for (WiredLinkEndpoint endpoint : endpoints) {
                boolean known = endpoint.networkId() <= 0 || this.networks.containsKey(endpoint.networkId());
                this.endpoints.put(
                        endpoint.itemId(),
                        known
                                ? endpoint
                                : new WiredLinkEndpoint(
                                        endpoint.itemId(),
                                        0,
                                        endpoint.roomId(),
                                        endpoint.ownerId(),
                                        endpoint.receiveLocal()));
            }
            this.loaded = true;
        }

        boolean hasName(String name, int exceptNetworkId) {
            for (WiredLinkNetwork network : this.networks.values()) {
                if (network.id() != exceptNetworkId && network.name().equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        NetworkView view(WiredLinkNetwork network, int roomId) {
            int count = 0;
            boolean inRoom = false;
            for (WiredLinkEndpoint endpoint : this.endpoints.values()) {
                if (endpoint.networkId() == network.id()) {
                    count++;
                    inRoom |= endpoint.roomId() == roomId;
                }
            }
            return new NetworkView(network.wireId(), network.name(), count, inRoom);
        }
    }
}
