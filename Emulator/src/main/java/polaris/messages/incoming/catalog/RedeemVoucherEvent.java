package polaris.messages.incoming.catalog;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.RedeemVoucherErrorComposer;
import polaris.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import polaris.threading.runnables.ShutdownEmulator;

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
                        this.client.getUser().getUserInfo().getId(), id -> new AtomicInteger())
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
