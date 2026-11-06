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

    // Loaded into a new list and swapped in: the hotel view read the list while a reload had emptied it.
    private volatile ArrayList<NewsWidget> newsWidgets;

    public NewsList() {
        this.newsWidgets = new ArrayList<>();
        this.reload();
    }

    public void reload() {
        ArrayList<NewsWidget> loaded = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM hotelview_news ORDER BY id DESC LIMIT 10")) {
            while (set.next()) {
                loaded.add(new NewsWidget(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }

        this.newsWidgets = loaded;
    }

    public ArrayList<NewsWidget> getNewsWidgets() {
        return this.newsWidgets;
    }
}
