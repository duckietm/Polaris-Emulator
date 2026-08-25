package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Payment Contract ({@code wf_contract_payment}) — configures what the triggering user must PAY for a
 * transaction to pass (currency debited from the user, optionally deposited into a linked chest).
 * Dialog code 110. Pure terms; executed by Init Transaction.
 */
public class InteractionWiredContractPayment extends InteractionWiredContract {
    public static final int CODE = 110;

    public InteractionWiredContractPayment(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredContractPayment(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }
}
