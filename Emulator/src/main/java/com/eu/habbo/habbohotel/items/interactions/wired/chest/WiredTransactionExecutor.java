package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates and applies wired contract terms atomically for {@code WiredEffectInitTransaction}.
 */
public final class WiredTransactionExecutor {
    private WiredTransactionExecutor() {
    }

    public static boolean execute(Habbo habbo, Room room, List<InteractionWiredContract> contracts) {
        if (habbo == null || room == null) {
            return false;
        }
        if (contracts == null || contracts.isEmpty()) {
            return true;
        }

        List<PendingChange> pending = new ArrayList<>();

        for (InteractionWiredContract contract : contracts) {
            if (contract == null) {
                continue;
            }
            InteractionWiredChest chest = contract.resolveLinkedChest(room);

            for (ContractTerm term : contract.getTerms()) {
                if (term.amount <= 0) {
                    continue;
                }

                if (term.isCurrency()) {
                    if (!validateCurrency(habbo, chest, term, pending)) {
                        return false;
                    }
                } else if (term.isFurni()) {
                    if (!validateFurni(habbo, chest, term, pending)) {
                        return false;
                    }
                }
            }
        }

        for (PendingChange change : pending) {
            change.apply();
        }

        for (InteractionWiredContract contract : contracts) {
            InteractionWiredChest chest = contract.resolveLinkedChest(room);
            if (chest != null) {
                chest.persistContents();
            }
        }

        return true;
    }

    private static boolean validateCurrency(Habbo habbo, InteractionWiredChest chest, ContractTerm term, List<PendingChange> pending) {
        if (term.direction == ContractTerm.DIR_PAY) {
            if (!ChestWiredCurrencyUtil.has(habbo, term.currencyType, term.amount)) {
                return false;
            }
            if (chest != null) {
                pending.add(PendingChange.chestAdd(chest, term.currencyType, term.amount));
            }
            pending.add(PendingChange.userTake(habbo, term.currencyType, term.amount));
            return true;
        }

        if (chest != null) {
            if (chest.getContents().count(ChestStorage.KIND_CURRENCY, term.currencyType) < term.amount) {
                return false;
            }
            pending.add(PendingChange.chestTake(chest, term.currencyType, term.amount));
        }
        pending.add(PendingChange.userGive(habbo, term.currencyType, term.amount));
        return true;
    }

    private static boolean validateFurni(Habbo habbo, InteractionWiredChest chest, ContractTerm term, List<PendingChange> pending) {
        if (term.baseItemId <= 0) {
            return false;
        }

        if (term.direction == ContractTerm.DIR_PAY) {
            if (ChestWiredFurniUtil.countInInventory(habbo, term.wallItem, term.baseItemId, term.legacyPosterId) < term.amount) {
                return false;
            }
            pending.add(PendingChange.userFurniTake(habbo, chest, term));
            return true;
        }

        if (chest != null) {
            if (chest.getContents().count(ChestStorage.KIND_FURNI, term.baseItemId) < term.amount) {
                return false;
            }
            pending.add(PendingChange.chestFurniGive(habbo, chest, term));
            return true;
        }

        pending.add(PendingChange.userFurniMint(habbo, term));
        return true;
    }

    private static final class PendingChange {
        private enum Kind {
            USER_TAKE, USER_GIVE, CHEST_TAKE, CHEST_ADD,
            USER_FURNI_TAKE, CHEST_FURNI_GIVE, USER_FURNI_MINT
        }

        private final Kind kind;
        private final Habbo habbo;
        private final InteractionWiredChest chest;
        private final int currencyType;
        private final int amount;
        private final ContractTerm furniTerm;
        private List<ChestFurniStoredItem> furniItems;

        private PendingChange(Kind kind, Habbo habbo, InteractionWiredChest chest, int currencyType, int amount, ContractTerm furniTerm) {
            this.kind = kind;
            this.habbo = habbo;
            this.chest = chest;
            this.currencyType = currencyType;
            this.amount = amount;
            this.furniTerm = furniTerm;
        }

        static PendingChange userTake(Habbo habbo, int currencyType, int amount) {
            return new PendingChange(Kind.USER_TAKE, habbo, null, currencyType, amount, null);
        }

        static PendingChange userGive(Habbo habbo, int currencyType, int amount) {
            return new PendingChange(Kind.USER_GIVE, habbo, null, currencyType, amount, null);
        }

        static PendingChange chestTake(InteractionWiredChest chest, int currencyType, int amount) {
            return new PendingChange(Kind.CHEST_TAKE, null, chest, currencyType, amount, null);
        }

        static PendingChange chestAdd(InteractionWiredChest chest, int currencyType, int amount) {
            return new PendingChange(Kind.CHEST_ADD, null, chest, currencyType, amount, null);
        }

        static PendingChange userFurniTake(Habbo habbo, InteractionWiredChest chest, ContractTerm term) {
            return new PendingChange(Kind.USER_FURNI_TAKE, habbo, chest, 0, 0, term);
        }

        static PendingChange chestFurniGive(Habbo habbo, InteractionWiredChest chest, ContractTerm term) {
            return new PendingChange(Kind.CHEST_FURNI_GIVE, habbo, chest, 0, 0, term);
        }

        static PendingChange userFurniMint(Habbo habbo, ContractTerm term) {
            return new PendingChange(Kind.USER_FURNI_MINT, habbo, null, 0, 0, term);
        }

        void apply() {
            switch (this.kind) {
                case USER_TAKE:
                    ChestWiredCurrencyUtil.take(this.habbo, this.currencyType, this.amount);
                    break;
                case USER_GIVE:
                    ChestWiredCurrencyUtil.give(this.habbo, this.currencyType, this.amount);
                    break;
                case CHEST_TAKE:
                    this.chest.getContents().take(ChestStorage.KIND_CURRENCY, this.currencyType, this.amount);
                    break;
                case CHEST_ADD:
                    this.chest.getContents().add(ChestStorage.KIND_CURRENCY, this.currencyType, this.amount);
                    break;
                case USER_FURNI_TAKE:
                    this.furniItems = ChestWiredFurniUtil.takeFromInventory(
                            this.habbo, this.furniTerm.wallItem, this.furniTerm.baseItemId,
                            this.furniTerm.legacyPosterId, this.furniTerm.amount);
                    if (this.chest != null && this.furniItems != null && !this.furniItems.isEmpty()) {
                        ChestWiredFurniUtil.depositToChest(this.chest, this.furniItems);
                    }
                    break;
                case CHEST_FURNI_GIVE:
                    if (this.furniTerm != null) {
                        ChestWiredFurniUtil.giveFromChestByType(
                                this.habbo, this.chest, this.furniTerm.wallItem, this.furniTerm.baseItemId,
                                this.furniTerm.legacyPosterId, this.furniTerm.amount);
                    }
                    break;
                case USER_FURNI_MINT:
                    if (this.furniTerm != null) {
                        ChestWiredFurniUtil.mintToInventory(
                                this.habbo, this.furniTerm.wallItem, this.furniTerm.baseItemId, this.furniTerm.amount);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
