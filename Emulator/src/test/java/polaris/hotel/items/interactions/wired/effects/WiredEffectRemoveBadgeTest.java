package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/**
 * "Remove badge" deletes a badge row from the database for every user it resolves. Taking a badge
 * away is the mirror of granting one, so it sits behind the same reward policy as "give badge".
 */
class WiredEffectRemoveBadgeTest {

    @Test
    void savingWithoutTheRewardPermissionIsRefused() throws Exception {
        WiredEffectRemoveBadge box = new WiredEffectRemoveBadge(1, 1, mock(Item.class), "", 0, 0);
        String before = box.getWiredData();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            ConfigurationManager config = requiringPermission();
            emulator.when(Emulator::getConfig).thenReturn(config);

            assertFalse(box.saveData(new WiredSettings(new int[] {0}, "ADM", new int[0], 0), null));
        }

        assertEquals(before, box.getWiredData());
    }

    @Test
    void aClientHoldingThePermissionStillSaves() throws Exception {
        WiredEffectRemoveBadge box = new WiredEffectRemoveBadge(1, 1, mock(Item.class), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            ConfigurationManager config = requiringPermission();
            emulator.when(Emulator::getConfig).thenReturn(config);

            assertTrue(box.saveData(new WiredSettings(new int[] {0}, "ADM", new int[0], 0), superWired()));
        }
    }

    private static ConfigurationManager requiringPermission() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean("hotel.wired.reward.require_permission", true)).thenReturn(true);
        return config;
    }

    private static GameClient superWired() {
        User user = mock(User.class);
        when(user.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);
        return client;
    }
}
