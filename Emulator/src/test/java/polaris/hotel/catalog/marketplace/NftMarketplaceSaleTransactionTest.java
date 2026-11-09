package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;

/** A collectible sale sells the offer, moves the furni, charges the buyer and pays the seller together, or not at all. */
class NftMarketplaceSaleTransactionTest {

    private static final NftMarketplaceSaleTransaction.Sale SALE =
            new NftMarketplaceSaleTransaction.Sale(41, 900, 7, 8, 1001, 100, 95, 1_700_000_000);

    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @BeforeEach
    void setUp() throws SQLException {
        when(this.connection.prepareStatement(anyString())).thenReturn(this.statement);
    }

    @Test
    void chargesTheBuyerAndPaysTheSellerInOneTransaction() throws Exception {
        when(this.statement.executeUpdate()).thenReturn(1);
        List<EconomyOperation> operations = new ArrayList<>();

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            ledger.when(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)))
                    .thenAnswer(call -> {
                        operations.add(call.getArgument(1));
                        return mock(EconomyMutationResult.class);
                    });

            NftMarketplaceSaleTransaction.Result result = NftMarketplaceSaleTransaction.commit(this.connection, SALE);

            assertNotNull(result);
            assertNotNull(result.seller());
        }

        assertEquals(2, operations.size());
        assertEquals(7, operations.get(0).userId());
        assertEquals(-100, operations.get(0).delta());
        assertEquals(1001, operations.get(0).currencyType());
        assertEquals(8, operations.get(1).userId());
        assertEquals(95, operations.get(1).delta());
        verify(this.connection).commit();
        verify(this.connection, never()).rollback();
    }

    @Test
    void anOfferSoldInTheMeantimeChangesNothing() throws Exception {
        when(this.statement.executeUpdate()).thenReturn(0);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            assertNull(NftMarketplaceSaleTransaction.commit(this.connection, SALE));

            ledger.verify(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)), never());
        }

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aBuyerWhoCannotPayTakesTheSaleBack() throws Exception {
        when(this.statement.executeUpdate()).thenReturn(1);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            ledger.when(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)))
                    .thenThrow(new IllegalArgumentException("economy mutation would produce an invalid balance"));

            assertNull(NftMarketplaceSaleTransaction.commit(this.connection, SALE));
        }

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aZeroPayoutChargesOnlyTheBuyer() throws Exception {
        when(this.statement.executeUpdate()).thenReturn(1);
        NftMarketplaceSaleTransaction.Sale sale =
                new NftMarketplaceSaleTransaction.Sale(41, 900, 7, 8, 1001, 1, 0, 1_700_000_000);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            ledger.when(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)))
                    .thenReturn(mock(EconomyMutationResult.class));

            NftMarketplaceSaleTransaction.Result result = NftMarketplaceSaleTransaction.commit(this.connection, sale);

            assertNotNull(result);
            assertNull(result.seller());
            ledger.verify(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)));
        }
    }

    @Test
    void onlySellsAnOpenCollectibleOfferAndMarksItPaidOut() {
        assertTrue(NftMarketplaceSaleTransaction.SELL_OFFER.contains("WHERE id = ? AND state = 1 AND nft = 1"));
        assertTrue(NftMarketplaceSaleTransaction.SELL_OFFER.contains("seller_claimed = 1"));
        assertTrue(NftMarketplaceSaleTransaction.TRANSFER_ITEM.contains("AND user_id = -1"));
    }

    @Test
    void refusesASaleToTheSeller() {
        assertNull(NftMarketplaceSaleTransaction.commit(
                mock(DataSource.class),
                new NftMarketplaceSaleTransaction.Sale(41, 900, 7, 7, 1001, 100, 95, 1_700_000_000)));
    }
}
