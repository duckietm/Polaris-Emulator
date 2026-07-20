package com.eu.habbo.messages.rcon;

import com.eu.habbo.stress.StressRunRegistry;
import com.eu.habbo.stress.StressScenario;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StressStart extends RCONMessage<StressStart.JSONStressStart> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StressStart.class);

    public StressStart() {
        super(JSONStressStart.class);
    }

    @Override
    public void handle(Gson gson, JSONStressStart json) {
        try {
            StressScenario scenario = new StressScenario(
                    json.room_id,
                    json.bots,
                    json.items,
                    json.rollers,
                    json.wired_stacks,
                    json.wired_events_per_second,
                    json.item_id,
                    json.chat_per_second,
                    json.duration_seconds,
                    json.seed,
                    json.movement);
            this.message = gson.toJson(StressRunRegistry.get().start(scenario));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            this.status = STATUS_ERROR;
            this.message = exception.getMessage();
        } catch (Exception exception) {
            this.status = SYSTEM_ERROR;
            this.message = "failed to start stress run";
            LOGGER.error("Failed to start stress run", exception);
        }
    }

    public static class JSONStressStart {
        @Positive(message = "invalid room")
        public int room_id;

        @PositiveOrZero(message = "invalid bot count")
        public int bots;

        @PositiveOrZero(message = "invalid item count")
        public int items;

        @PositiveOrZero(message = "invalid roller count")
        public int rollers;

        @PositiveOrZero(message = "invalid wired stack count")
        public int wired_stacks;

        @PositiveOrZero(message = "invalid wired event rate")
        public int wired_events_per_second;

        @PositiveOrZero(message = "invalid item id")
        public int item_id;

        @PositiveOrZero(message = "invalid chat rate")
        public int chat_per_second;

        @PositiveOrZero(message = "invalid duration")
        public int duration_seconds;

        public long seed;
        public boolean movement;
    }
}
