package com.eu.habbo.habbohotel.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.guardians.*;
import com.eu.habbo.messages.outgoing.guides.BullyReportClosedComposer;
import com.eu.habbo.threading.runnables.GuardianNotAccepted;
import com.eu.habbo.threading.runnables.GuardianVotingFinish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GuardianTicket {
    private final Map<Habbo, GuardianVote> votes = new HashMap<>();
    private final Habbo reporter;
    private final Habbo reported;
    private final Date date;
    private ArrayList<ModToolChatLog> chatLogs;
    private GuardianVoteType verdict;
    private int timeLeft = 120;
    private int resendCount = 0;
    private int checkSum = 0;
    private int guardianCount = 0;

    public GuardianTicket(Habbo reporter, Habbo reported, ArrayList<ModToolChatLog> chatLogs) {
        this.chatLogs = chatLogs;
        Collections.sort(chatLogs);
        Emulator.getThreading().run(new GuardianVotingFinish(this), 120000);

        this.reported = reported;
        this.reporter = reporter;
        this.date = new Date();
    }


    public void requestToVote(Habbo guardian) {
        guardian.getClient().sendResponse(new GuardianNewReportReceivedComposer());

        this.votes.put(guardian, new GuardianVote(this.guardianCount, guardian));
        this.guardianCount++;

        Emulator.getThreading().run(new GuardianNotAccepted(this, guardian), Emulator.getConfig().getInt("guardians.accept.timer") * 1000L);
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

        if (vote == null)
            return;

        if (vote.type == GuardianVoteType.SEARCHING || vote.type == GuardianVoteType.WAITING) {
            this.getVoteForGuardian(guardian).type = GuardianVoteType.NOT_VOTED;
        }

        this.getVoteForGuardian(guardian).ignore = true;

        guardian.getClient().sendResponse(new GuardianVotingTimeEnded());

        this.updateVotes();
    }


    public void vote(Habbo guardian, GuardianVoteType vote) {
        this.votes.get(guardian).type = vote;

        this.updateVotes();

        AchievementManager.progressAchievement(guardian, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideChatReviewer"));

        this.finish();
    }


    public void updateVotes() {
        synchronized (this.votes) {
            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                if (set.getValue().type == GuardianVoteType.WAITING || set.getValue().type == GuardianVoteType.NOT_VOTED || set.getValue().ignore || set.getValue().type == GuardianVoteType.SEARCHING)
                    continue;

                set.getKey().getClient().sendResponse(new GuardianVotingVotesComposer(this, set.getKey()));
            }
        }
    }


    public void finish() {
        int votedCount = this.getVotedCount();
        if (votedCount < Emulator.getConfig().getInt("guardians.minimum.votes")) {
            if (this.votes.size() >= Emulator.getConfig().getInt("guardians.maximum.guardians.total") || this.resendCount == Emulator.getConfig().getInt("guardians.maximum.resends")) {
                this.verdict = GuardianVoteType.FORWARDED;

                Emulator.getGameEnvironment().getGuideManager().closeTicket(this);

                ModToolIssue issue = new ModToolIssue(this.reporter.getHabboInfo().getId(),
                        this.reporter.getHabboInfo().getUsername(),
                        this.reported.getHabboInfo().getId(),
                        this.reported.getHabboInfo().getUsername(),
                        0,
                        "",
                        ModToolTicketType.GUARDIAN);

                Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
                Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);

                this.reporter.getClient().sendResponse(new BullyReportClosedComposer(BullyReportClosedComposer.CLOSED));
            } else {
                this.timeLeft = 30;
                Emulator.getThreading().run(new GuardianVotingFinish(this), 10000);
                this.resendCount++;

                Emulator.getGameEnvironment().getGuideManager().findGuardians(this);
            }
        } else {
            this.verdict = this.calculateVerdict();

            for (Map.Entry<Habbo, GuardianVote> set : this.votes.entrySet()) {
                if (set.getValue().type == GuardianVoteType.ACCEPTABLY ||
                        set.getValue().type == GuardianVoteType.BADLY ||
                        set.getValue().type == GuardianVoteType.AWFULLY) {
                    set.getKey().getClient().sendResponse(new GuardianVotingResultComposer(this, set.getValue()));
                }
            }

            Emulator.getGameEnvironment().getGuideManager().closeTicket(this);

            if (this.verdict == GuardianVoteType.ACCEPTABLY)
                this.reporter.getClient().sendResponse(new BullyReportClosedComposer(BullyReportClosedComposer.MISUSE));
            else
                this.reporter.getClient().sendResponse(new BullyReportClosedComposer(BullyReportClosedComposer.CLOSED));
        }
    }


    public boolean inProgress() {
        return this.verdict == null;
    }


    public GuardianVoteType calculateVerdict() {
        // Vote counting logic placeholder - currently returns fixed verdict
        return GuardianVoteType.BADLY;
    }

    public GuardianVote getVoteForGuardian(Habbo guardian) {
        return this.votes.get(guardian);
    }

    public Map<Habbo, GuardianVote> getVotes() {
        return this.votes;
    }

    public int getTimeLeft() {
        return this.timeLeft;
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
                if (set.getValue().type == GuardianVoteType.ACCEPTABLY ||
                        set.getValue().type == GuardianVoteType.BADLY ||
                        set.getValue().type == GuardianVoteType.AWFULLY)
                    count++;
            }
        }

        return count;
    }
}
