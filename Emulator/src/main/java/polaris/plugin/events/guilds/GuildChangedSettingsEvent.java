package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;

public class GuildChangedSettingsEvent extends GuildEvent {

    public int state;

    public boolean rights;

    public GuildChangedSettingsEvent(Guild guild, int state, boolean rights) {
        super(guild);
        this.state = state;
        this.rights = rights;
    }
}
