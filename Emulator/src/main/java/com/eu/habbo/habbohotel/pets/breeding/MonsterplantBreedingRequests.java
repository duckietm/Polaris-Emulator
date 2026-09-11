package com.eu.habbo.habbohotel.pets.breeding;

import com.eu.habbo.Emulator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two monsterplants with two owners cannot be bred by one of them alone: the other has to say yes.
 * A request is the moment between the asking and the answer, and it lives only in memory because it
 * is worth nothing once anybody has left.
 */
public class MonsterplantBreedingRequests {
    /** How long an unanswered request stands. */
    static final int TIMEOUT_SECONDS = 60;

    private static final MonsterplantBreedingRequests INSTANCE = new MonsterplantBreedingRequests();

    /** The open requests, by the id of the pet that was asked for. */
    private final ConcurrentHashMap<Integer, Request> requests = new ConcurrentHashMap<>();

    private MonsterplantBreedingRequests() {}

    public static MonsterplantBreedingRequests getInstance() {
        return INSTANCE;
    }

    /** Who asked, for which two plants, and when. */
    public record Request(int requesterId, int requesterPetId, int askedPetId, int openedAt) {}

    /** Records the asking, replacing whatever stood for that plant before. */
    public void open(int requesterId, int requesterPetId, int askedPetId) {
        this.requests.put(
                askedPetId, new Request(requesterId, requesterPetId, askedPetId, Emulator.getIntUnixTimestamp()));
    }

    /** The request standing on this plant, or null when there is none or it has gone stale. */
    public Request on(int askedPetId) {
        Request request = this.requests.get(askedPetId);

        if (request == null) return null;

        if (Emulator.getIntUnixTimestamp() - request.openedAt() > TIMEOUT_SECONDS) {
            this.requests.remove(askedPetId, request);
            return null;
        }

        return request;
    }

    /** Takes the request off the plant, answered or withdrawn. */
    public Request close(int askedPetId) {
        return this.requests.remove(askedPetId);
    }
}
