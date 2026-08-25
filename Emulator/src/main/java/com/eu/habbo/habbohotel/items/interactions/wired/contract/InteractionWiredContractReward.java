package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reward Contract ({@code wf_contract_reward}) — configures what the user RECEIVES when a transaction
 * completes (currency credited to the user, sourced from a linked chest pool). Dialog code 111.
 * Pure terms; executed by Init Transaction.
 */
public class InteractionWiredContractReward extends InteractionWiredContract {
    public static final int CODE = 111;

    public InteractionWiredContractReward(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredContractReward(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }
}
