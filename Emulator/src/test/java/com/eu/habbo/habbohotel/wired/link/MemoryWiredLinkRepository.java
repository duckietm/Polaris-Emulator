package com.eu.habbo.habbohotel.wired.link;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An in-memory {@link WiredLinkRepository} for tests. */
final class MemoryWiredLinkRepository implements WiredLinkRepository {
    final Map<Integer, WiredLinkNetwork> networks = new LinkedHashMap<>();
    final Map<Integer, WiredLinkEndpoint> endpoints = new LinkedHashMap<>();
    final Map<Integer, String> rooms = new HashMap<>();
    private int nextId = 1;

    @Override
    public List<WiredLinkNetwork> networksOf(int ownerId) {
        return this.networks.values().stream()
                .filter(n -> n.ownerId() == ownerId)
                .toList();
    }

    @Override
    public List<WiredLinkEndpoint> endpointsOf(int ownerId) {
        return this.endpoints.values().stream()
                .filter(e -> e.ownerId() == ownerId)
                .toList();
    }

    @Override
    public WiredLinkNetwork createNetwork(int ownerId, String name) {
        WiredLinkNetwork network = new WiredLinkNetwork(this.nextId++, ownerId, name);
        this.networks.put(network.id(), network);
        return network;
    }

    @Override
    public boolean renameNetwork(int networkId, String name) {
        WiredLinkNetwork network = this.networks.get(networkId);
        if (network == null) return false;
        this.networks.put(networkId, new WiredLinkNetwork(networkId, network.ownerId(), name));
        return true;
    }

    @Override
    public boolean deleteNetwork(int networkId) {
        for (WiredLinkEndpoint e : new ArrayList<>(this.endpoints.values())) {
            if (e.networkId() == networkId) {
                this.endpoints.put(
                        e.itemId(), new WiredLinkEndpoint(e.itemId(), 0, e.roomId(), e.ownerId(), e.receiveLocal()));
            }
        }
        return this.networks.remove(networkId) != null;
    }

    @Override
    public boolean saveEndpoint(WiredLinkEndpoint endpoint) {
        this.endpoints.put(endpoint.itemId(), endpoint);
        return true;
    }

    @Override
    public boolean deleteEndpoint(int itemId) {
        this.endpoints.remove(itemId);
        return true;
    }

    @Override
    public Map<Integer, String> roomNames(Collection<Integer> roomIds) {
        Map<Integer, String> names = new HashMap<>();
        for (Integer id : roomIds) {
            if (this.rooms.containsKey(id)) names.put(id, this.rooms.get(id));
        }
        return names;
    }
}
