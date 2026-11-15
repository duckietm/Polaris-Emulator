package com.srth.plugin.events.habboEvents.tasksWorks;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.PolicesManager;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;

public class StepOnTaskTileEvent implements EventListener {

    @EventHandler
    public static void onWalk(UserTakeStepEvent ev) throws SQLException {
        if (ev.isCancelled())
            return;

        new WorkSystemLevelManager().getReceibedStepOnTileEvent(ev);
        PolicesManager.getReceibedStepOnTileEvent(ev);

        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().eventStepOnTileEvent(ev);

    }
}
