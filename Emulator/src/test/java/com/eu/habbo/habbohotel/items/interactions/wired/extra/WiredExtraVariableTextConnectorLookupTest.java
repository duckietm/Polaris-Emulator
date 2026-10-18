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

    @Test
    void onlyNumberEqualsTextLinesAreKept() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn("1=x\nbad\n 3 = z \nx=1\n4,comma\n-1=neg\n5=\n7=a\n7=b");
        WiredExtraVariableTextConnector connector =
                new WiredExtraVariableTextConnector(1, 1, mock(Item.class), "", 0, 0);

        connector.loadWiredData(set, null);

        assertEquals(java.util.Map.of(1, "x", -1, "neg", 5, "", 7, "b"), connector.getMappings());
    }

    @Test
    void theStoredTextIsTheValidLinesInNumberOrder() {
        assertEquals(
                "-2=neg\n3=b\n9=a\n20=c", WiredExtraVariableTextConnector.storedMappingsText("9=a\n3=b\n-2=neg\n20=c"));
        assertEquals(
                "7=again\n9=\n10=a=b\n_=other",
                WiredExtraVariableTextConnector.storedMappingsText(
                        "Seven=7\n_=other\n7=seven\n 8=x\n9=\n10=a=b\n7=again"));
        assertEquals("", WiredExtraVariableTextConnector.storedMappingsText(""));
    }
}
