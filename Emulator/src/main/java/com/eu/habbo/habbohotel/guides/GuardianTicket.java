package com.eu.habbo.habbohotel.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.guardians.GuardianNewReportReceivedComposer;
import com.eu.habbo.messages.outgoing.guardians.GuardianVotingRequestedComposer;
import com.eu.habbo.messages.outgoing.guardians.GuardianVotingResultComposer;
import com.eu.habbo.messages.outgoing.guardians.GuardianVotingTimeEnded;
import com.eu.habbo.messages.outgoing.guardians.GuardianVotingVotesComposer;
import com.eu.habbo.messages.outgoing.guides.BullyReportClosedComposer;
import com.eu.habbo.messages.outgoing.guides.GuideSessionErrorComposer;
import com.eu.habbo.threading.runnables.GuardianNotAccepted;
import com.eu.habbo.threading.runnables.GuardianVotingFinish;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class GuardianTicket {
    private final Map<Habbo, GuardianVote> votes = new HashMap<>();
    private final Habbo reporter;
    private final Habbo reported;
    private final Date date;
    private ArrayList<ModToolChatLog> chatLogs;
    private final String reportedName;
    private final String reportedFigure;
    private final String roomName;
    private GuardianVoteType verdict;
    /** When the current voting round ends, in epoch milliseconds. */
    private long deadline;

    private int resendCount = 0;
    /** Bumped on every new round so the timer of an earlier round does not end the current one. */
    private int checkSum = 0;

    private int guardianCount = 0;

    public GuardianTicket(Habbo reporter, Habbo reported, ArrayList<ModToolChatLog> chatLogs) {
        this.chatLogs = chatLogs;
        Collections.sort(chatLogs);

        this.reported = reported;
        this.reporter = reporter;
        this.date = new Date();

        // Kept for the reporter's "pending report" window, which shows them after the user has left.
        this.reportedName = reported.getHabboInfo().getUsername();
        this.reportedFigure = reported.getHabboInfo().getLook();
        Room room = reported.getHabboInfo().getCurrentRoom();
        this.roomName = room != null ? room.getName() : "";

        this.startRound(120);
    }

    private void startRound(int seconds) {
        this.checkSum++;
        this.deadline = System.currentTimeMillis() + seconds * 1000L;
        Emulator.getThreading().run(new GuardianVotingFinish(this), seconds * 1000L);
    }

    public void requestToVote(Habbo guardian) {
        guardian.getClient().sendResponse(new GuardianNewReportReceivedComposer());

        this.votes.put(guardian, new GuardianVote(this.guardianCount, guardian));
        this.guardianCount++;

        Emulator.getThreading()
                .run(
                        new GuardianNotAccepted(this, guardian),
                        Emulator.getConfig().getInt("guardians.accept.timer") * 1000L);
    }

    public void addGuardian(Habbo guardian) {
        GuardianVote vote = this.votes.get(guardian);

        if (vote != null && vote.type == GuardianVoteType.SEARCHING) {
            guardian.getClient().sendResponse(new GuardianVotingRequestedComposer(this));
            vote.type = GuardianVoteType.WAITING;
            this.updateVotes();
        }
    }

    public void removeGuardian(Habbo guardian) {
        GuardianVote vote = this.getVoteForGuardian(guardian);

        if (vote == null) return;

        if (vote.type == GuardianVoteType.SEARCHING || vote.type == GuardianVoteType.WAITING) {
            this.getVoteForGuardian(guardian).type = GuardianVoteType.NOT_VOTED;
        }

        this.getVoteForGuardian(guardian).ignore = true;

        guardian.getClient().sendResponse(new GuardianVotingTimeEnded());

        this.updateVotes();
    }

    public void vote(Habbo guardian, GuardianVoteType vote) {
        GuardianVote current = this.votes.get(guardian);

        // Only a guardian who accepted the review and has not voted yet may vote, and only once.
        if (current == null || current.type != GuardianVoteType.WAITING || !this.inProgress()) return;

        current.type = vote;

        this.updateVotes();

        AchievementManager.progressAchievement(
                guardian, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideChatReviewer"));

        // The verdict comes as soon as enough guardians voted; otherwise the round timer decides.
        if (this.getVotedCount() >= minimumVotes()) {
            this.finish();
        }
    }

    /** The guardian closed the review: they no longer take part and are free for the next one. */
    public void detachGuardian(Habbo guardian) {
        GuardianVote vote = this.votes.get(guardian);

        if (vote == null) return;

        if (vote.type == GuardianVoteType.SEARCHING || vote.type == GuardianVoteType.WAITING) {
            vote.type = GuardianVoteType.NOT_VOTED;
        }

        vote.ignore();

        this.updateVotes();
    }

    public void updateVotes() {
        synchronized (this.votes) {
            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                if (set.getValue().type == GuardianVoteType.WAITING
                        || set.getValue().type == GuardianVoteType.NOT_VOTED
                        || set.getValue().ignore
                        || set.getValue().type == GuardianVoteType.SEARCHING) continue;

                set.getKey().getClient().sendResponse(new GuardianVotingVotesComposer(this, set.getKey()));
            }
        }
    }

    public void finish() {
        if (!this.inProgress()) return;

        int votedCount = this.getVotedCount();
        if (votedCount < minimumVotes()) {
            if (this.votes.size() >= Emulator.getConfig().getInt("guardians.maximum.guardians.total")
                    || this.resendCount >= Emulator.getConfig().getInt("guardians.maximum.resends")) {
                this.verdict = GuardianVoteType.FORWARDED;

                this.sendResults();

                Emulator.getGameEnvironment().getGuideManager().closeTicket(this);

                this.forwardToModerators();

                // Not enough guardians answered: the official client tells the reporter exactly that.
                this.sendToReporter(new GuideSessionErrorComposer(GuideSessionErrorComposer.NO_GUARDIANS_AVAILABLE));
            } else {
                this.startRound(30);
                this.resendCount++;

                Emulator.getGameEnvironment().getGuideManager().findGuardians(this);
            }
        } else {
            this.verdict = this.calculateVerdict();

            this.sendResults();

            Emulator.getGameEnvironment().getGuideManager().closeTicket(this);

            int resolution;

            if (this.verdict == GuardianVoteType.ACCEPTABLY) {
                resolution = BullyReportClosedComposer.MISUSE;
            } else if (this.verdict == GuardianVoteType.FORWARDED) {
                // A tie has no verdict: the moderators take the case, as when too few voted.
                this.forwardToModerators();
                resolution = BullyReportClosedComposer.CLOSED;
            } else {
                resolution = BullyReportClosedComposer.TOOK_ACTION;
            }

            this.sendToReporter(new BullyReportClosedComposer(resolution));
        }
    }

    /**
     * Every guardian who voted gets the verdict; one who accepted but did not vote is let go, so
     * no window is left waiting for a review that has ended.
     */
    private void sendResults() {
        synchronized (this.votes) {
            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                GuardianVoteType type = set.getValue().type;

                if (set.getKey().getClient() == null) continue;

                if (type == GuardianVoteType.ACCEPTABLY
                        || type == GuardianVoteType.BADLY
                        || type == GuardianVoteType.AWFULLY) {
                    if (!set.getValue().ignore) {
                        set.getKey().getClient().sendResponse(new GuardianVotingResultComposer(this, set.getValue()));
                    }
                } else if (type == GuardianVoteType.WAITING || type == GuardianVoteType.SEARCHING) {
                    set.getKey().getClient().sendResponse(new GuardianVotingTimeEnded());
                }
            }
        }
    }

    private static int minimumVotes() {
        return Emulator.getConfig().getInt("guardians.minimum.votes");
    }

    private void forwardToModerators() {
        ModToolIssue issue = new ModToolIssue(
                this.reporter.getHabboInfo().getId(),
                this.reporter.getHabboInfo().getUsername(),
                this.reported.getHabboInfo().getId(),
                this.reported.getHabboInfo().getUsername(),
                0,
                "",
                ModToolTicketType.GUARDIAN);

        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
    }

    private void sendToReporter(MessageComposer composer) {
        if (this.reporter.getClient() != null) {
            this.reporter.getClient().sendResponse(composer);
        }
    }

    public boolean inProgress() {
        return this.verdict == null;
    }

    /**
     * The majority verdict of the guardians who actually voted. A tie has no winner, which is
     * exactly the "inconclusive" outcome the official results window renders for the forwarded
     * vote code (-1).
     */
    public GuardianVoteType calculateVerdict() {
        EnumMap<GuardianVoteType, Integer> tally = new EnumMap<>(GuardianVoteType.class);

        synchronized (this.votes) {
            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                GuardianVoteType type = set.getValue().type;

                if (type == GuardianVoteType.ACCEPTABLY
                        || type == GuardianVoteType.BADLY
                        || type == GuardianVoteType.AWFULLY) {
                    tally.merge(type, 1, Integer::sum);
                }
            }
        }

        GuardianVoteType winner = GuardianVoteType.FORWARDED;
        int best = 0;
        boolean tied = false;

        for (Map.Entry<GuardianVoteType, Integer> set : tally.entrySet()) {
            if (set.getValue() > best) {
                best = set.getValue();
                winner = set.getKey();
                tied = false;
            } else if (set.getValue() == best) {
                tied = true;
            }
        }

        return tied ? GuardianVoteType.FORWARDED : winner;
    }

    public GuardianVote getVoteForGuardian(Habbo guardian) {
        return this.votes.get(guardian);
    }

    public Map<Habbo, GuardianVote> getVotes() {
        return this.votes;
    }

    /** Seconds left in the current voting round, what the guardian's countdown starts from. */
    public int getTimeLeft() {
        return (int) Math.max(0L, (this.deadline - System.currentTimeMillis() + 999L) / 1000L);
    }

    public String getReportedName() {
        return this.reportedName;
    }

    public String getReportedFigure() {
        return this.reportedFigure;
    }

    public String getRoomName() {
        return this.roomName;
    }

    public GuardianVoteType getVerdict() {
        return this.verdict;
    }

    public ArrayList<ModToolChatLog> getChatLogs() {
        return this.chatLogs;
    }

    public int getResendCount() {
        return this.resendCount;
    }

    public int getCheckSum() {
        return this.checkSum;
    }

    public Habbo getReporter() {
        return this.reporter;
    }

    public Habbo getReported() {
        return this.reported;
    }

    public Date getDate() {
        return this.date;
    }

    public int getGuardianCount() {
        return this.guardianCount;
    }

    public ArrayList<GuardianVote> getSortedVotes(Habbo guardian) {
        synchronized (this.votes) {
            ArrayList<GuardianVote> votes = new ArrayList<>(this.votes.values());
            Collections.sort(votes);

            GuardianVote v = null;
            for (GuardianVote vote : votes) {
                if (vote.guardian == guardian) {
                    v = vote;
                    break;
                }
            }
            votes.remove(v);

            return votes;
        }
    }

    public int getVotedCount() {
        int count = 0;
        synchronized (this.votes) {
            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                if (set.getValue().type == GuardianVoteType.ACCEPTABLY
                        || set.getValue().type == GuardianVoteType.BADLY
                        || set.getValue().type == GuardianVoteType.AWFULLY) count++;
            }
        }

        return count;
    }
}
