package com.eu.habbo.messages.incoming.camera;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.plugin.PluginManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

class CameraPurchaseEventTest {
    private static final int PHOTO_TIMESTAMP = 1_700_000_000;
    private static final int PHOTO_ROOM = 7;
    private static final String PHOTO_JSON = "{\"t\":" + PHOTO_TIMESTAMP + ",\"id\":\"%id%\"}";

    private final GameClient client = mock(GameClient.class);
    private final Habbo habbo = mock(Habbo.class);
    private final HabboInfo info = mock(HabboInfo.class);
    private final Room room = mock(Room.class);
    private final ItemManager itemManager = mock(ItemManager.class);
    private final Item item = mock(Item.class);
    private final HabboItem photoItem = mock(HabboItem.class);

    private MockedStatic<Emulator> emulator;

    @BeforeEach
    void setUp() {
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        HabboInventory inventory = mock(HabboInventory.class);
        when(habbo.getInventory()).thenReturn(inventory);
        when(inventory.getItemsComponent()).thenReturn(mock(ItemsComponent.class));
        when(habbo.tryTakeCredits(anyInt())).thenReturn(true);

        when(info.getId()).thenReturn(1);
        when(info.getUsername()).thenReturn("tester");
        when(info.getCredits()).thenReturn(100);
        when(info.getCurrencyAmount(anyInt())).thenReturn(100);
        when(info.getPhotoTimestamp()).thenReturn(PHOTO_TIMESTAMP);
        when(info.getPhotoJSON()).thenReturn(PHOTO_JSON);
        when(info.getPhotoURL()).thenReturn("1_" + PHOTO_TIMESTAMP + ".png");
        when(info.getPhotoRoomId()).thenReturn(PHOTO_ROOM);
        when(info.getCurrentRoom()).thenReturn(room);
        doCallRealMethod().when(info).claimPhotoPurchase(anyInt());
        doCallRealMethod().when(info).releasePhotoPurchase(anyInt());
        when(room.getId()).thenReturn(PHOTO_ROOM);

        ItemInteraction interaction = mock(ItemInteraction.class);
        when(interaction.getName()).thenReturn("external_image");
        when(item.getInteractionType()).thenReturn(interaction);
        when(itemManager.getItem(99)).thenReturn(item);
        when(itemManager.createItem(eq(1), eq(item), eq(0), eq(0), eq(PHOTO_JSON)))
                .thenReturn(photoItem);
        when(photoItem.getId()).thenReturn(55);
        when(photoItem.getExtradata()).thenReturn(PHOTO_JSON);

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(itemManager);
        when(environment.getAchievementManager()).thenReturn(mock(AchievementManager.class));

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("camera.item_id")).thenReturn(99);

        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenReturn("");
        when(texts.getValue(anyString(), anyString())).thenReturn("");

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(call -> call.getArgument(0));

        emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
    }

    @AfterEach
    void tearDown() {
        emulator.close();
    }

    private void purchase() {
        CameraPurchaseEvent event = new CameraPurchaseEvent();
        event.client = client;
        event.packet = packet("");
        event.handle();
    }

    @Test
    void aPhotoIsChargedBeforeItIsCreated() {
        purchase();

        InOrder order = inOrder(habbo, itemManager);
        order.verify(habbo).tryTakeCredits(CameraPurchaseEvent.CAMERA_PURCHASE_CREDITS);
        order.verify(itemManager).createItem(1, item, 0, 0, PHOTO_JSON);
    }

    @Test
    void aSecondPacketForTheSamePhotoBuysNothing() {
        purchase();
        purchase();

        verify(habbo, times(1)).tryTakeCredits(anyInt());
        verify(itemManager, times(1)).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void aPurchaseOutsideThePhotosRoomIsRefused() {
        when(room.getId()).thenReturn(PHOTO_ROOM + 1);

        purchase();

        verify(habbo, never()).tryTakeCredits(anyInt());
        verify(itemManager, never()).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void aUserWhoCannotAffordThePhotoKeepsItForLater() {
        when(info.getCredits()).thenReturn(0);
        purchase();
        verify(itemManager, never()).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());

        when(info.getCredits()).thenReturn(100);
        purchase();
        verify(itemManager, times(1)).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void aRefusedChargeCreatesNothingAndKeepsThePhoto() {
        when(habbo.tryTakeCredits(anyInt())).thenReturn(false);
        purchase();
        verify(itemManager, never()).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());

        when(habbo.tryTakeCredits(anyInt())).thenReturn(true);
        purchase();
        verify(itemManager, times(1)).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void aFailedCreationIsRefundedAndThePhotoCanBeBoughtAgain() {
        when(itemManager.createItem(anyInt(), any(), anyInt(), anyInt(), anyString()))
                .thenReturn(null)
                .thenReturn(photoItem);

        purchase();
        verify(habbo).giveCredits(CameraPurchaseEvent.CAMERA_PURCHASE_CREDITS);

        purchase();
        verify(habbo, times(2)).tryTakeCredits(anyInt());
        verify(itemManager, times(2)).createItem(anyInt(), any(), anyInt(), anyInt(), anyString());
    }

    private static ClientMessage packet(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
        return new ClientMessage(0, buffer);
    }
}
