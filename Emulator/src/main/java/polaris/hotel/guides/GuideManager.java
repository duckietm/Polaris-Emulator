package polaris.hotel.guides;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.guides.GuideSessionAttachedComposer;
import polaris.messages.outgoing.guides.GuideSessionDetachedComposer;
import polaris.messages.outgoing.guides.GuideSessionEndedComposer;
import polaris.messages.outgoing.guides.GuideSessionErrorComposer;
import polaris.messages.outgoing.guides.GuideSessionStartedComposer;
import polaris.messages.outgoing.guides.GuideToolsComposer;
import polaris.threading.runnables.GuardianTicketFindMoreSlaves;
import polaris.threading.runnables.GuideFindNewHelper;

public class GuideManager {
    private final Set<GuideTour> activeTours;
    private final Set<GuardianTicket> activeTickets;
    private final Set<GuardianTicket> closedTickets;
    private final Map<User, Boolean> activeHelpers;
    private final Map<User, GuardianTicket> activeGuardians;
    private final Map<Integer, Integer> tourRequestTiming;

    public GuideManager() {
        // Each collection is its own monitor: the loops below lock it, and a synchronized wrapper makes the
        // single puts and removes that ran outside those locks (logout, going on duty) take the same one.
        this.activeTours = Collections.synchronizedSet(new HashSet<>());
        this.activeTickets = Collections.synchronizedSet(new HashSet<>());
        this.closedTickets = Collections.synchronizedSet(new HashSet<>());
        this.activeHelpers = Collections.synchronizedMap(new HashMap<>());
        this.activeGuardians = Collections.synchronizedMap(new HashMap<>());
        this.tourRequestTiming = Collections.synchronizedMap(new HashMap<>());
    }

    public void userLogsOut(User user) {
        GuideTour tour = this.getGuideTourByUser(user);

        if (tour != null) {
            this.endSession(tour);
        }

        this.activeHelpers.remove(user);

        GuardianTicket ticket = this.getTicketForGuardian(user);

        if (ticket != null) {
            ticket.removeGuardian(user);
        }

        this.activeGuardians.remove(user);
    }

    public void setOnGuide(User user, boolean onDuty) {
        if (onDuty) {
            this.activeHelpers.put(user, false);
        } else {
            GuideTour tour = this.getGuideTourByUser(user);

            if (tour != null) return;

            this.activeHelpers.remove(user);
        }
    }

    public boolean findHelper(GuideTour tour) {
        synchronized (this.activeHelpers) {
            for (Map.Entry<User, Boolean> set : this.activeHelpers.entrySet()) {
                if (!set.getValue()) {
                    if (!tour.hasDeclined(set.getKey().getUserInfo().getId())) {
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
        tour.getNoob()
                .getClient()
                .sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.NO_HELPERS_AVAILABLE));

        return false;
    }

    public void declineTour(GuideTour tour) {
        User helper = tour.getHelper();
        tour.addDeclinedHelper(tour.getHelper().getUserInfo().getId());
        tour.setHelper(null);
        helper.getClient().sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
        helper.getClient().sendResponse(new GuideSessionDetachedComposer());
        if (!this.findHelper(tour)) {
            this.endSession(tour);
            tour.getNoob()
                    .getClient()
                    .sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.NO_HELPERS_AVAILABLE));
        }
    }

    public void startSession(GuideTour tour, User helper) {
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
                tour.getNoob()
                        .getClient()
                        .sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
                tour.end();

                if (tour.getHelper() != null) {
                    this.activeHelpers.put(tour.getHelper(), false);
                    tour.getHelper()
                            .getClient()
                            .sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
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
            AchievementManager.progressAchievement(
                    tour.getNoob(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideFeedbackGiver"));

            this.activeTours.remove(tour);
        }
    }

    public GuideTour getGuideTourByHelper(User helper) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (!tour.isEnded() && tour.getHelper() == helper) {
                    return tour;
                }
            }
        }

        return null;
    }

    public GuideTour getGuideTourByNoob(User noob) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (tour.getNoob() == noob) {
                    return tour;
                }
            }
        }

        return null;
    }

    /**
     * The tour the user takes part in that is still going on. An ended tour stays listed until its recommendation
     * or the cleanup, and the chat and the report used to keep working on it - messages to the former helper, and
     * a moderation ticket for every report packet.
     */
    public GuideTour getActiveGuideTourByUser(User user) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (!tour.isEnded() && (tour.getHelper() == user || tour.getNoob() == user)) {
                    return tour;
                }
            }
        }

        return null;
    }

    /** Ends a tour that one side reported: one report per tour, and the helper is free again. */
    public boolean endReportedSession(GuideTour tour) {
        synchronized (this.activeTours) {
            synchronized (this.activeHelpers) {
                if (tour.isEnded()) return false;

                tour.end();

                if (tour.getHelper() != null) this.activeHelpers.put(tour.getHelper(), false);

                return true;
            }
        }
    }

    public GuideTour getGuideTourByUser(User user) {
        synchronized (this.activeTours) {
            for (GuideTour tour : this.activeTours) {
                if (tour.getHelper() == user || tour.getNoob() == user) {
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

            if (this.tourRequestTiming.isEmpty()) return 5;

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

            Set<User> selectedGuardians = new HashSet<>();

            for (Map.Entry<User, GuardianTicket> set : this.activeGuardians.entrySet()) {
                if (count == 5) break;

                if (set.getKey() == ticket.getReporter() || set.getKey() == ticket.getReported()) continue;

                if (set.getValue() == null) {
                    if (ticket.getVoteForGuardian(set.getKey()) == null) {
                        ticket.requestToVote(set.getKey());

                        selectedGuardians.add(set.getKey());
                    }
                }

                count++;
            }

            for (User user : selectedGuardians) {
                this.activeGuardians.put(user, ticket);
            }

            if (count < 5) {
                Emulator.getThreading().run(new GuardianTicketFindMoreSlaves(ticket), 3000);
            }
        }
    }

    public void acceptTicket(User guardian, boolean accepted) {
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

    /** The guardian left their review: their vote stays counted and they are free for the next one. */
    public void detachGuardian(User guardian) {
        synchronized (this.activeGuardians) {
            GuardianTicket ticket = this.activeGuardians.get(guardian);

            if (ticket == null) return;

            ticket.detachGuardian(guardian);
            this.activeGuardians.put(guardian, null);
        }
    }

    public GuardianTicket getTicketForGuardian(User guardian) {
        synchronized (this.activeGuardians) {
            return this.activeGuardians.get(guardian);
        }
    }

    public GuardianTicket getRecentTicket(User reporter) {
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
                if (t.getReporter() != reporter) continue;

                if (ticket == null
                        || Emulator.getIntUnixTimestamp() - (t.getDate().getTime() / 1000)
                                < Emulator.getIntUnixTimestamp()
                                        - (ticket.getDate().getTime() / 1000)) {
                    ticket = t;
                }
            }
        }

        return ticket;
    }

    public GuardianTicket getOpenReportedUserTicket(User reported) {
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

        Set<User> toUpdate = new HashSet<>();

        synchronized (this.activeGuardians) {
            for (Map.Entry<User, GuardianTicket> set : this.activeGuardians.entrySet()) {
                if (set.getValue() == ticket) {
                    toUpdate.add(set.getKey());
                }
            }

            for (User user : toUpdate) {
                this.activeGuardians.put(user, null);
            }
        }
    }

    public void setOnGuardian(User user, boolean onDuty) {
        if (onDuty) {
            this.activeGuardians.put(user, null);
        } else {
            GuardianTicket ticket = this.getTicketForGuardian(user);

            if (ticket != null) {
                ticket.removeGuardian(user);
            }

            this.activeGuardians.remove(user);
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
