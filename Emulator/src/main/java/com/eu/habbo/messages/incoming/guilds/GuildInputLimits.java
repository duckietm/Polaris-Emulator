package com.eu.habbo.messages.incoming.guilds;

final class GuildInputLimits {
    static final int MAX_GUILD_NAME_LENGTH = 29;
    static final int MAX_GUILD_DESCRIPTION_LENGTH = 254;

    private GuildInputLimits() {
    }

    static boolean isValidGuildName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_GUILD_NAME_LENGTH;
    }

    static boolean isValidGuildDescription(String description) {
        return description != null && description.length() <= MAX_GUILD_DESCRIPTION_LENGTH;
    }
}
