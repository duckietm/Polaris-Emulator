package polaris.hotel.users.custombadge;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomBadge {

    private final int id;
    private final int userId;
    private final String badgeId;
    private String badgeName;
    private String badgeDescription;
    private final int dateCreated;
    private int dateEdit;

    public CustomBadge(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.userId = set.getInt("user_id");
        this.badgeId = set.getString("badge_id");
        this.badgeName = set.getString("badge_name");
        this.badgeDescription = set.getString("badge_description");
        this.dateCreated = set.getInt("date_created");
        this.dateEdit = set.getInt("date_edit");
    }

    public CustomBadge(
            int id,
            int userId,
            String badgeId,
            String badgeName,
            String badgeDescription,
            int dateCreated,
            int dateEdit) {
        this.id = id;
        this.userId = userId;
        this.badgeId = badgeId;
        this.badgeName = badgeName;
        this.badgeDescription = badgeDescription;
        this.dateCreated = dateCreated;
        this.dateEdit = dateEdit;
    }

    public int getId() {
        return this.id;
    }

    public int getUserId() {
        return this.userId;
    }

    public String getBadgeId() {
        return this.badgeId;
    }

    public String getBadgeName() {
        return this.badgeName;
    }

    public String getBadgeDescription() {
        return this.badgeDescription;
    }

    public int getDateCreated() {
        return this.dateCreated;
    }

    public int getDateEdit() {
        return this.dateEdit;
    }

    public void setBadgeName(String badgeName) {
        this.badgeName = badgeName;
    }

    public void setBadgeDescription(String badgeDescription) {
        this.badgeDescription = badgeDescription;
    }

    public void setDateEdit(int dateEdit) {
        this.dateEdit = dateEdit;
    }
}
