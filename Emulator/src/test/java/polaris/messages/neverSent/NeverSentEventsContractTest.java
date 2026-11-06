package polaris.messages.neverSent;

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
        String pet = source("polaris/hotel/pets/Pet.java");

        assertTrue(pet.contains("owner.getClient().sendResponse(new PetLevelUpComposer(this))"));
        assertTrue(pet.indexOf("this.level++") < pet.indexOf("new PetLevelUpComposer(this)"));
    }

    @Test
    void theUnreadForumsCountAnswersTheHeaderTheClientAsksOn() throws Exception {
        String incoming = source("polaris/messages/incoming/Incoming.java");
        String registry = source("polaris/messages/PacketManager.java");
        String handler = source("polaris/messages/incoming/guilds/forums/GetUnreadForumsCountEvent.java");

        assertTrue(incoming.contains("GetUnreadForumsCountEvent = 2908"));
        assertTrue(registry.contains("Incoming.GetUnreadForumsCountEvent, GetUnreadForumsCountEvent.class"));
        assertTrue(handler.contains("new GuildForumsUnreadMessagesCountComposer("));
        // A forum counts once, however many posts it holds.
        assertTrue(handler.contains("GROUP BY `guilds`.`id`"));
    }

    @Test
    void thePromotedRoomsHeaderIsNoLongerRegisteredTwice() throws Exception {
        String registry = source("polaris/messages/PacketManager.java");

        assertTrue(!registry.contains("Incoming.RequestPromotedRoomsEvent,"));
    }

    @Test
    void theBonusDailyTaskIsAnnouncedWhenItUnlocks() throws Exception {
        String manager = source("polaris/hotel/quests/DailyTaskManager.java");

        assertTrue(manager.contains("if (justCompleted && task.isBonus())"));
        assertTrue(manager.contains("new DailyTasksAddedComposer("));
    }

    @Test
    void aTicketSomebodyElseTookSaysSoInTheWindow() throws Exception {
        String pick = source("polaris/messages/incoming/modtool/ModToolPickTicketEvent.java");
        String composer = source("polaris/messages/outgoing/modtool/ModToolIssuePickFailedComposer.java");

        assertTrue(pick.contains("new ModToolIssuePickFailedComposer(issue)"));
        assertTrue(composer.contains("this.response.appendInt(issue.modId)"));
        assertTrue(composer.contains("appendString(issue.modName == null ? \"\" : issue.modName)"));
    }

    @Test
    void changingTheMottoRefreshesAProfileLeftOpen() throws Exception {
        String motto = source("polaris/messages/incoming/users/SaveMottoEvent.java");

        assertTrue(motto.contains("new ExtendedProfileChangedComposer("));
        assertTrue(motto.indexOf("setMotto(motto)") < motto.indexOf("new ExtendedProfileChangedComposer("));
    }

    @Test
    void aPlayerWhoHasNotBeenThroughTheTourIsOfferedIt() throws Exception {
        String login = source("polaris/messages/incoming/handshake/SecureLoginEvent.java");
        String nux = source("polaris/messages/incoming/users/UserNuxEvent.java");

        assertTrue(login.contains("new NewUserExperienceNotCompleteComposer()"));
        assertTrue(login.contains("!user.getUserStats().nuxReward"));
        assertTrue(nux.contains("new NewUserGiftComposer(List.of(gifts))"));
        assertTrue(nux.contains("hotel.nux.gifts.enabled"));
    }

    @Test
    void aRefusedRoomEventSaysWhyInsteadOfNothing() throws Exception {
        String promotion = source("polaris/messages/incoming/rooms/promotions/UpdateRoomPromotionEvent.java");

        assertTrue(promotion.contains("new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_IN_GUEST_ROOM)"));
        assertTrue(promotion.contains("new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_THE_OWNER)"));
    }

    @Test
    void theErrorCodesAreTheOnesTheClientHasSentencesFor() throws Exception {
        String composer = source("polaris/messages/outgoing/navigator/CanCreateEventComposer.java");

        assertTrue(composer.contains("ERROR_NOT_IN_GUEST_ROOM = 1"));
        assertTrue(composer.contains("ERROR_NOT_THE_OWNER = 2"));
        assertTrue(composer.contains("ERROR_ROOM_CLOSED = 3"));
        assertTrue(composer.contains("ERROR_DISABLED = 4"));
        assertTrue(composer.contains("ERROR_ALREADY_HERE = 5"));
        assertTrue(composer.contains("ERROR_ALREADY_ELSEWHERE = 6"));
    }

    @Test
    void writingToSomebodyWhoseReportYouHoldAnswersThatReport() throws Exception {
        String alert = source("polaris/messages/incoming/modtool/ModToolAlertEvent.java");
        String manager = source("polaris/hotel/modtool/ModToolManager.java");

        assertTrue(alert.contains("new ModToolIssueResponseAlertComposer(message)"));
        assertTrue(alert.indexOf("pickedTicketOf(") < alert.indexOf(".alert(this.client.getUser()"));
        assertTrue(manager.contains("if (issue.state != ModToolTicketState.PICKED) continue;"));
        assertTrue(manager.contains("if (issue.modId != moderatorId) continue;"));
    }

    @Test
    void anInvitationNobodyReceivedIsReportedBackWithTheNames() throws Exception {
        String invite = source("polaris/messages/incoming/friends/InviteFriendsEvent.java");

        assertTrue(invite.contains(
                "if (user == null || user.getClient() == null || user.getUserStats().blockRoomInvites) {"));
        assertTrue(invite.contains("missed.add(buddy)"));
        assertTrue(invite.contains("new RoomInviteErrorComposer("));
        assertTrue(invite.contains("RoomInviteErrorComposer.ERROR_RECIPIENT_UNAVAILABLE, missed)"));
    }

    @Test
    void aGroupThatWasDissolvedIsTakenOffItsRoom() throws Exception {
        String delete = source("polaris/messages/incoming/guilds/GuildDeleteEvent.java");

        assertTrue(delete.contains("new RemoveGuildFromRoomComposer(guild.getId())"));
        assertTrue(delete.indexOf("new RemoveGuildFromRoomComposer(guild.getId())")
                < delete.indexOf("new RoomDataComposer(guildRoom, user, true, false)"));
    }

    @Test
    void theEffectYouAreWearingSurvivesAReconnect() throws Exception {
        String login = source("polaris/messages/incoming/handshake/SecureLoginEvent.java");

        assertTrue(login.contains("new AvatarEffectSelectedComposer("));
        assertTrue(login.contains("getEffectsComponent()"));
    }

    @Test
    void aCautionLooksLikeACautionAndLinksTheRules() throws Exception {
        String manager = source("polaris/hotel/modtool/ModToolManager.java");

        assertTrue(manager.contains("if (reason == SupportUserAlertedReason.CAUTION)"));
        assertTrue(manager.contains("new ModerationCautionComposer(alertedEvent.message, link)"));
        assertTrue(manager.contains("new UserBannedComposer(alertedEvent.message)"));
        // Every other moderator message is untouched.
        assertTrue(manager.contains("new ModToolIssueHandledComposer(alertedEvent.message)"));
    }

    @Test
    void theRoomEffectsTheClientCanPlayHaveACommand() throws Exception {
        String command = source("polaris/hotel/commands/RoomSpecialEffectCommand.java");
        String handler = source("polaris/hotel/commands/CommandHandler.java");

        assertTrue(handler.contains("addCommand(new RoomSpecialEffectCommand())"));
        assertTrue(command.contains("case \"shake\" -> SpecialRoomEventComposer.EFFECT_SHAKE"));
        assertTrue(command.contains("case \"disco\" -> SpecialRoomEventComposer.EFFECT_DISCO"));
        assertTrue(command.contains("room.sendComposer(new SpecialRoomEventComposer(effectId).compose())"));
    }
}
