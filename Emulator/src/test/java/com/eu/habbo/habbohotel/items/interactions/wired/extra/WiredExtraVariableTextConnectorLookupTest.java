package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredExtraVariableTextConnectorLookupTest {

    @Test
    void theLastCaseInsensitiveMatchWins() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn("1=abc\n2=ABC");
        WiredExtraVariableTextConnector connector =
                new WiredExtraVariableTextConnector(1, 1, mock(Item.class), "", 0, 0);

        connector.loadWiredData(set, null);

        assertEquals(2L, connector.resolveLongValue("abc"));
        assertEquals(2L, connector.resolveLongValue("Abc"));
    }
}
