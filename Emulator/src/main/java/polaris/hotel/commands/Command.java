package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;

public abstract class Command {

    public final String permission;

    public final String[] keys;

    public Command(String permission, String[] keys) {
        this.permission = permission;
        this.keys = keys;
    }

    public abstract boolean handle(GameClient gameClient, String[] params) throws Exception;
}
