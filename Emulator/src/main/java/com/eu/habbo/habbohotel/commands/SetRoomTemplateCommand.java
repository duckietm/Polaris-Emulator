package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class SetRoomTemplateCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetRoomTemplateCommand.class);

    public SetRoomTemplateCommand() {
        super("cmd_setroom_template", Emulator.getTexts().getValue("commands.keys.cmd_setroom_template").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_setroom_template.no_room"),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String yes = Emulator.getTexts().getValue("generic.yes");

        if (params.length < 2 || !params[1].equalsIgnoreCase(yes)) {
            gameClient.getHabbo().alert(
                    Emulator.getTexts().getValue("commands.succes.cmd_setroom_template.verify")
                            .replace("%generic.yes%", yes)
                            .replace("%roomname%", room.getName()));
            return true;
        }

        int newTemplateId = 0;
        int itemsCopied = 0;
        int itemsSkipped = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement insTemplate = connection.prepareStatement(
                    """
                    INSERT INTO room_templates (title, description, thumbnail, sort_order, enabled, \
                    name, room_description, model, password, state, users_max, category, \
                    paper_floor, paper_wall, paper_landscape, thickness_wall, thickness_floor, \
                    moodlight_data, override_model, trade_mode) \
                    (SELECT name, description, '', 0, '1', \
                    name, description, model, password, state, users_max, category, \
                    paper_floor, paper_wall, paper_landscape, thickness_wall, thickness_floor, \
                    moodlight_data, override_model, trade_mode \
                    FROM rooms WHERE id = ?)""",
                    Statement.RETURN_GENERATED_KEYS)) {
                insTemplate.setInt(1, room.getId());
                insTemplate.executeUpdate();
                try (ResultSet keys = insTemplate.getGeneratedKeys()) {
                    if (keys.next()) newTemplateId = keys.getInt(1);
                }
            }

            if (newTemplateId <= 0) {
                gameClient.getHabbo().whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_setroom_template"),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (room.hasCustomLayout()) {
                try (PreparedStatement updLayout = connection.prepareStatement(
                        """
                        UPDATE room_templates t \
                        JOIN room_models_custom c ON c.id = ? \
                        SET t.heightmap = c.heightmap, t.door_x = c.door_x, \
                            t.door_y = c.door_y, t.door_dir = c.door_dir \
                        WHERE t.template_id = ?""")) {
                    updLayout.setInt(1, room.getId());
                    updLayout.setInt(2, newTemplateId);
                    updLayout.executeUpdate();
                }
            }

            try (PreparedStatement insItems = connection.prepareStatement(
                    """
                    INSERT INTO room_templates_items (template_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data) \
                    SELECT ?, i.item_id, i.wall_pos, i.x, i.y, i.z, i.rot, i.extra_data, i.wired_data \
                    FROM items i JOIN items_base ib ON ib.id = i.item_id \
                    WHERE i.room_id = ?""")) {
                insItems.setInt(1, newTemplateId);
                insItems.setInt(2, room.getId());
                itemsCopied = insItems.executeUpdate();
            }

            try (PreparedStatement countTotal = connection.prepareStatement(
                    "SELECT COUNT(*) FROM items WHERE room_id = ?")) {
                countTotal.setInt(1, room.getId());
                try (ResultSet rs = countTotal.executeQuery()) {
                    if (rs.next()) itemsSkipped = Math.max(0, rs.getInt(1) - itemsCopied);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("cmd_setroom_template failed for roomId=" + room.getId(), e);
            gameClient.getHabbo().whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_setroom_template"),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_setroom_template")
                        .replace("%id%", Integer.toString(newTemplateId))
                        .replace("%items%", Integer.toString(itemsCopied))
                        .replace("%skipped%", Integer.toString(itemsSkipped)),
                RoomChatMessageBubbles.ALERT);

        return true;
    }
}
