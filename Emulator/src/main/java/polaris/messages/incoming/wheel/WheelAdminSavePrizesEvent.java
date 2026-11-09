package polaris.messages.incoming.wheel;

import java.util.HashSet;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.wheel.WheelManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wheel.WheelAdminPrizesComposer;
import polaris.messages.outgoing.wheel.WheelDataComposer;

public class WheelAdminSavePrizesEvent extends MessageHandler {
    public static final String PERMISSION_KEY = "acc_wheeladmin";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null || !this.client.getUser().hasPermission(PERMISSION_KEY)) {
            return;
        }

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();

        int count = this.packet.readInt();
        if (count <= 0 || count > WheelManager.MAX_PRIZES_PER_SAVE) return;

        // The client sends the full authoritative list of prizes in display
        // order. id <= 0 means "insert a new prize"; any existing prize whose
        // id is absent from this list was removed in the editor and gets
        // soft-disabled below.
        Set<Integer> keptIds = new HashSet<>();
        int failedSaves = 0;

        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            String type = this.packet.readString();
            String value = this.packet.readString();
            int amount = this.packet.readInt();
            int pointsType = this.packet.readInt();
            int weight = this.packet.readInt();
            String label = this.packet.readString();
            int savedId = wheel.savePrize(id, type, value, amount, pointsType, weight, label, i);
            if (savedId > 0) keptIds.add(savedId);
            else failedSaves++;
        }

        // A prize that could not be saved is missing from keptIds: disabling "everything else" would then switch it
        // off too, and every prize when the database is down. The removals wait for a save that went through.
        if (failedSaves == 0) {
            wheel.disablePrizesNotIn(keptIds);
        }
        wheel.reload();

        this.client.sendResponse(new WheelAdminPrizesComposer(wheel.getPrizes()));
        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(this.client.getUser().getUserInfo().getId()),
                wheel.getSpinCost(),
                wheel.getSpinCostType(),
                wheel.getPrizes()));
    }
}
