package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;

/**
 * The paid-out flags used to be written one by one before the credit, so a crash or a refused credit in between left
 * the seller's offers paid out with nothing paid. They are written with the credit now, or not at all.
 */
class MarketPlacePayoutTransactionTest {

    private final Connection connection = mock(Connection.class);
    private final PreparedStatement claim = mock(PreparedStatement.class);

    @BeforeEach
    void setUp() throws SQLException {
        when(this.connection.prepareStatement(anyString())).thenReturn(this.claim);
    }

    @Test
    void theOffersAreFlaggedAndTheSellerCreditedTogether() throws Exception {
        when(this.claim.executeUpdate()).thenReturn(1);
        EconomyMutationResult result = mock(EconomyMutationResult.class);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            ledger.when(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)))
                    .thenReturn(result);

            assertNotNull(MarketPlacePayoutTransaction.commit(this.connection, 7, List.of(11, 12), -1, 300));

            ledger.verify(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)));
        }

        verify(this.connection).setAutoCommit(false);
        verify(this.connection).commit();
        verify(this.connection, never()).rollback();
    }

    @Test
    void anOfferAnotherSessionTookFirstPaysNothing() throws Exception {
        // The second offer was already paid out: neither the first flag nor the credit may stay.
        when(this.claim.executeUpdate()).thenReturn(1).thenReturn(0);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {

            assertNull(MarketPlacePayoutTransaction.commit(this.connection, 7, List.of(11, 12), -1, 300));

            ledger.verify(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)), never());
        }

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aRefusedCreditTakesTheFlagsBack() throws Exception {
        when(this.claim.executeUpdate()).thenReturn(1);

        try (MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class)) {
            ledger.when(() -> EconomyLedger.apply(any(Connection.class), any(EconomyOperation.class)))
                    .thenThrow(new SQLException("ledger refused"));

            assertNull(MarketPlacePayoutTransaction.commit(this.connection, 7, List.of(11), -1, 300));
        }

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }
}
