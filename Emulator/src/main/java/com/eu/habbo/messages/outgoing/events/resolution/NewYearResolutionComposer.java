package com.eu.habbo.messages.outgoing.events.resolution;

import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolutionCandidate;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * The picker of a resolution furni: every achievement the owner may promise, with the level they are
 * on, the badge of the level they would have to reach and why they may not pick it. The client hides
 * the window when the list is empty, so an owner with no achievement yet simply sees nothing.
 */
public class NewYearResolutionComposer extends MessageComposer {
    private final int stuffId;
    private final List<AchievementResolutionCandidate> candidates;
    private final int secondsLeft;

    public NewYearResolutionComposer(int stuffId, List<AchievementResolutionCandidate> candidates, int secondsLeft) {
        this.stuffId = stuffId;
        this.candidates = candidates;
        this.secondsLeft = secondsLeft;
    }

    /**
     * @deprecated The old placeholder answer, which described a competition that did not exist. It
     *     now says "nothing to pick" so a plugin still compiling against it cannot invent one.
     */
    @Deprecated
    public NewYearResolutionComposer() {
        this(0, List.of(), 0);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewYearResolutionComposer);
        this.response.appendInt(this.stuffId);
        this.response.appendInt(this.candidates.size());

        for (AchievementResolutionCandidate candidate : this.candidates) {
            this.response.appendInt(candidate.achievementId());
            this.response.appendInt(candidate.level());
            this.response.appendString(candidate.badgeCode());
            this.response.appendInt(candidate.requiredLevel());
            this.response.appendInt(candidate.state());
        }

        this.response.appendInt(this.secondsLeft);
        return this.response;
    }
}
