package polaris.hotel.hotelview;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NewsWidget {

    private final int id;

    private final String title;

    private final String message;

    private final String buttonMessage;

    private final int type;

    private final String link;

    private final String image;

    public NewsWidget(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.title = set.getString("title");
        this.message = set.getString("text");
        this.buttonMessage = set.getString("button_text");
        this.type = set.getString("button_type").equals("client") ? 1 : 0;
        this.link = set.getString("button_link");
        this.image = set.getString("image");
    }

    public int getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public String getMessage() {
        return this.message;
    }

    public String getButtonMessage() {
        return this.buttonMessage;
    }

    public int getType() {
        return this.type;
    }

    public String getLink() {
        return this.link;
    }

    public String getImage() {
        return this.image;
    }
}
