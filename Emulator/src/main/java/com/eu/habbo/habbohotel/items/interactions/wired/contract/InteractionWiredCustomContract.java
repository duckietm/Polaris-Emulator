package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Custom Contract add-on ({@code wf_xtra_custom_contract}) — the free-form "power user" contract: an
 * arbitrary mix of PAY / RECEIVE currency terms in one box (superset of payment/reward/trade). Dialog
 * code 113. Same term/persistence shape as the fixed contracts; executed atomically by Init Transaction.
 */
public class InteractionWiredCustomContract extends InteractionWiredContract {
    public static final int CODE = 113;

    public InteractionWiredCustomContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredCustomContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }
}
