package polaris.hotel.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageLayouts;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.layouts.RoomBundleLayout;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class RoomBundleCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBundleCommand.class);

    public RoomBundleCommand() {
        super(
                "cmd_bundle",
                Emulator.getTexts().getValue("commands.keys.cmd_bundle").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int parentId;
        int credits;
        int points;
        int pointsType;

        if (params.length < 5) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_bundle.missing_params"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (Emulator.getGameEnvironment()
                        .getCatalogManager()
                        .getCatalogPage("room_bundle_"
                                + gameClient
                                        .getUser()
                                        .getUserInfo()
                                        .getCurrentRoom()
                                        .getId())
                != null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_bundle.duplicate"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        parentId = Integer.parseInt(params[1]);
        credits = Integer.parseInt(params[2]);
        points = Integer.parseInt(params[3]);
        pointsType = Integer.parseInt(params[4]);

        CatalogPage page = Emulator.getGameEnvironment()
                .getCatalogManager()
                .createCatalogPage(
                        "Room Bundle: "
                                + gameClient
                                        .getUser()
                                        .getUserInfo()
                                        .getCurrentRoom()
                                        .getName(),
                        "room_bundle_"
                                + gameClient
                                        .getUser()
                                        .getUserInfo()
                                        .getCurrentRoom()
                                        .getId(),
                        gameClient.getUser().getUserInfo().getCurrentRoom().getId(),
                        0,
                        CatalogPageLayouts.room_bundle,
                        gameClient.getUser().getUserInfo().getRank().getId(),
                        parentId,
                        CatalogPageType.NORMAL,
                        CatalogPageType.NORMAL);

        if (page instanceof RoomBundleLayout) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO catalog_items (page_id, item_ids, catalog_name, cost_credits, cost_points, points_type ) VALUES (?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, page.getId());
                statement.setString(2, "");
                statement.setString(3, "room_bundle");
                statement.setInt(4, credits);
                statement.setInt(5, points);
                statement.setInt(6, pointsType);
                statement.execute();

                try (ResultSet set = statement.getGeneratedKeys()) {
                    if (set.next()) {
                        try (PreparedStatement stmt =
                                connection.prepareStatement("SELECT * FROM catalog_items WHERE id = ?")) {
                            stmt.setInt(1, set.getInt(1));
                            try (ResultSet st = stmt.executeQuery()) {
                                if (st.next()) {
                                    page.addItem(new CatalogItem(st));
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
            ((RoomBundleLayout) page)
                    .loadItems(gameClient.getUser().getUserInfo().getCurrentRoom());

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_bundle")
                                    .replace("%id%", page.getId() + ""),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
