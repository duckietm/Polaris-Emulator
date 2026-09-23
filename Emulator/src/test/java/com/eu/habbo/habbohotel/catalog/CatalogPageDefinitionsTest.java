package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.layouts.Default_3x3Layout;
import com.eu.habbo.habbohotel.catalog.layouts.InfoMonkeyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.InfoNikoLayout;
import com.eu.habbo.habbohotel.catalog.layouts.ProductPage1Layout;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * The catalog page layout registry: which page class each layout code resolves to, and the
 * layout code each page writes on the wire so the client picks the matching renderer.
 */
class CatalogPageDefinitionsTest {

    @Test
    void nikoAndMonkeyResolveToTheirOwnPageClasses() {
        assertAll(
                () -> assertEquals(InfoNikoLayout.class, CatalogManager.pageDefinitions.get("niko")),
                () -> assertEquals(InfoMonkeyLayout.class, CatalogManager.pageDefinitions.get("monkey")));
    }

    @Test
    void aliasesOfTheDefaultPageResolveToTheDefaultLayout() {
        assertAll(
                () -> assertEquals(Default_3x3Layout.class, CatalogManager.pageDefinitions.get("default_3x3")),
                () -> assertEquals(Default_3x3Layout.class, CatalogManager.pageDefinitions.get("plasto")),
                () -> assertEquals(Default_3x3Layout.class, CatalogManager.pageDefinitions.get("root")),
                () -> assertEquals(Default_3x3Layout.class, CatalogManager.pageDefinitions.get("collectibles")));
    }

    @Test
    void productPage1ResolvesToItsOwnPageClass() {
        assertEquals(ProductPage1Layout.class, CatalogManager.pageDefinitions.get("productpage1"));
    }

    @Test
    void retiredLayoutStaysUnregistered() {
        assertFalse(CatalogManager.pageDefinitions.containsKey("custom_prefix"));
    }

    @Test
    void nikoWritesItsOwnLayoutCodeWithThreeImagesAndThreeTexts() throws SQLException {
        ByteBuf packet = serialize(new InfoNikoLayout(pageRow("niko")));
        try {
            assertEquals("niko", string(packet));
            assertEquals(3, packet.readInt());
            assertEquals("headline.png", string(packet));
            assertEquals("teaser.png", string(packet));
            assertEquals("special.png", string(packet));
            assertEquals(3, packet.readInt());
            assertEquals("text one", string(packet));
            assertEquals("text details", string(packet));
            assertEquals("text teaser", string(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void productPage1WritesItsLayoutCodeWithTwoImagesAndFourTexts() throws SQLException {
        ByteBuf packet = serialize(new ProductPage1Layout(pageRow("productpage1")));
        try {
            assertEquals("productpage1", string(packet));
            assertEquals(2, packet.readInt());
            assertEquals("headline.png", string(packet));
            assertEquals("teaser.png", string(packet));
            assertEquals(4, packet.readInt());
            assertEquals("text one", string(packet));
            assertEquals("text two", string(packet));
            assertEquals("text details", string(packet));
            assertEquals("text teaser", string(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    /** A {@code catalog_pages} row as a mock {@link ResultSet}, the same path the production loader uses. */
    private static ResultSet pageRow(String layout) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(7);
        when(set.getInt("parent_id")).thenReturn(1);
        when(set.getInt("min_rank")).thenReturn(1);
        when(set.getString("caption")).thenReturn("Caption");
        when(set.getString("caption_save")).thenReturn("caption_save");
        when(set.getInt("icon_color")).thenReturn(1);
        when(set.getInt("icon_image")).thenReturn(2);
        when(set.getInt("order_num")).thenReturn(3);
        when(set.getBoolean("visible")).thenReturn(true);
        when(set.getBoolean("enabled")).thenReturn(true);
        when(set.getBoolean("club_only")).thenReturn(false);
        when(set.getBoolean("vip_only")).thenReturn(false);
        when(set.getInt("room_id")).thenReturn(0);
        when(set.getString("catalog_mode")).thenReturn("NORMAL");
        when(set.getString("page_layout")).thenReturn(layout);
        when(set.getString("page_headline")).thenReturn("headline.png");
        when(set.getString("page_teaser")).thenReturn("teaser.png");
        when(set.getString("page_special")).thenReturn("special.png");
        when(set.getString("page_text1")).thenReturn("text one");
        when(set.getString("page_text2")).thenReturn("text two");
        when(set.getString("page_text_details")).thenReturn("text details");
        when(set.getString("page_text_teaser")).thenReturn("text teaser");
        when(set.getString("includes")).thenReturn("");
        return set;
    }

    /** Serialises the page body and positions the reader after the length and header prefix. */
    private static ByteBuf serialize(CatalogPage page) {
        ServerMessage message = new ServerMessage(1);
        page.serialize(message);
        ByteBuf packet = message.get();
        packet.skipBytes(6);
        return packet;
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
