package polaris.hotel.hotelview;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class NewsList {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewsList.class);

    private final ArrayList<NewsWidget> newsWidgets;

    public NewsList() {
        this.newsWidgets = new ArrayList<>();
        this.reload();
    }

    public void reload() {
        synchronized (this.newsWidgets) {
            this.newsWidgets.clear();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT * FROM hotelview_news ORDER BY id DESC LIMIT 10")) {
                while (set.next()) {
                    this.newsWidgets.add(new NewsWidget(set));
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public ArrayList<NewsWidget> getNewsWidgets() {
        return this.newsWidgets;
    }
}
