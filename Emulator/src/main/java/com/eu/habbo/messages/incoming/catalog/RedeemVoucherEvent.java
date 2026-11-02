package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.RedeemVoucherErrorComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RedeemVoucherEvent extends MessageHandler {
    /** Voucher codes one user may try in ten minutes: nothing counted the wrong ones, so codes could be guessed. */
    static final int MAX_ATTEMPTS = 10;

    private static final Map<Integer, AtomicInteger> ATTEMPTS = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .<Integer, AtomicInteger>build()
            .asMap();

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (ShutdownEmulator.timestamp > 0) {
            this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                    (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            return;
        }

        String voucherCode = this.packet.readString();

        int attempts = ATTEMPTS.computeIfAbsent(
                        this.client.getHabbo().getHabboInfo().getId(), id -> new AtomicInteger())
                .incrementAndGet();
        if (attempts > MAX_ATTEMPTS) {
            this.client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
            return;
        }

        if (voucherCode.contains(" ")) {
            this.client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
            return;
        }

        Emulator.getGameEnvironment().getCatalogManager().redeemVoucher(this.client, voucherCode);
    }
}
