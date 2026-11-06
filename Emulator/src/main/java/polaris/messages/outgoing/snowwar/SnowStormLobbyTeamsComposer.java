package polaris.messages.outgoing.snowwar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import polaris.hotel.games.snowwar.SnowWarArenaDefinition;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Provisional lobby line-up broadcast during the pre-match countdown so the
 * client can show a "getting ready" screen with the waiting players already
 * split into their teams (Red / Blue), before the arena splash.
 *
 * <p>The real match (and its object ids) only exists once the countdown hits
 * zero, so this carries no object ids - the team split is computed the same
 * round-robin way {@code SnowWarGame} does at creation ({@code index %
 * teamCount}). Player records are written in the SnowWarPlayerData wire shape
 * (objectId, userId, teamId, name, look, gender) with objectId 0 so the client
 * can reuse the existing player parser. The AIR GameLobbyPlayerData skill
 * level (1..30) follows as an optional tail {@code int count, [userId,
 * skillLevel]} after the arena list.
 */
public class SnowStormLobbyTeamsComposer extends MessageComposer {

    private final List<User> players;
    private final int teamCount;
    private final int leaderUserId;
    private final int selectedArenaId;
    private final List<SnowWarArenaDefinition> arenas;
    private final Map<Integer, Integer> skillLevels;
    private final Map<Integer, Integer> teams;

    public SnowStormLobbyTeamsComposer(
            List<User> players,
            int teamCount,
            int leaderUserId,
            int selectedArenaId,
            List<SnowWarArenaDefinition> arenas) {
        this(players, teamCount, leaderUserId, selectedArenaId, arenas, Collections.emptyMap());
    }

    public SnowStormLobbyTeamsComposer(
            List<User> players,
            int teamCount,
            int leaderUserId,
            int selectedArenaId,
            List<SnowWarArenaDefinition> arenas,
            Map<Integer, Integer> skillLevels) {
        this(players, teamCount, leaderUserId, selectedArenaId, arenas, skillLevels, Collections.emptyMap());
    }

    /** {@code teams}: userId to team, as the match will split them (SnowWarTeams); empty = round robin. */
    public SnowStormLobbyTeamsComposer(
            List<User> players,
            int teamCount,
            int leaderUserId,
            int selectedArenaId,
            List<SnowWarArenaDefinition> arenas,
            Map<Integer, Integer> skillLevels,
            Map<Integer, Integer> teams) {
        this.teams = teams;
        this.players = players;
        this.teamCount = teamCount;
        this.leaderUserId = leaderUserId;
        this.selectedArenaId = selectedArenaId;
        this.arenas = arenas;
        this.skillLevels = skillLevels;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormLobbyTeamsComposer);

        this.response.appendInt(this.teamCount);
        this.response.appendInt(this.players.size());

        int index = 0;
        for (User user : this.players) {
            this.response.appendInt(0); // objectId - not assigned until match creation
            this.response.appendInt(user.getUserInfo().getId());
            this.response.appendInt(this.teams.getOrDefault(user.getUserInfo().getId(), index % this.teamCount));
            this.response.appendString(user.getUserInfo().getUsername());
            this.response.appendString(user.getUserInfo().getLook());
            this.response.appendString(user.getUserInfo().getGender().name().toUpperCase());
            index++;
        }

        this.response.appendInt(this.leaderUserId);
        this.response.appendInt(this.selectedArenaId);
        this.response.appendInt(this.arenas.size());
        for (SnowWarArenaDefinition arena : this.arenas) {
            this.response.appendInt(arena.id());
            this.response.appendString(arena.name());
            this.response.appendBoolean(arena.official());
        }

        this.response.appendInt(this.players.size());
        for (User user : this.players) {
            this.response.appendInt(user.getUserInfo().getId());
            this.response.appendInt(
                    this.skillLevels.getOrDefault(user.getUserInfo().getId(), 1));
        }

        return this.response;
    }
}
