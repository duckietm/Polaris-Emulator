package polaris.hotel.economy;

public record EconomyMutationResult(int balanceBefore, int balanceAfter, boolean applied) {}
