package com.eu.habbo.messages.neverSent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The events the client listened to and nobody ever sent. Each test pins the place that sends one,
 * because the defect they all shared was not a wrong packet: it was no packet at all.
 */
class NeverSentEventsContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theOwnerOfAPetIsToldWhenItLevelsUp() throws Exception {
        String pet = source("com/eu/habbo/habbohotel/pets/Pet.java");

        assertTrue(pet.contains("owner.getClient().sendResponse(new PetLevelUpComposer(this))"));
        assertTrue(pet.indexOf("this.level++") < pet.indexOf("new PetLevelUpComposer(this)"));
    }

    @Test
    void theUnreadForumsCountAnswersTheHeaderTheClientAsksOn() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");
        String registry = source("com/eu/habbo/messages/PacketManager.java");
        String handler = source("com/eu/habbo/messages/incoming/guilds/forums/GetUnreadForumsCountEvent.java");

        assertTrue(incoming.contains("GetUnreadForumsCountEvent = 2908"));
        assertTrue(registry.contains("Incoming.GetUnreadForumsCountEvent, GetUnreadForumsCountEvent.class"));
        assertTrue(handler.contains("new GuildForumsUnreadMessagesCountComposer("));
        // A forum counts once, however many posts it holds.
        assertTrue(handler.contains("GROUP BY `guilds`.`id`"));
    }

    @Test
    void thePromotedRoomsHeaderIsNoLongerRegisteredTwice() throws Exception {
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(!registry.contains("Incoming.RequestPromotedRoomsEvent,"));
    }

    @Test
    void theBonusDailyTaskIsAnnouncedWhenItUnlocks() throws Exception {
        String manager = source("com/eu/habbo/habbohotel/quests/DailyTaskManager.java");

        assertTrue(manager.contains("if (justCompleted && task.isBonus())"));
        assertTrue(manager.contains("new DailyTasksAddedComposer("));
    }

    @Test
    void aTicketSomebodyElseTookSaysSoInTheWindow() throws Exception {
        String pick = source("com/eu/habbo/messages/incoming/modtool/ModToolPickTicketEvent.java");
        String composer = source("com/eu/habbo/messages/outgoing/modtool/ModToolIssuePickFailedComposer.java");

        assertTrue(pick.contains("new ModToolIssuePickFailedComposer(issue)"));
        assertTrue(composer.contains("this.response.appendInt(issue.modId)"));
        assertTrue(composer.contains("appendString(issue.modName == null ? \"\" : issue.modName)"));
    }

    @Test
    void changingTheMottoRefreshesAProfileLeftOpen() throws Exception {
        String motto = source("com/eu/habbo/messages/incoming/users/SaveMottoEvent.java");

        assertTrue(motto.contains("new ExtendedProfileChangedComposer("));
        assertTrue(motto.indexOf("setMotto(motto)") < motto.indexOf("new ExtendedProfileChangedComposer("));
    }

    @Test
    void aPlayerWhoHasNotBeenThroughTheTourIsOfferedIt() throws Exception {
        String login = source("com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");
        String nux = source("com/eu/habbo/messages/incoming/users/UserNuxEvent.java");

        assertTrue(login.contains("new NewUserExperienceNotCompleteComposer()"));
        assertTrue(login.contains("!this.client.getHabbo().getHabboStats().nuxReward"));
        assertTrue(nux.contains("new NewUserGiftComposer(List.of(gifts))"));
        assertTrue(nux.contains("hotel.nux.gifts.enabled"));
    }

    @Test
    void theRoomEffectsTheClientCanPlayHaveACommand() throws Exception {
        String command = source("com/eu/habbo/habbohotel/commands/RoomSpecialEffectCommand.java");
        String handler = source("com/eu/habbo/habbohotel/commands/CommandHandler.java");

        assertTrue(handler.contains("addCommand(new RoomSpecialEffectCommand())"));
        assertTrue(command.contains("case \"shake\" -> SpecialRoomEventComposer.EFFECT_SHAKE"));
        assertTrue(command.contains("case \"disco\" -> SpecialRoomEventComposer.EFFECT_DISCO"));
        assertTrue(command.contains("room.sendComposer(new SpecialRoomEventComposer(effectId).compose())"));
    }
}
