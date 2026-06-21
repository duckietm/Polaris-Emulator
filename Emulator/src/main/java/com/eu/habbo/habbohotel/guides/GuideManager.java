package com.eu.habbo.habbohotel.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.guides.*;
import com.eu.habbo.threading.runnables.GuardianTicketFindMoreSlaves;
import com.eu.habbo.threading.runnables.GuideFindNewHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GuideManager {
    private final Set<GuideTour> activeTours;
    private final Set<GuardianTicket> activeTickets;
    private final Set<GuardianTicket> closedTickets;
    private final Map<Habbo, Boolean> activeHelpers;
    private final Map<Habbo, GuardianTicket> activeGuardians;
    private final Map<Integer, Integer> tourRequestTiming;

    public GuideManager() {
        this.activeTours = new HashSet<>();
        this.activeTickets = new HashSet<>();
        this.closedTickets = new HashSet<>();
        this.activeHelpers = new HashMap<>();
        this.activeGuardians = new HashMap<>();
        this.tourRequestTiming = new HashMap<>();
    }

    public void userLogsOut(Habbo habbo) {
        GuideTour tour = this.getGuideTourByHabbo(habbo);

        if (tour != null) {
            this.endSession(tour);
        }

        this.activeHelpers.remove(habbo);

        GuardianTicket ticket = this.getTicketForGuardian(habbo);

        if (ticket != null) {
            ticket.removeGuardian(habbo);
        }

        this.activeGuardians.remove(habbo);
    }


    public void setOnGuide(Habbo habbo, boolean onDuty) {
        if (onDuty) {
            this.activeHelpers.put(habbo, false);
        } else {
            GuideTour tour = this.getGuideTourByHabbo(habbo);

            if (tour != null)
                return;

            this.activeHelpers.remove(habbo);
        }
    }


    public boolean findHelper(GuideTour tour) {
        synchronized (this.activeHelpers) {
            for (Map.Entry<Habbo, Boolean> set : this.activeHelpers.entrySet()) {
                if (!set.getValue()) {
                    if (!tour.hasDeclined(set.getKey().getHabboInfo().getId())) {
                        tour.checkSum++;
                        tour.setHelper(set.getKey());
                        set.getKey().getClient().sendResponse(new GuideSessionAttachedComposer(tour, true));
                        tour.getNoob().getClient().sendResponse(new GuideSessionAttachedComposer(tour, false));
                        Emulator.getThreading().run(new GuideFindNewHelper(tour, set.getKey()), 60000);
                        this.activeTours.add(tour);
                        return true;
                    }
                }
            }
        }
        this.endSession(tour);
        tour.getNoob().getClient().sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.NO_HELPERS_AVAILABLE));

        return false;
    }


    public void declineTour(GuideTour tour) {
        Habbo helper = tour.getHelper();
        tour.addDeclinedHelper(tour.getHelper().getHabboInfo().getId());
        tour.setHelper(null);
        helper.getClient().sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
        helper.getClient().sendResponse(new GuideSessionDetachedComposer());
        if (!this.findHelper(tour)) {
            this.endSession(tour);
            tour.getNoob().getClient().sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.NO_HELPERS_AVAILABLE));
        }
    }


    public void startSession(GuideTour tour, Habbo helper) {
        synchronized (this.activeTours) {
            synchronized (this.activeHelpers) {
                this.activeHelpers.put(helper, true);

                ServerMessage message = new GuideSessionStartedComposer(tour).compose();
                tour.getNoob().getClient().sendResponse(message);
                tour.getHelper().getClient().sendResponse(message);
                tour.checkSum++;
                this.tourRequestTiming.put(tour.getStartTime(), Emulator.getIntUnixTimestamp());
            }
        }
    }


    public void endSession(GuideTour tour) {
        synchronized (this.activeTours) {
            synchronized (this.activeHelpers) {
                tour.getNoob().getClient().sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
                tour.end();

                if (tour.getHelper() != null) {
                    this.activeHelpers.put(tour.getHelper(), false);
                    tour.getHelper().getClient().sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
                    tour.getHelper().getClient().sendResponse(new GuideSessionDetachedComposer());
                    tour.getHelper().getClient().sendResponse(new GuideToolsComposer(true));
                }
            }
        }
    }


    public void recommend(GuideTour tour, boolean recommend) {
        synchronized (this.activeTours) {
            tour.setWouldRecommend(recommend ? GuideRecommendStatus.YES : GuideRecommendStatus.NO);
            tour.getNoob().getClient().sendResponse(new GuideSessionDetachedComposer());
            AchievementManager.progressAchievement(tour.getNoob(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideFeedbackGiver"));

            this.activeTours.remove(tour);
        }
    }


    public GuideTour getGuideTourByHelper(Habbo helper) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (!tour.isEnded() && tour.getHelper() == helper) {
                    return tour;
                }
            }
        }

        return null;
    }


    public GuideTour getGuideTourByNoob(Habbo noob) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (tour.getNoob() == noob) {
                    return tour;
                }
            }
        }

        return null;
    }


    public GuideTour getGuideTourByHabbo(Habbo habbo) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (tour.getHelper() == habbo || tour.getNoob() == habbo) {
                    return tour;
                }
            }
        }

        return null;
    }


    public int getGuidesCount() {
        return this.activeHelpers.size();
    }


    public int getGuardiansCount() {
        return this.activeGuardians.size();
    }


    public boolean activeGuardians() {
        return this.activeGuardians.size() > 0;
    }


    public int getAverageWaitingTime() {
        synchronized (this.tourRequestTiming) {
            int total = 0;

            if (this.tourRequestTiming.isEmpty())
                return 5;

            for (Map.Entry<Integer, Integer> set : this.tourRequestTiming.entrySet()) {
                total += (set.getValue() - set.getKey());
            }

            return total / this.tourRequestTiming.size();
        }
    }


    public void addGuardianTicket(GuardianTicket ticket) {
        synchronized (this.activeTickets) {
            this.activeTickets.add(ticket);

            this.findGuardians(ticket);
        }
    }


    public void findGuardians(GuardianTicket ticket) {
        synchronized (this.activeGuardians) {
            int count = ticket.getVotedCount();

            Set<Habbo> selectedGuardians = new HashSet<>();

            for (Map.Entry<Habbo, GuardianTicket> set : this.activeGuardians.entrySet()) {
                if (count == 5)
                    break;

                if (set.getKey() == ticket.getReporter() ||
                        set.getKey() == ticket.getReported())
                    continue;

                if (set.getValue() == null) {
                    if (ticket.getVoteForGuardian(set.getKey()) == null) {
                        ticket.requestToVote(set.getKey());

                        selectedGuardians.add(set.getKey());
                    }
                }

                count++;
            }

            for (Habbo habbo : selectedGuardians) {
                this.activeGuardians.put(habbo, ticket);
            }

            if (count < 5) {
                Emulator.getThreading().run(new GuardianTicketFindMoreSlaves(ticket), 3000);
            }
        }
    }


    public void acceptTicket(Habbo guardian, boolean accepted) {
        GuardianTicket ticket = this.getTicketForGuardian(guardian);

        if (ticket != null) {
            if (!accepted) {
                ticket.removeGuardian(guardian);
                this.findGuardians(ticket);
                this.activeGuardians.put(guardian, null);
            } else {
                ticket.addGuardian(guardian);
                this.activeGuardians.put(guardian, ticket);
            }
        }
    }


    public GuardianTicket getTicketForGuardian(Habbo guardian) {
        synchronized (this.activeGuardians) {
            return this.activeGuardians.get(guardian);
        }
    }


    public GuardianTicket getRecentTicket(Habbo reporter) {
        GuardianTicket ticket = null;

        synchronized (this.activeTickets) {
            for (GuardianTicket t : this.activeTickets) {
                if (t.getReporter() == reporter) {
                    return t;
                }
            }
        }

        synchronized (this.closedTickets) {
            for (GuardianTicket t : this.closedTickets) {
                if (t.getReporter() != reporter)
                    continue;

                if (ticket == null || Emulator.getIntUnixTimestamp() - (t.getDate().getTime() / 1000) < Emulator.getIntUnixTimestamp() - (ticket.getDate().getTime() / 1000)) {
                    ticket = t;
                }
            }
        }

        return ticket;
    }

    public GuardianTicket getOpenReportedHabboTicket(Habbo reported) {
        synchronized (this.activeTickets) {
            for (GuardianTicket t : this.activeTickets) {
                if (t.getReported() == reported) {
                    return t;
                }
            }
        }

        return null;
    }


    public void closeTicket(GuardianTicket ticket) {
        synchronized (this.activeTickets) {
            this.activeTickets.remove(ticket);
        }

        synchronized (this.closedTickets) {
            this.closedTickets.add(ticket);
        }

        Set<Habbo> toUpdate = new HashSet<>();

        synchronized (this.activeGuardians) {
            for (Map.Entry<Habbo, GuardianTicket> set : this.activeGuardians.entrySet()) {
                if (set.getValue() == ticket) {
                    toUpdate.add(set.getKey());
                }
            }

            for (Habbo habbo : toUpdate) {
                this.activeGuardians.put(habbo, null);
            }
        }
    }


    public void setOnGuardian(Habbo habbo, boolean onDuty) {
        if (onDuty) {
            this.activeGuardians.put(habbo, null);
        } else {
            GuardianTicket ticket = this.getTicketForGuardian(habbo);

            if (ticket != null) {
                ticket.removeGuardian(habbo);
            }

            this.activeGuardians.remove(habbo);
        }
    }


    public void cleanup() {
        synchronized (this.activeTours) {
            Set<GuideTour> tours = new HashSet<>();
            for (GuideTour tour : this.activeTours) {
                if (tour.isEnded() && (Emulator.getIntUnixTimestamp() - tour.getEndTime() > 300)) {
                    tours.add(tour);
                }
            }

            for (GuideTour tour : tours) {
                this.activeTours.remove(tour);
            }
        }

        synchronized (this.activeTickets) {
            Set<GuardianTicket> tickets = new HashSet<>();

            for (GuardianTicket ticket : this.closedTickets) {
                if (Emulator.getIntUnixTimestamp() - (ticket.getDate().getTime() / 1000) > 15 * 60) {
                    tickets.add(ticket);
                }
            }

            for (GuardianTicket ticket : tickets) {
                this.closedTickets.remove(ticket);
            }
        }
    }
}
