package com.eu.habbo.messages.rcon;

import com.eu.habbo.stress.StressRunRegistry;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class StressStop extends RCONMessage<StressStop.JSONStressStop> {
    public StressStop() {
        super(JSONStressStop.class);
    }

    @Override
    public void handle(Gson gson, JSONStressStop json) {
        try {
            this.message = gson.toJson(StressRunRegistry.get().stop(json.room_id));
        } catch (IllegalStateException exception) {
            this.status = STATUS_ERROR;
            this.message = exception.getMessage();
        }
    }

    public static class JSONStressStop {
        @Positive(message = "invalid room")
        public int room_id;
    }
}
