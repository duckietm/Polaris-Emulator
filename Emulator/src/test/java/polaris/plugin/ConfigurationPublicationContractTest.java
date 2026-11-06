package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.UserManager;
import polaris.hotel.wired.core.WiredEngine;
import polaris.messages.PacketManager;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;

class ConfigurationPublicationContractTest {

    @Test
    void crossThreadConfigurationTargetsAreVolatile() throws Exception {
        List<Field> fields = List.of(
                Room.class.getField("USER_CHAT_DELAY"),
                RoomChatMessage.class.getField("BANNED_BUBBLES"),
                RoomLayout.class.getField("MAXIMUM_STEP_HEIGHT"),
                RoomManager.class.getField("SHOW_PUBLIC_IN_POPULAR_TAB"),
                WiredEngine.class.getField("MAX_RECURSION_DEPTH"),
                PacketManager.class.getField("DEBUG_SHOW_PACKETS"),
                CatalogManager.class.getField("PURCHASE_COOLDOWN"),
                UserManager.class.getField("WELCOME_MESSAGE"),
                DiscountComposer.class.getField("ADDITIONAL_DISCOUNT_THRESHOLDS"),
                GiftConfigurationComposer.class.getField("BOX_TYPES"),
                Bot.class.getField("PLACEMENT_MESSAGES"));

        for (Field field : fields) {
            assertTrue(
                    Modifier.isVolatile(field.getModifiers()),
                    () -> field.getDeclaringClass().getSimpleName()
                            + "."
                            + field.getName()
                            + " must publish reloads through a volatile field");
        }
    }
}
