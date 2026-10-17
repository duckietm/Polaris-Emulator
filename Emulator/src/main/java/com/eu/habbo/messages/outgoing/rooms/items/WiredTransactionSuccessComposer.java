package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestAirWire;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * A wired transaction went through (AIR {@code WiredTransactionSuccess}): {@code int successType}
 * (0 deposit, 1 withdraw, 2 reward, 3 payment, 4 trade); a reward also carries what was given as a
 * rule ({@code int count, TradeRequirementNode[]}), the box's reward text and whether the reward
 * popup opens by itself.
 */
public class WiredTransactionSuccessComposer extends MessageComposer {
    public static final int TYPE_DEPOSIT = 0;
    public static final int TYPE_WITHDRAW = 1;
    public static final int TYPE_REWARD = 2;
    public static final int TYPE_PAYMENT = 3;
    public static final int TYPE_TRADE = 4;

    private final int type;
    private final List<Term> rule;
    private final String rewardText;
    private final boolean openByDefault;

    public WiredTransactionSuccessComposer(int type) {
        this(type, List.of(), "", false);
    }

    public WiredTransactionSuccessComposer(int type, List<Term> rule, String rewardText, boolean openByDefault) {
        this.type = type;
        this.rule = rule == null ? List.of() : List.copyOf(rule);
        this.rewardText = rewardText == null ? "" : rewardText;
        this.openByDefault = openByDefault;
    }

    /** A reward: what was handed out, with the box's text. */
    public static WiredTransactionSuccessComposer reward(List<Term> given, String rewardText, boolean openByDefault) {
        return new WiredTransactionSuccessComposer(TYPE_REWARD, given, rewardText, openByDefault);
    }

    public int getType() {
        return this.type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTransactionSuccessComposer);
        this.response.appendInt(this.type);
        if (this.type == TYPE_REWARD) {
            ChestAirWire.appendRule(this.response, this.rule);
            this.response.appendString(this.rewardText);
            this.response.appendBoolean(this.openByDefault);
        }
        return this.response;
    }
}
