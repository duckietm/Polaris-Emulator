package polaris.hotel.catalog.specialitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;

/** A free claim with a choice (the cursed dragon adoption): which furni is given, and that it is given once. */
class SpecialItemClaimChoiceTest {
    private static final String DRAGON = "cursed_dragon_adoption";

    private final DataSource dataSource = mock(DataSource.class);
    private final Map<String, Integer> offers = new HashMap<>();
    private final Map<String, Integer> choices = new HashMap<>();
    private final Set<String> claims = new HashSet<>();
    /** Choices that give pet 37: "claim#choice" to the breed. */
    private final Map<String, Integer> pets = new HashMap<>();

    private final Map<String, String> badges = new HashMap<>();

    private final Set<String> disabled = new HashSet<>();
    private final List<Integer> created = new ArrayList<>();
    private final UserItem item = mock(UserItem.class);
    private boolean creates = true;
    private SpecialItemClaimService service;

    @BeforeEach
    void setUp() throws Exception {
        when(this.dataSource.getConnection()).thenAnswer(call -> {
            Connection connection = mock(Connection.class);
            when(connection.prepareStatement(anyString())).thenAnswer(prepare -> statement(prepare.getArgument(0)));
            return connection;
        });
        this.service = new SpecialItemClaimService(
                this.dataSource, id -> id == 404 ? null : mock(Item.class), (userId, itemId) -> {
                    this.created.add(itemId);
                    return this.creates ? this.item : null;
                });
        this.offers.put(DRAGON, 100);
        this.choices.put(DRAGON + "#0", 100);
        this.choices.put(DRAGON + "#1", 101);
    }

    /** A statement over the three tables, enough for the queries the service runs. */
    private PreparedStatement statement(String sql) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        Map<Integer, Object> params = new HashMap<>();
        org.mockito.Mockito.doAnswer(call -> params.put(call.getArgument(0), call.getArgument(1)))
                .when(statement)
                .setString(org.mockito.ArgumentMatchers.anyInt(), anyString());
        org.mockito.Mockito.doAnswer(call -> params.put(call.getArgument(0), call.getArgument(1)))
                .when(statement)
                .setInt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        when(statement.executeQuery()).thenAnswer(call -> {
            Integer found;
            if (sql.contains("special_items_free_claim_choices") && sql.contains("choice = ?")) {
                found = this.choices.get(params.get(1) + "#" + params.get(2));
            } else if (sql.contains("special_items_free_claim_choices")) {
                found = this.choices.keySet().stream().anyMatch(key -> key.startsWith(params.get(1) + "#")) ? 1 : null;
            } else if (sql.contains("special_items_free_claims")) {
                found = this.offers.get(params.get(1));
            } else {
                found = this.claims.contains(params.get(1) + "#" + params.get(2)) ? 1 : null;
            }
            ResultSet set = mock(ResultSet.class);
            when(set.next()).thenReturn(found != null);
            when(set.getInt("item_id")).thenReturn(found == null ? 0 : found);
            String petKey = params.get(1) + "#" + params.get(2);
            when(set.getInt("pet_type")).thenReturn(this.pets.containsKey(petKey) ? 37 : -1);
            when(set.getInt("pet_breed")).thenReturn(this.pets.getOrDefault(petKey, 0));
            when(set.getString("pet_name")).thenReturn(this.pets.containsKey(petKey) ? " Blue Dragon " : "");
            when(set.getString("badge_code")).thenReturn(this.badges.getOrDefault(petKey, ""));
            when(set.getBoolean("enabled")).thenReturn(!this.disabled.contains(String.valueOf(params.get(1))));
            return set;
        });
        when(statement.executeUpdate()).thenAnswer(call -> {
            String key = params.get(1) + "#" + params.get(2);
            if (sql.startsWith("DELETE")) return this.claims.remove(key) ? 1 : 0;
            if (!this.claims.add(key)) throw new java.sql.SQLIntegrityConstraintViolationException("duplicate");
            return 1;
        });
        return statement;
    }

    @Test
    void givesTheChosenFurniOnce() throws Exception {
        SpecialItemClaimService.ChoiceResult first = this.service.claim(7, DRAGON, 1, 1000);
        assertEquals(SpecialItemClaimService.Outcome.CLAIMED, first.outcome());
        assertSame(this.item, first.item());
        assertEquals(List.of(101), this.created);

        SpecialItemClaimService.ChoiceResult second = this.service.claim(7, DRAGON, 0, 1001);
        assertEquals(SpecialItemClaimService.Outcome.ALREADY_CLAIMED, second.outcome());
        assertNull(second.item());
        assertEquals(List.of(101), this.created, "the other dragon is not given as well");
    }

    @Test
    void refusesAChoiceTheClaimDoesNotHaveAndLeavesItOpen() throws Exception {
        assertEquals(
                SpecialItemClaimService.Outcome.INVALID_CHOICE,
                this.service.claim(7, DRAGON, 2, 1000).outcome());
        assertEquals(
                SpecialItemClaimService.Outcome.INVALID_CHOICE,
                this.service
                        .claim(7, DRAGON, SpecialItemClaimService.NO_CHOICE, 1000)
                        .outcome());
        assertEquals(
                SpecialItemClaimService.Outcome.CLAIMED,
                this.service.claim(7, DRAGON, 0, 1000).outcome());
    }

    @Test
    void aClaimWithoutChoicesKeepsGivingItsOwnFurni() throws Exception {
        this.offers.put("chests_starter", 55);

        assertEquals(
                SpecialItemClaimService.Outcome.CLAIMED,
                this.service
                        .claim(7, "chests_starter", SpecialItemClaimService.NO_CHOICE, 1000)
                        .outcome());
        assertEquals(List.of(55), this.created);
    }

    @Test
    void tellsUnavailableFromFailed() throws Exception {
        assertEquals(
                SpecialItemClaimService.Outcome.UNAVAILABLE,
                this.service.claim(7, "not_offered", 0, 1000).outcome());

        this.choices.put(DRAGON + "#1", 404);
        assertEquals(
                SpecialItemClaimService.Outcome.UNAVAILABLE,
                this.service.claim(7, DRAGON, 1, 1000).outcome());

        this.creates = false;
        assertEquals(
                SpecialItemClaimService.Outcome.FAILED,
                this.service.claim(7, DRAGON, 0, 1000).outcome());
        this.creates = true;
        assertEquals(
                SpecialItemClaimService.Outcome.CLAIMED,
                this.service.claim(7, DRAGON, 0, 1000).outcome(),
                "a failed claim can be tried again");
    }

    @Test
    void theChoicesAloneOfferAClaim() throws Exception {
        this.offers.remove(DRAGON);

        assertEquals(false, this.service.claimedOrUnavailable(7, DRAGON));
        assertEquals(
                SpecialItemClaimService.Outcome.CLAIMED,
                this.service.claim(7, DRAGON, 0, 1000).outcome());
        assertEquals(List.of(100), this.created);
        assertEquals(true, this.service.claimedOrUnavailable(7, DRAGON));
    }

    @Test
    void aDisabledRowSwitchesTheClaimOffWhateverItsChoices() throws Exception {
        this.disabled.add(DRAGON);

        assertEquals(true, this.service.claimedOrUnavailable(8, DRAGON));
        assertEquals(
                SpecialItemClaimService.Outcome.UNAVAILABLE,
                this.service.claim(8, DRAGON, 0, 1000).outcome());
    }

    @Test
    void aRowWithoutAFurniAndWithoutChoicesOffersNothing() throws Exception {
        this.offers.put("empty", 0);

        assertEquals(true, this.service.claimedOrUnavailable(7, "empty"));
    }

    @Test
    void aChoiceMayGiveAPetBesidesItsFurni() throws Exception {
        this.pets.put(DRAGON + "#1", 1);

        SpecialItemClaimService.ChoiceResult result = this.service.claim(7, DRAGON, 1, 1000);

        assertEquals(SpecialItemClaimService.Outcome.CLAIMED, result.outcome());
        assertSame(this.item, result.item());
        assertEquals(new SpecialItemClaimService.PetReward(37, 1, "Blue Dragon"), result.pet());
        assertNull(this.service.claim(8, DRAGON, 0, 1000).pet(), "a choice without a pet gives none");
    }

    @Test
    void aChoiceMayGiveOnlyAPet() throws Exception {
        this.choices.put(DRAGON + "#0", 0);
        this.pets.put(DRAGON + "#0", 0);

        SpecialItemClaimService.ChoiceResult result = this.service.claim(7, DRAGON, 0, 1000);

        assertEquals(SpecialItemClaimService.Outcome.CLAIMED, result.outcome());
        assertNull(result.item());
        assertEquals(37, result.pet().type());
        assertEquals(List.of(), this.created);
        assertNull(this.service.claim(7, DRAGON, 0, 1001).pet(), "and gives it once");
    }

    @Test
    void aPetWithoutANameGetsTheDefaultOne() {
        assertEquals("Pet", new SpecialItemClaimService.PetReward(37, 0, " ").name());
        assertEquals("Pet", new SpecialItemClaimService.PetReward(37, 0, null).name());
    }

    @Test
    void aChoiceMayGiveABadge() throws Exception {
        this.badges.put(DRAGON + "#0", " PET17 ");
        this.badges.put(DRAGON + "#1", "PET18");

        assertEquals("PET17", this.service.claim(7, DRAGON, 0, 1000).badge());
        assertEquals("PET18", this.service.claim(8, DRAGON, 1, 1000).badge());
        assertEquals("", this.service.claim(7, DRAGON, 1, 1001).badge(), "a refused claim gives no badge");
    }

    @Test
    void aChoiceMayGiveOnlyABadge() throws Exception {
        this.choices.put(DRAGON + "#0", 0);
        this.badges.put(DRAGON + "#0", "PET17");

        SpecialItemClaimService.ChoiceResult result = this.service.claim(7, DRAGON, 0, 1000);

        assertEquals(SpecialItemClaimService.Outcome.CLAIMED, result.outcome());
        assertNull(result.item());
        assertNull(result.pet());
        assertEquals("PET17", result.badge());
    }
}
