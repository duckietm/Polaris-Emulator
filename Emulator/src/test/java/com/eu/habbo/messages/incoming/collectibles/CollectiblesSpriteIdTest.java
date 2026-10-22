package com.eu.habbo.messages.incoming.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.collectibles.CollectibleData;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Mintable;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.collectibles.CollectiblesRuntime;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintablesComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleShopOffersComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * In a hotel where {@code items_base.id} and {@code sprite_id} differ, the shop and relic packets name the
 * furni by sprite id (what the client's furnidata and inventory use), while the tables keep the id.
 */
class CollectiblesSpriteIdTest {
    private static final int CHAIR_ID = 1_100_009_244;
    private static final int CHAIR_SPRITE = 18_570;
    private static final int LAMP_ID = 407;
    private static final int LAMP_SPRITE = 1_619;

    private static final Product CHAIR = new Product(
            "nft_h26_witchchair",
            CollectibleData.TYPE_FLOOR,
            Integer.toString(CHAIR_ID),
            10,
            "common",
            "",
            List.of(),
            null,
            700,
            true,
            -1,
            0);

    private CollectiblesService service;

    @BeforeEach
    void install() {
        this.service = mock(CollectiblesService.class);
        ItemManager items = mock(ItemManager.class);
        Item chair = furni(CHAIR_ID, CHAIR_SPRITE);
        Item lamp = furni(LAMP_ID, LAMP_SPRITE);
        when(items.getItem(CHAIR_ID)).thenReturn(chair);
        when(items.getItem(LAMP_ID)).thenReturn(lamp);
        CollectiblesSettings on = new CollectiblesSettings(true, 1001, 1000, true, 1000, 10, true, true);
        CollectiblesRuntime.install(new CollectiblesRuntime(this.service, () -> on, items, id -> null, () -> 1_000));
    }

    @AfterEach
    void uninstall() {
        CollectiblesRuntime.uninstall();
    }

    @Test
    void theShopSendsTheWitchChairAsSprite18570() throws Exception {
        when(this.service.shopOffers()).thenReturn(List.of(CHAIR));

        ByteBuf packet = sent(new GetCollectibleShopEvent(), CollectibleShopOffersComposer.class);
        try {
            packet.skipBytes(4);
            assertEquals(3495, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals("nft_h26_witchchair", string(packet));
            assertEquals(700, packet.readInt());
            packet.skipBytes(2 + 4 + 4);
            assertEquals(CollectibleData.TYPE_FLOOR, packet.readShort());
            assertEquals("18570", string(packet));
        } finally {
            packet.release();
        }
        assertEquals(Integer.toString(CHAIR_ID), CHAIR.itemTypeId());
    }

    @Test
    void relicsSendTheFurniSpriteIdTheInventoryMatchesOn() throws Exception {
        when(this.service.mintables()).thenReturn(List.of(new Mintable(LAMP_ID, "nft_rare_dragonlamp.4", 4, 0, 0)));

        ByteBuf packet = sent(new GetMintableItemsEvent(), CollectibleMintablesComposer.class);
        try {
            packet.skipBytes(4);
            assertEquals(9423, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals(LAMP_SPRITE, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readInt());
            assertFalse(packet.readBoolean());
            assertEquals(4, packet.readInt());
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readShort());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    private static Item furni(int id, int sprite) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getSpriteId()).thenReturn(sprite);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }

    private static ByteBuf sent(MessageHandler handler, Class<? extends MessageComposer> type) throws Exception {
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(mock(Habbo.class));
        handler.client = client;
        ByteBuf buffer = Unpooled.buffer();
        handler.packet = new ClientMessage(0, buffer);
        try {
            handler.handle();
        } finally {
            buffer.release();
        }
        ArgumentCaptor<MessageComposer> composer = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(composer.capture());
        return assertInstanceOf(type, composer.getValue()).compose().get();
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
