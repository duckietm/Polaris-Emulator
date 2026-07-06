package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Trade Contract ({@code wf_contract_trade}) — an atomic exchange: the user PAYS one side and RECEIVES
 * the other in a single transaction (cost in / return out, both committed together or not at all).
 * Dialog code 112. Pure terms; executed atomically by Init Transaction.
 */
public class InteractionWiredContractTrade extends InteractionWiredContract {
    public static final int CODE = 112;

    public InteractionWiredContractTrade(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredContractTrade(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }
}
