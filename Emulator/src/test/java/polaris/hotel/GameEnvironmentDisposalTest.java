package polaris.hotel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import polaris.core.CreditsScheduler;
import polaris.core.GotwPointsScheduler;
import polaris.core.PixelScheduler;
import polaris.core.PointsScheduler;
import polaris.hotel.bots.BotManager;
import polaris.hotel.campaign.calendar.CalendarManager;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.crafting.CraftingManager;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.hotelview.HotelViewManager;
import polaris.hotel.items.ItemManager;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.translations.GoogleTranslateManager;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.subscriptions.SubscriptionManager;
import polaris.hotel.users.subscriptions.SubscriptionScheduler;

class GameEnvironmentDisposalTest {

    @Test
    void partialEnvironmentCanBeDisposedSafely() {
        assertDoesNotThrow(() -> new GameEnvironment().dispose());
    }

    @Test
    void failedAndMissingStepsDoNotPreventLaterDisposal() {
        AtomicBoolean laterStepRan = new AtomicBoolean();
        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put("missing", null);
        steps.put("failing", () -> {
            throw new IllegalStateException("expected");
        });
        steps.put("later", () -> laterStepRan.set(true));

        GameEnvironment.disposeAll(steps);

        assertTrue(laterStepRan.get());
    }

    @Test
    void roomsAreQuiescedBeforeTheRoomSavePassAndBotsAreDisposed() throws Exception {
        GameEnvironment environment = new GameEnvironment();
        polaris.hotel.rooms.RoomManager rooms = mock(polaris.hotel.rooms.RoomManager.class);
        polaris.hotel.bots.BotManager bots = mock(polaris.hotel.bots.BotManager.class);
        setField(environment, "roomManager", rooms);
        setField(environment, "botManager", bots);

        environment.dispose();

        var order = inOrder(rooms);
        order.verify(rooms).quiesceRoomCycles();
        order.verify(rooms).dispose();
        verify(bots).dispose();
    }

    @Test
    void disposesEveryResourceWithAnEstablishedLifecycleContract() throws Exception {
        GameEnvironment environment = new GameEnvironment();
        CreditsScheduler credits = mock(CreditsScheduler.class);
        PixelScheduler pixels = mock(PixelScheduler.class);
        PointsScheduler points = mock(PointsScheduler.class);
        GotwPointsScheduler gotw = mock(GotwPointsScheduler.class);
        SubscriptionScheduler subscriptions = mock(SubscriptionScheduler.class);
        environment.creditsScheduler = credits;
        environment.pixelScheduler = pixels;
        environment.pointsScheduler = points;
        environment.gotwPointsScheduler = gotw;
        environment.subscriptionScheduler = subscriptions;

        UserManager users = install(environment, "userManager", UserManager.class);
        HotelViewManager hotelView = install(environment, "hotelViewManager", HotelViewManager.class);
        ItemManager items = install(environment, "itemManager", ItemManager.class);
        BotManager bots = install(environment, "botManager", BotManager.class);
        GuildManager guilds = install(environment, "guildManager", GuildManager.class);
        CatalogManager catalog = install(environment, "catalogManager", CatalogManager.class);
        RoomManager rooms = install(environment, "roomManager", RoomManager.class);
        CommandHandler commands = install(environment, "commandHandler", CommandHandler.class);
        CraftingManager crafting = install(environment, "craftingManager", CraftingManager.class);
        SubscriptionManager subscriptionManager =
                install(environment, "subscriptionManager", SubscriptionManager.class);
        CalendarManager calendar = install(environment, "calendarManager", CalendarManager.class);
        GoogleTranslateManager translate = install(environment, "googleTranslateManager", GoogleTranslateManager.class);

        environment.dispose();

        verify(credits).setDisposed(true);
        verify(pixels).setDisposed(true);
        verify(points).setDisposed(true);
        verify(gotw).setDisposed(true);
        verify(subscriptions).setDisposed(true);
        verify(rooms).quiesceRoomCycles();
        verify(users).dispose();
        verify(hotelView).dispose();
        verify(items).dispose();
        verify(bots).dispose();
        verify(guilds).dispose();
        verify(catalog).dispose();
        verify(rooms).dispose();
        verify(commands).dispose();
        verify(crafting).dispose();
        verify(subscriptionManager).dispose();
        verify(calendar).dispose();
        verify(translate).clearCache();
    }

    @Test
    void oneResourceFailureDoesNotPreventLaterLegacyResourceDisposal() throws Exception {
        GameEnvironment environment = new GameEnvironment();
        CraftingManager crafting = install(environment, "craftingManager", CraftingManager.class);
        GoogleTranslateManager translate = install(environment, "googleTranslateManager", GoogleTranslateManager.class);
        doThrow(new IllegalStateException("expected")).when(crafting).dispose();

        environment.dispose();

        verify(translate).clearCache();
    }

    private static void setField(GameEnvironment environment, String name, Object value) throws Exception {
        Field field = GameEnvironment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(environment, value);
    }

    private static <T> T install(GameEnvironment environment, String name, Class<T> type) throws Exception {
        T resource = mock(type);
        setField(environment, name, resource);
        return resource;
    }
}
