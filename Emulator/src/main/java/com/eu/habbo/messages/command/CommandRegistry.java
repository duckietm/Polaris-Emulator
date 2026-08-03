package com.eu.habbo.messages.command;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.rcon.RCONMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport-neutral registry and dispatcher for administrative commands.
 *
 * <p>Historically these commands were only reachable over the RCON TCP listener. To let
 * the same command surface be exposed over a second transport (the CMS HTTP API) without
 * duplicating command logic, lookup + payload parsing + validation + handling now live
 * here. Each transport owns its own concerns (RCON keeps IP allow-listing and rate
 * limiting; the HTTP API adds authentication and its own limits) and simply calls
 * {@link #dispatch(String, String)} once a request has been admitted.
 *
 * <p>The dispatch semantics — command-key normalization, Jakarta Bean Validation via
 * {@link RCONMessage#validate}, and the {@code {status, message}} response envelope — are
 * preserved exactly as the RCON listener implemented them, so existing RCON behavior is
 * unchanged.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CommandRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, Class<? extends RCONMessage>> messages = new HashMap<>();
    private final GsonBuilder gsonBuilder;

    public CommandRegistry() {
        this.gsonBuilder = new GsonBuilder();
        this.gsonBuilder.registerTypeAdapter(RCONMessage.class, new RCONMessage.RCONMessageSerializer());
    }

    /** Command keys are matched with underscores removed and lower-cased. */
    public static String normalize(String key) {
        return key.replace("_", "").toLowerCase();
    }

    public void register(String key, Class<? extends RCONMessage> clazz) {
        this.messages.put(normalize(key), clazz);
    }

    public boolean isRegistered(String key) {
        return key != null && this.messages.containsKey(normalize(key));
    }

    public List<String> getCommands() {
        return new ArrayList<>(this.messages.keySet());
    }

    /**
     * Parses, validates and handles a single command.
     *
     * @param key  the command key (normalized internally)
     * @param body the raw JSON payload for the command's {@code data} object
     * @return the transport-neutral outcome; never {@code null}
     */
    public CommandResult dispatch(String key, String body) {
        Class<? extends RCONMessage> message = key == null ? null : this.messages.get(normalize(key));

        if (message == null) {
            LOGGER.error("Couldn't find: {}", key);
            return CommandResult.unknownCommand();
        }

        try {
            RCONMessage rcon = message.getDeclaredConstructor().newInstance();
            Gson gson = this.gsonBuilder.create();
            Object payload = gson.fromJson(body, rcon.type);
            if (rcon.validate(payload)) {
                rcon.handle(gson, payload);
            }
            LOGGER.info("Handled RCON Message: {}", message.getSimpleName());

            if (Emulator.debugging) {
                LOGGER.debug("RCON Data {} RCON Status {} Message {}", body, rcon.status, rcon.message);
            }

            return CommandResult.of(rcon.status, rcon.message);
        } catch (Exception ex) {
            LOGGER.error("Failed to handle RCONMessage", ex);
        }

        return CommandResult.failed();
    }
}
