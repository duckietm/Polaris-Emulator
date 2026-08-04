package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CatalogAdminPageDetailsComposerTest {
    @Test
    void serializesAuthoritativeEditablePageFieldsInProtocolOrder() {
        CatalogPage page = mock(CatalogPage.class);
        when(page.getId()).thenReturn(42);
        when(page.getCaption()).thenReturn("Guild shop");
        when(page.getPageName()).thenReturn("guild_shop");
        when(page.getParentId()).thenReturn(7);
        when(page.getCatalogPageType()).thenReturn(CatalogPageType.BOTH);
        when(page.getLayout()).thenReturn("guild_furni");
        when(page.getIconColor()).thenReturn(3);
        when(page.getIconImage()).thenReturn(145);
        when(page.getRank()).thenReturn(5);
        when(page.getOrderNum()).thenReturn(9);
        when(page.isVisible()).thenReturn(true);
        when(page.isEnabled()).thenReturn(false);
        when(page.isClubOnly()).thenReturn(true);
        when(page.isVipOnly()).thenReturn(false);
        when(page.getHeaderImage()).thenReturn("headline");
        when(page.getTeaserImage()).thenReturn("teaser");
        when(page.getSpecialImage()).thenReturn("special");
        when(page.getTextOne()).thenReturn("text one");
        when(page.getTextTwo()).thenReturn("text two");
        when(page.getTextDetails()).thenReturn("details");
        when(page.getTextTeaser()).thenReturn("teaser text");
        when(page.getRoomId()).thenReturn(123);
        when(page.getIncluded()).thenReturn(new ArrayList<>(java.util.List.of(1, 2, 3)));

        ByteBuf payload = new CatalogAdminPageDetailsComposer(page).compose().get();
        payload.skipBytes(6);

        assertEquals(42, payload.readInt());
        assertEquals("Guild shop", readString(payload));
        assertEquals("guild_shop", readString(payload));
        assertEquals(7, payload.readInt());
        assertEquals("BOTH", readString(payload));
        assertEquals("guild_furni", readString(payload));
        assertEquals(3, payload.readInt());
        assertEquals(145, payload.readInt());
        assertEquals(5, payload.readInt());
        assertEquals(9, payload.readInt());
        assertTrue(payload.readBoolean());
        assertFalse(payload.readBoolean());
        assertTrue(payload.readBoolean());
        assertFalse(payload.readBoolean());
        assertEquals("headline", readString(payload));
        assertEquals("teaser", readString(payload));
        assertEquals("special", readString(payload));
        assertEquals("text one", readString(payload));
        assertEquals("text two", readString(payload));
        assertEquals("details", readString(payload));
        assertEquals("teaser text", readString(payload));
        assertEquals(123, payload.readInt());
        assertEquals("1;2;3", readString(payload));
        assertFalse(payload.isReadable());
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
