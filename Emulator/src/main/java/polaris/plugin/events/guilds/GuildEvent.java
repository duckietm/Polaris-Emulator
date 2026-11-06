package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.plugin.Event;

public abstract class GuildEvent extends Event {

    public final Guild guild;

    public GuildEvent(Guild guild) {
        this.guild = guild;
    }
}
