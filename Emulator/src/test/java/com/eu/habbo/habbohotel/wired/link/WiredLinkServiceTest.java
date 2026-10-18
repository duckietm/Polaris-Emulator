package com.eu.habbo.habbohotel.wired.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiredLinkServiceTest {
    private static final int OWNER = 7;
    private static final int OTHER = 8;

    private MemoryWiredLinkRepository repository;
    private WiredLinkService service;

    @BeforeEach
    void setUp() {
        this.repository = new MemoryWiredLinkRepository();
        this.service = new WiredLinkService(this.repository);
    }

    private String create(int owner, String name) {
        WiredLinkService.Result result = this.service.create(owner, 1, 100, name);
        assertTrue(result.succeeded(), "create " + name);
        return result.networks().get(0).id();
    }

    @Test
    void namesFollowTheOfficialRules() {
        assertEquals(
                WiredLinkService.BAD_NAME,
                this.service.create(OWNER, 1, 100, "   ").resultCode());
        assertEquals(
                WiredLinkService.BAD_NAME,
                this.service.create(OWNER, 1, 100, "x".repeat(256)).resultCode());
        create(OWNER, "Games");
        assertEquals(
                WiredLinkService.DUPLICATE_NAME,
                this.service.create(OWNER, 1, 100, "GAMES").resultCode());
        // Another user's network of the same name is theirs.
        create(OTHER, "Games");
    }

    @Test
    void oneLinkPerRoomPerNetwork() {
        String id = create(OWNER, "Net");
        WiredLinkService.Result first = this.service.save(OWNER, 1, 100, id, false);
        assertTrue(first.succeeded());
        assertTrue(first.hasConfiguration());
        assertEquals(id, first.selectedNetworkId());
        assertEquals(1, first.networks().get(0).connectionCount());
        assertTrue(first.networks().get(0).linkedInCurrentRoom());

        assertEquals(
                WiredLinkService.LINKED_IN_ROOM,
                this.service.save(OWNER, 1, 101, id, false).resultCode());
        assertTrue(this.service.save(OWNER, 2, 200, id, false).succeeded());
    }

    @Test
    void networksAreScopedToTheirOwner() {
        String theirs = create(OTHER, "Theirs");
        assertEquals(
                WiredLinkService.NETWORK_GONE,
                this.service.save(OWNER, 1, 100, theirs, false).resultCode());
        assertEquals(
                WiredLinkService.NETWORK_GONE,
                this.service.rename(OWNER, 1, 100, theirs, "Mine").resultCode());
        assertEquals(
                WiredLinkService.NETWORK_GONE,
                this.service.delete(OWNER, 1, 100, theirs).resultCode());
        assertInstanceOf(WiredLinkService.Result.class, this.service.connections(OWNER, 1, 100, theirs));
    }

    @Test
    void badIdsAreRefused() {
        assertEquals(
                WiredLinkService.BAD_ID,
                this.service.save(OWNER, 1, 100, "abc", false).resultCode());
        assertEquals(
                WiredLinkService.BAD_ID,
                this.service.save(OWNER, 1, 100, "99999999999", false).resultCode());
        assertEquals(
                WiredLinkService.BAD_ID, this.service.delete(OWNER, 1, 100, "0").resultCode());
        assertTrue(this.service.save(OWNER, 1, 100, "0", true).succeeded());
    }

    @Test
    void receiversAreThePeersInOtherRoomsAndTheLinkItselfWhenItReceivesLocally() {
        String id = create(OWNER, "Net");
        this.service.save(OWNER, 1, 100, id, true);
        this.service.save(OWNER, 2, 200, id, false);
        this.service.save(OWNER, 3, 300, id, false);
        this.service.save(OWNER, 4, 400, "0", true);

        List<Integer> receivers = this.service.receivers(OWNER, 100).stream()
                .map(WiredLinkEndpoint::itemId)
                .toList();
        assertEquals(List.of(100, 200, 300), receivers.stream().sorted().toList());
        assertEquals(
                List.of(400),
                this.service.receivers(OWNER, 400).stream()
                        .map(WiredLinkEndpoint::itemId)
                        .toList());
        assertTrue(this.service.receivers(OWNER, 999).isEmpty());
    }

    @Test
    void deletingANetworkDisconnectsItsLinks() {
        String id = create(OWNER, "Net");
        this.service.save(OWNER, 1, 100, id, true);
        this.service.save(OWNER, 2, 200, id, false);
        WiredLinkService.Result result = this.service.delete(OWNER, 1, 100, id);
        assertTrue(result.succeeded());
        assertEquals(id, result.removedNetworkId());
        assertFalse(this.service.endpoint(OWNER, 200).connected());
        assertEquals(
                List.of(100),
                this.service.receivers(OWNER, 100).stream()
                        .map(WiredLinkEndpoint::itemId)
                        .toList());
    }

    @Test
    void connectionsListTheNetworksLinks() {
        this.repository.rooms.put(1, "Lobby");
        String id = create(OWNER, "Net");
        this.service.save(OWNER, 1, 100, id, true);
        this.service.save(OWNER, 2, 200, id, false);
        WiredLinkService.Connections connections =
                assertInstanceOf(WiredLinkService.Connections.class, this.service.connections(OWNER, 1, 100, id));
        assertEquals(2, connections.connections().size());
        assertEquals("Lobby", connections.connections().get(0).roomName());
        assertTrue(connections.connections().get(0).receiveLocal());
    }

    @Test
    void pickingALinkUpTakesItOffItsNetwork() {
        String id = create(OWNER, "Net");
        this.service.save(OWNER, 1, 100, id, false);
        this.service.removeLink(OWNER, 100);
        assertEquals(null, this.service.endpoint(OWNER, 100));
        assertTrue(this.service.save(OWNER, 1, 101, id, false).succeeded());
    }

    @Test
    void theStateSurvivesAReload() {
        String id = create(OWNER, "Net");
        this.service.save(OWNER, 1, 100, id, true);
        WiredLinkService reloaded = new WiredLinkService(this.repository);
        WiredLinkService.Configuration configuration = reloaded.configuration(OWNER, 1, 100);
        assertEquals(id, configuration.selectedNetworkId());
        assertTrue(configuration.receiveLocal());
        assertEquals(1, configuration.networks().size());
    }
}
