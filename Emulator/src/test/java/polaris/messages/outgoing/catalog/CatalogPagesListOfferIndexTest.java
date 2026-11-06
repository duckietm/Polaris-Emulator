package polaris.messages.outgoing.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.users.User;
import polaris.messages.outgoing.Outgoing;

class CatalogPagesListOfferIndexTest {

    @Test
    void indexPublishesDistinctPositiveOfferIdsInPageOrder() {
        User user = mock(User.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        CatalogManager catalogManager = mock(CatalogManager.class);
        CatalogPage page = mock(CatalogPage.class);

        when(environment.getCatalogManager()).thenReturn(catalogManager);
        when(catalogManager.getCatalogPages(-1, user, CatalogPageType.NORMAL)).thenReturn(List.of(page));
        when(catalogManager.getCatalogPages(42, user, CatalogPageType.NORMAL)).thenReturn(List.of());
        when(page.isVisible()).thenReturn(true);
        when(page.isEnabled()).thenReturn(true);
        when(page.getIconImage()).thenReturn(7);
        when(page.getId()).thenReturn(42);
        when(page.getParentId()).thenReturn(-1);
        when(page.getPageName()).thenReturn("chairs");
        when(page.getCaption()).thenReturn("Chairs");
        when(page.getOfferIds()).thenReturn(new IntArrayList(new int[] {100, 100, -5, 200, 0}));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            ByteBuf packet =
                    new CatalogPagesListComposer(user, "NORMAL").compose().get();
            assertEquals(packet.readableBytes() - Integer.BYTES, packet.readInt());
            assertEquals(Outgoing.CatalogPagesListComposer, packet.readUnsignedShort());

            skipNodeHeader(packet);
            assertEquals(0, packet.readInt());
            assertEquals(1, packet.readInt());

            skipNodeHeader(packet);
            assertEquals(2, packet.readInt());
            assertEquals(100, packet.readInt());
            assertEquals(200, packet.readInt());
            assertEquals(0, packet.readInt());

            assertFalse(packet.readBoolean());
            assertEquals("NORMAL", readString(packet));
            assertFalse(packet.isReadable());
        }
    }

    @Test
    void indexCapsOffersAtRendererProtocolLimitWithoutCorruptingSuffix() {
        User user = mock(User.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        CatalogManager catalogManager = mock(CatalogManager.class);
        CatalogPage page = mock(CatalogPage.class);
        int[] offers = new int[4005];
        for (int index = 0; index < offers.length; index++) offers[index] = index + 1;

        when(environment.getCatalogManager()).thenReturn(catalogManager);
        when(catalogManager.getCatalogPages(-1, user, CatalogPageType.NORMAL)).thenReturn(List.of(page));
        when(catalogManager.getCatalogPages(42, user, CatalogPageType.NORMAL)).thenReturn(List.of());
        when(page.isVisible()).thenReturn(true);
        when(page.isEnabled()).thenReturn(true);
        when(page.getIconImage()).thenReturn(7);
        when(page.getId()).thenReturn(42);
        when(page.getParentId()).thenReturn(-1);
        when(page.getPageName()).thenReturn("chairs");
        when(page.getCaption()).thenReturn("Chairs");
        when(page.getOfferIds()).thenReturn(new IntArrayList(offers));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            ByteBuf packet =
                    new CatalogPagesListComposer(user, "NORMAL").compose().get();
            packet.skipBytes(Integer.BYTES + Short.BYTES);

            skipNodeHeader(packet);
            assertEquals(0, packet.readInt());
            assertEquals(1, packet.readInt());

            skipNodeHeader(packet);
            assertEquals(4000, packet.readInt());
            for (int offerId = 1; offerId <= 4000; offerId++) assertEquals(offerId, packet.readInt());
            assertEquals(0, packet.readInt());

            assertFalse(packet.readBoolean());
            assertEquals("NORMAL", readString(packet));
            assertFalse(packet.isReadable());
        }
    }

    @Test
    void indexCarriesTheNewAdditionsFlag() {
        User user = mock(User.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        CatalogManager catalogManager = mock(CatalogManager.class);

        when(environment.getCatalogManager()).thenReturn(catalogManager);
        when(catalogManager.getCatalogPages(-1, user, CatalogPageType.NORMAL)).thenReturn(List.of());

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            ByteBuf packet =
                    new CatalogPagesListComposer(user, "NORMAL", true).compose().get();
            packet.skipBytes(Integer.BYTES + Short.BYTES);

            skipNodeHeader(packet);
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readInt());

            org.junit.jupiter.api.Assertions.assertTrue(packet.readBoolean(), "AIR newAdditionsAvailable");
            assertEquals("NORMAL", readString(packet));
            assertFalse(packet.isReadable());
        }
    }

    private static void skipNodeHeader(ByteBuf packet) {
        packet.readBoolean();
        packet.readInt();
        packet.readInt();
        packet.readInt();
        readString(packet);
        readString(packet);
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
