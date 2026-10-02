package com.eu.habbo.habbohotel.users.customization;

import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import java.sql.SQLException;

public final class UserCustomizationPurchaseService {
    private final UserCustomizationRepository repository;

    public UserCustomizationPurchaseService(UserCustomizationRepository repository) {
        this.repository = repository;
    }

    public PurchaseResult purchaseNickIcon(Habbo habbo, String iconKey) throws SQLException {
        if (habbo.getInventory().getNickIconsComponent().getNickIconByKey(iconKey) != null) {
            return PurchaseResult.failure("You already own this nick icon.");
        }
        var offer = this.repository.findNickIcon(iconKey).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.failure("This nick icon is not available.");
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        habbo,
                        debit(
                                EconomyOperationId.create(
                                        "nick-icon:" + habbo.getHabboInfo().getId() + ":" + iconKey),
                                habbo,
                                offer.pointsType(),
                                offer.points(),
                                "nick_icon_purchase",
                                "inventory.nick_icon.purchase",
                                iconKey));
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Not enough points.");
            }
        }
        UserNickIcon nickIcon = new UserNickIcon(habbo.getHabboInfo().getId(), iconKey);
        return PurchaseResult.nickIcon(nickIcon, offer.points() > 0);
    }

    private static EconomyOperation debit(
            String operationId,
            Habbo habbo,
            int currency,
            int amount,
            String operation,
            String reason,
            String metadata) {
        return new EconomyOperation(
                operationId,
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getId(),
                operation,
                reason,
                currency,
                -amount,
                null,
                metadata);
    }

    public record PurchaseResult(Status status, String message, UserNickIcon nickIcon, boolean currencyChanged) {
        public static PurchaseResult failure(String message) {
            return new PurchaseResult(Status.FAILURE, message, null, false);
        }

        public static PurchaseResult nickIcon(UserNickIcon nickIcon, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", nickIcon, currencyChanged);
        }
    }

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
