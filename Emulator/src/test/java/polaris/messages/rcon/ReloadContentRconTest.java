package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.GameEnvironment;
import polaris.hotel.commands.UpdateCatalogCommand;
import polaris.hotel.commands.UpdatePermissionsCommand;
import polaris.hotel.commands.UpdatePetDataCommand;
import polaris.hotel.commands.UpdateTextsCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.quests.QuestManager;

class ReloadContentRconTest {
    private static final Gson GSON = new Gson();

    private final GameEnvironment environment = mock(GameEnvironment.class);

    private ReloadContent run(String json) {
        ReloadContent command = new ReloadContent(() -> this.environment);
        ReloadContent.JSON payload = GSON.fromJson(json, ReloadContent.JSON.class);

        if (command.validate(payload)) {
            command.handle(GSON, payload);
        }

        return command;
    }

    @Test
    void theTargetIsRequired() {
        ReloadContent command = this.run("{}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("target is required", command.message);
    }

    @Test
    void anUnknownTargetListsTheSupportedOnes() {
        ReloadContent command = this.run("{\"target\":\"vouchers\"}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("Unknown target. Use one of: " + String.join(", ", ReloadContent.TARGETS), command.message);
        assertFalse(ReloadContent.TARGETS.contains("vouchers"));
        assertTrue(ReloadContent.TARGETS.containsAll(java.util.List.of(
                "achievements",
                "navigator",
                "petdata",
                "guildparts",
                "chatbubbles",
                "discounts",
                "calendar",
                "config",
                "catalog",
                "items",
                "texts",
                "permissions",
                "wordfilter",
                "hotelview",
                "polls",
                "bots",
                "youtube",
                "quests",
                "dailytasks")));
    }

    @Test
    void targetsAreReadWithoutCaseOrSeparators() {
        assertEquals("petdata", ReloadContent.normalize(" Pet_Data "));
        assertEquals("dailytasks", ReloadContent.normalize("daily-tasks"));
        assertEquals("", ReloadContent.normalize(null));
    }

    @Test
    void aTargetRunsItsUpdateCommandsReloadAndIsAudited() {
        try (MockedStatic<UpdatePetDataCommand> petData = mockStatic(UpdatePetDataCommand.class);
                MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            ReloadContent command = this.run("{\"target\":\"pet_data\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            petData.verify(UpdatePetDataCommand::reload);
            audit.verify(() -> HousekeepingAuditLog.log(
                    eq(0),
                    eq("All-in-1 Manager"),
                    eq("hotel.reload.petdata"),
                    eq(HousekeepingAuditLog.TARGET_HOTEL),
                    eq(0),
                    eq(""),
                    eq("target=petdata"),
                    eq("rcon")));
        }
    }

    @Test
    void permissionsAreReloadedForTheSystemOperator() {
        try (MockedStatic<UpdatePermissionsCommand> permissions = mockStatic(UpdatePermissionsCommand.class);
                MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            assertEquals(RCONMessage.STATUS_OK, this.run("{\"target\":\"permissions\"}").status);
            permissions.verify(() -> UpdatePermissionsCommand.reload(0, "All-in-1 Manager"));
        }
    }

    @Test
    void questsReloadThroughTheirManager() {
        QuestManager quests = mock(QuestManager.class);
        when(this.environment.getQuestManager()).thenReturn(quests);

        try (MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            assertEquals(RCONMessage.STATUS_OK, this.run("{\"target\":\"quests\"}").status);
            verify(quests).reload();
        }
    }

    @Test
    void theCatalogCooldownIsAnErrorAndNotAudited() {
        try (MockedStatic<UpdateCatalogCommand> catalog = mockStatic(UpdateCatalogCommand.class);
                MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            catalog.when(UpdateCatalogCommand::reload).thenReturn(false);

            ReloadContent command = this.run("{\"target\":\"catalog\"}");

            assertEquals(RCONMessage.STATUS_ERROR, command.status);
            assertEquals(ReloadContent.CATALOG_COOLDOWN, command.message);
            audit.verifyNoInteractions();
        }
    }

    @Test
    void aFailedReloadIsASystemError() {
        try (MockedStatic<UpdateTextsCommand> texts = mockStatic(UpdateTextsCommand.class)) {
            texts.when(UpdateTextsCommand::reload).thenThrow(new IllegalStateException("broken"));

            ReloadContent command = this.run("{\"target\":\"texts\"}");

            assertEquals(RCONMessage.SYSTEM_ERROR, command.status);
            assertEquals("The texts could not be reloaded.", command.message);
        }
    }
}
