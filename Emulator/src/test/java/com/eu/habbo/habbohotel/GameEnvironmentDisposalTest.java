package com.eu.habbo.habbohotel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eu.habbo.core.CreditsScheduler;
import com.eu.habbo.core.GotwPointsScheduler;
import com.eu.habbo.core.PixelScheduler;
import com.eu.habbo.core.PointsScheduler;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarManager;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.crafting.CraftingManager;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.hotelview.HotelViewManager;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionScheduler;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

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
        com.eu.habbo.habbohotel.rooms.RoomManager rooms = mock(com.eu.habbo.habbohotel.rooms.RoomManager.class);
        com.eu.habbo.habbohotel.bots.BotManager bots = mock(com.eu.habbo.habbohotel.bots.BotManager.class);
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

        HabboManager habbos = install(environment, "habboManager", HabboManager.class);
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
        verify(habbos).dispose();
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
