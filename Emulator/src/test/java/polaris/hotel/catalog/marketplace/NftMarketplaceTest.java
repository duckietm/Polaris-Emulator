package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;
import polaris.plugin.PluginManager;
import polaris.plugin.events.users.UserPointsEvent;

/** Offering and buying collectibles: what is refused, and that a sale goes through the one sale transaction. */
class NftMarketplaceTest {

    private static final int EMERALDS = 1001;
    private static final int NOW = 1_700_000_000;

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet result = mock(ResultSet.class);
    private final ItemManager items = mock(ItemManager.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final GameClient client = mock(GameClient.class);
    private final User buyer = mock(User.class, RETURNS_DEEP_STUBS);
    private final List<Runnable> async = new ArrayList<>();
    private NftMarketplaceSettings settings = new NftMarketplaceSettings(true, 1, 1000, 60, 2, 5, 0, EMERALDS);
    private boolean previousMarketplace;

    @BeforeEach
    void setUp() throws Exception {
        this.previousMarketplace = MarketPlace.MARKETPLACE_ENABLED;
        MarketPlace.MARKETPLACE_ENABLED = true;
        NftMarketplaceRuntime.install(new NftMarketplaceRuntime(
                this.dataSource,
                () -> this.settings,
                this.items,
                id -> null,
                this.plugins,
                this.async::add,
                () -> NOW));

        when(this.dataSource.getConnection()).thenReturn(this.connection);
        when(this.connection.prepareStatement(anyString())).thenReturn(this.statement);
        when(this.statement.executeQuery()).thenReturn(this.result);
        when(this.plugins.fireEvent(any())).thenAnswer(call -> call.getArgument(0));
        when(this.client.getUser()).thenReturn(this.buyer);
        when(this.buyer.getUserInfo().getId()).thenReturn(7);
    }

    @AfterEach
    void tearDown() {
        NftMarketplaceRuntime.uninstall();
        MarketPlace.MARKETPLACE_ENABLED = this.previousMarketplace;
    }

    private UserItem inventoryItem(String className, FurnitureType type) {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn(className);
        when(base.getType()).thenReturn(type);
        UserItem item = mock(UserItem.class);
        when(item.getBaseItem()).thenReturn(base);
        when(this.buyer.getInventory().getItemsComponent().getUserItem(900)).thenReturn(item);
        return item;
    }

    private void cheapestOffer(int offerId, int sellerId, int price) throws Exception {
        // The cheapest offer, then its item row.
        when(this.result.next()).thenReturn(true, true, false);
        when(this.result.getInt("id")).thenReturn(offerId);
        when(this.result.getInt("item_id")).thenReturn(900);
        when(this.result.getInt("user_id")).thenReturn(sellerId);
        when(this.result.getInt("price")).thenReturn(price);
    }

    @Test
    void isOffWithoutTheSettingOrBeforeStartUp() {
        this.settings = new NftMarketplaceSettings(false, 1, 1000, 60, 2, 5, 0, EMERALDS);
        assertEquals(
                MarketplaceItemPostedComposer.MARKETPLACE_DISABLED, NftMarketplace.makeOffer(this.client, 1, 900, 10));
        assertEquals(
                NftMarketplace.RESULT_DISABLED,
                NftMarketplace.buy(this.client, "nft_bunny", 10).result());

        NftMarketplaceRuntime.uninstall();
        assertEquals(
                NftMarketplace.RESULT_DISABLED,
                NftMarketplace.buy(this.client, "nft_bunny", 10).result());
    }

    @Test
    void onlyOffersAnNftFurniOfTheRightKindForAPriceInRange() throws Exception {
        inventoryItem("rare_dragonlamp", FurnitureType.FLOOR);
        assertEquals(
                MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR,
                NftMarketplace.makeOffer(this.client, 1, 900, 10));

        inventoryItem("nft_bunny", FurnitureType.FLOOR);
        assertEquals(
                MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR,
                NftMarketplace.makeOffer(this.client, 2, 900, 10));
        assertEquals(
                MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR,
                NftMarketplace.makeOffer(this.client, 1, 900, 1001));
        assertEquals(
                MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR, NftMarketplace.makeOffer(this.client, 1, 900, 0));
    }

    @Test
    void refusesAnOfferOverTheOpenOfferLimit() throws Exception {
        inventoryItem("nft_bunny", FurnitureType.FLOOR);
        when(this.result.next()).thenReturn(true);
        when(this.result.getInt(1)).thenReturn(2);

        assertEquals(
                MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR,
                NftMarketplace.makeOffer(this.client, 1, 900, 10));
    }

    @Test
    void answersSoldOutWhenNoOfferIsOpen() throws Exception {
        when(this.result.next()).thenReturn(false);

        assertEquals(
                NftMarketplace.RESULT_SOLD_OUT,
                NftMarketplace.buy(this.client, "nft_bunny", 10).result());
    }

    @Test
    void tellsTheBuyerWhenTheOnlyOpenOfferIsTheirOwn() throws Exception {
        // No offer of someone else, then the buyer's own offer.
        when(this.result.next()).thenReturn(false, true);

        assertEquals(
                NftMarketplace.RESULT_OWN_OFFER,
                NftMarketplace.buy(this.client, "nft_bunny", 10).result());
    }

    @Test
    void neverPaysMoreThanTheBuyerSaw() throws Exception {
        cheapestOffer(41, 8, 120);

        NftMarketplace.BuyResult answer = NftMarketplace.buy(this.client, "nft_bunny", 100);

        assertEquals(NftMarketplace.RESULT_PRICE_CHANGED, answer.result());
        assertEquals(41, answer.offerId());
        assertEquals(120, answer.price());
    }

    @Test
    void refusesABuyerWithoutTheEmeralds() throws Exception {
        cheapestOffer(41, 8, 100);
        when(this.buyer.getUserInfo().getCurrencyAmount(EMERALDS)).thenReturn(99);

        assertEquals(
                NftMarketplace.RESULT_NOT_ENOUGH_EMERALDS,
                NftMarketplace.buy(this.client, "nft_bunny", 100).result());
    }

    @Test
    void aPluginCannotChangeTheEmeraldCharge() throws Exception {
        cheapestOffer(41, 8, 100);
        when(this.buyer.getUserInfo().getCurrencyAmount(EMERALDS)).thenReturn(500);
        when(this.items.loadUserItem(any(ResultSet.class))).thenReturn(mock(UserItem.class));
        when(this.plugins.fireEvent(any())).thenAnswer(call -> {
            Object event = call.getArgument(0);
            if (event instanceof UserPointsEvent points) points.points = -1;
            return event;
        });

        assertEquals(
                NftMarketplace.RESULT_FAILED,
                NftMarketplace.buy(this.client, "nft_bunny", 100).result());
    }

    @Test
    void aSaleChargesThePriceAndPaysThePriceMinusTheFee() throws Exception {
        cheapestOffer(41, 8, 100);
        when(this.buyer.getUserInfo().getCurrencyAmount(EMERALDS)).thenReturn(500);
        UserItem item = mock(UserItem.class);
        when(this.items.loadUserItem(any(ResultSet.class))).thenReturn(item);

        List<NftMarketplaceSaleTransaction.Sale> sales = new ArrayList<>();
        try (MockedStatic<NftMarketplaceSaleTransaction> transaction = mockStatic(NftMarketplaceSaleTransaction.class);
                MockedStatic<LedgerWalletMutation> wallet = mockStatic(LedgerWalletMutation.class)) {
            transaction
                    .when(() -> NftMarketplaceSaleTransaction.commit(any(DataSource.class), any()))
                    .thenAnswer(call -> {
                        sales.add(call.getArgument(1));
                        return new NftMarketplaceSaleTransaction.Result(
                                mock(polaris.hotel.economy.EconomyMutationResult.class), null);
                    });
            wallet.when(() -> LedgerWalletMutation.coordinated(any(User.class), any()))
                    .thenAnswer(call -> call.<LedgerWalletMutation.CoordinatedWork<?, ?>>getArgument(1)
                            .execute());

            NftMarketplace.BuyResult answer = NftMarketplace.buy(this.client, "nft_bunny", 100);

            assertEquals(NftMarketplace.RESULT_SUCCESS, answer.result());
            assertEquals(41, answer.offerId());
        }

        assertEquals(1, sales.size());
        NftMarketplaceSaleTransaction.Sale sale = sales.getFirst();
        assertEquals(41, sale.offerId());
        assertEquals(7, sale.buyerId());
        assertEquals(8, sale.sellerId());
        assertEquals(EMERALDS, sale.currencyType());
        assertEquals(100, sale.price());
        assertEquals(95, sale.payout());
        assertEquals(NOW, sale.soldTimestamp());
        verify(item).setUserId(7);
        assertEquals(1, this.async.size());
    }

    @Test
    void theBuyerIsNeverTheirOwnSeller() throws Exception {
        when(this.result.next()).thenReturn(false);

        NftMarketplace.buy(this.client, "nft_bunny", 100);

        verify(this.statement, atLeastOnce()).setInt(3, 7);
        verify(this.items, never()).loadUserItem(any(ResultSet.class));
    }
}
