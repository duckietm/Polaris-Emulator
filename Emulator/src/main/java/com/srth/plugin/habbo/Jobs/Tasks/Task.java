package com.srth.plugin.habbo.Jobs.Tasks;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Task {
    private int id;
    private String job_name;
    private String type;
    private String type_data;
    private String action_name;
    private String title;
    private String description;
    private String image;
    private int xp;
    private int process_required;

    public Task(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.job_name = set.getString("job_name");
        this.type = set.getString("type");
        this.type_data = set.getString("type_data");
        this.action_name = set.getString("action_name");
        this.image = set.getString("image");
        this.title = set.getString("title");
        this.description = set.getString("description");
        this.xp = set.getInt("xp");
        this.process_required = set.getInt("process_required");
    }

    public String getJobName(){
        return job_name;
    }
    
    public int getId() {
        return id;
    }

    public String getName() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getTypeData() {
        return type_data;
    }

    public String getActionName() {
        return action_name;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }
    
    public int getXP() {
        return xp;
    }

    public int getProcessRequired(){
        return process_required;
    }

}
