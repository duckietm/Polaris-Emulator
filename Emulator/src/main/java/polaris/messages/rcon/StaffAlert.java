package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import polaris.Emulator;

public class StaffAlert extends RCONMessage<StaffAlert.JSON> {
    public StaffAlert() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        Emulator.getGameEnvironment().getUserManager().staffAlert(json.message);
    }

    static class JSON {

        @NotBlank(message = "invalid message")
        @Size(max = 4096, message = "invalid message")
        public String message;
    }
}
