package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecuteCommand extends RCONMessage<ExecuteCommand.JSONExecuteCommand> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteCommand.class);
    static final int DEFAULT_MAX_COMMAND_LENGTH = 256;
    private static final String DEFAULT_DENIED_PERMISSIONS = String.join(";",
            "cmd_shutdown",
            "cmd_update_config",
            "cmd_update_permissions",
            "cmd_give_rank",
            "cmd_badge",
            "cmd_gift",
            "cmd_credits",
            "cmd_points",
            "cmd_pixels",
            "cmd_massbadge",
            "cmd_masscredits",
            "cmd_massgift",
            "cmd_massduckets",
            "cmd_masspoints",
            "cmd_empty",
            "cmd_empty_bots",
            "cmd_empty_pets",
            "cmd_unload",
            "cmd_ban",
            "cmd_superban",
            "cmd_ip_ban",
            "cmd_machine_ban",
            "cmd_disconnect");


    public ExecuteCommand() {
        super(JSONExecuteCommand.class);
    }

    @Override
    public void handle(Gson gson, JSONExecuteCommand json) {
        try {
            String commandLine = json.command.trim();
            int maxLength = parseMaxCommandLength(Emulator.getConfig().getValue("rcon.execute_command.max_length", String.valueOf(DEFAULT_MAX_COMMAND_LENGTH)));

            if (!commandLine.startsWith(":") || commandLine.length() > maxLength) {
                this.status = STATUS_ERROR;
                this.message = "invalid command";
                return;
            }

            String commandKey = commandKey(commandLine);
            if (commandKey.isEmpty()) {
                this.status = STATUS_ERROR;
                this.message = "invalid command";
                return;
            }

            Command command = CommandHandler.getCommand(commandKey);
            String commandPermission = command != null && command.permission != null ? command.permission : commandKey;

            if (!isAllowed(commandPermission,
                    Emulator.getConfig().getValue("rcon.execute_command.denied_permissions", DEFAULT_DENIED_PERMISSIONS),
                    Emulator.getConfig().getValue("rcon.execute_command.allowed_permissions", ""))) {
                this.status = STATUS_ERROR;
                this.message = "command not allowed";
                return;
            }

            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(json.user_id);

            if (habbo == null) {
                this.status = HABBO_NOT_FOUND;
                return;
            }


            if (!CommandHandler.handleCommand(habbo.getClient(), commandLine)) {
                this.status = STATUS_ERROR;
                this.message = "command failed";
            }
        } catch (Exception e) {
            this.status = STATUS_ERROR;
            LOGGER.error("Caught exception", e);
        }
    }

    static boolean isAllowed(String commandPermission, String deniedPermissions, String allowedPermissions) {
        String normalized = normalize(commandPermission);
        Set<String> allowed = permissionSet(allowedPermissions);
        if (!allowed.isEmpty()) {
            return allowed.contains(normalized);
        }

        return !permissionSet(deniedPermissions).contains(normalized);
    }

    static String commandKey(String commandLine) {
        if (commandLine == null) {
            return "";
        }

        String trimmed = commandLine.trim();
        if (!trimmed.startsWith(":")) {
            return "";
        }

        String withoutPrefix = trimmed.substring(1).trim();
        if (withoutPrefix.isEmpty()) {
            return "";
        }

        return withoutPrefix.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    static int parseMaxCommandLength(String configured) {
        try {
            int parsed = Integer.parseInt(configured);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        return DEFAULT_MAX_COMMAND_LENGTH;
    }

    private static Set<String> permissionSet(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(permissions.split("[;,]"))
                .map(ExecuteCommand::normalize)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String permission) {
        return permission == null ? "" : permission.trim().toLowerCase(Locale.ROOT);
    }

    static class JSONExecuteCommand {

        @Positive(message = "invalid user")
        public int user_id;

        @NotBlank(message = "invalid command")
        @Size(max = 512, message = "invalid command")
        public String command;
    }
}
