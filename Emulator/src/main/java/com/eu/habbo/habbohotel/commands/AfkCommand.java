package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * :afk — bascule manuellement l'etat "absent".
 *
 * L'avatar prend la pose endormie, visible par toute la salle, sans attendre
 * le delai d'inactivite automatique. Une seconde utilisation de la commande
 * reveille l'avatar ; tout deplacement ou message le reveille egalement, via
 * le mecanisme d'inactivite deja en place.
 *
 * Aucune permission requise : la commande est ouverte a tous les joueurs.
 *
 * Les libelles sont lus dans emulator_texts (migration
 * V20260824200000__afk_command_texts.sql). getValueQuietly evite de journaliser
 * une erreur si la migration n'a pas encore ete appliquee : les valeurs par
 * defaut ci-dessous prennent alors le relais.
 */
public class AfkCommand extends Command {

    private static final String DEFAULT_KEYS = "afk;absent;away";
    private static final String DEFAULT_AWAY =
            "Vous etes maintenant absent. Tapez :afk pour revenir.";
    private static final String DEFAULT_BACK = "Bon retour !";

    public AfkCommand() {
        super(
                null,
                Emulator.getTexts()
                        .getValueQuietly("commands.keys.cmd_afk", DEFAULT_KEYS)
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo habbo = gameClient.getHabbo();

        if (habbo == null || habbo.getRoomUnit() == null) {
            return true;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (habbo.getRoomUnit().isIdle()) {
            room.unIdle(habbo);
            habbo.whisper(
                    Emulator.getTexts()
                            .getValueQuietly("commands.generic.cmd_afk.back", DEFAULT_BACK),
                    RoomChatMessageBubbles.NORMAL);
        } else {
            room.idle(habbo);
            habbo.whisper(
                    Emulator.getTexts()
                            .getValueQuietly("commands.generic.cmd_afk.away", DEFAULT_AWAY),
                    RoomChatMessageBubbles.NORMAL);
        }

        return true;
    }
}
