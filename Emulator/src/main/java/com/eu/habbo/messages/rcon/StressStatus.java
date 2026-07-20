package com.eu.habbo.messages.rcon;

import com.eu.habbo.stress.StressRunRegistry;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class StressStatus extends RCONMessage<StressStatus.JSONStressStatus> {
    public StressStatus() {
        super(JSONStressStatus.class);
    }

    @Override
    public void handle(Gson gson, JSONStressStatus json) {
        try {
            this.message = gson.toJson(StressRunRegistry.get().status(json.room_id));
        } catch (IllegalStateException exception) {
            this.status = STATUS_ERROR;
            this.message = exception.getMessage();
        }
    }

    public static class JSONStressStatus {
        @Positive(message = "invalid room")
        public int room_id;
    }
}
