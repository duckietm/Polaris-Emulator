package com.srth.plugin.events;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.events.habboEvents.FurniToggleEvent;
import com.srth.plugin.events.habboEvents.OnLogin;
import com.srth.plugin.events.habboEvents.OnLogout;
import com.srth.plugin.events.habboEvents.onEmulatorLoadItemsManagerEvent;
import com.srth.plugin.events.habboEvents.onUserEnterRoom;
import com.srth.plugin.events.habboEvents.onUserExecuteCommand;
import com.srth.plugin.events.habboEvents.onUserExitRoom;
import com.srth.plugin.events.habboEvents.onUserSavedLookEvent;
import com.srth.plugin.events.habboEvents.furniture.onFurnitureBuildHeightEvent;
import com.srth.plugin.events.habboEvents.furniture.onFurnitureMovedEvent;
import com.srth.plugin.events.habboEvents.furniture.onFurniturePickedUpEvent;
import com.srth.plugin.events.habboEvents.furniture.onFurniturePlacedEvent;
import com.srth.plugin.events.habboEvents.furniture.onFurnitureRotateEvent;
import com.srth.plugin.events.habboEvents.tasksWorks.StepOnTaskTileEvent;
import com.srth.plugin.events.habboEvents.tasksWorks.UserCommandEvento;

public class RegisterHabboEvents {

        public RegisterHabboEvents() {
        }

        public void Init() {
                this.registerEvents();
        }

        private void registerEvents() {
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new OnLogin());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new OnLogout());

                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onUserEnterRoom());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onUserExitRoom());

                // Tasks
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new FurniToggleEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new StepOnTaskTileEvent());

                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new UserCommandEvento());

                // NEWS
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onUserSavedLookEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onFurnitureBuildHeightEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onFurnitureMovedEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onFurniturePickedUpEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onFurnitureRotateEvent());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onFurniturePlacedEvent());

                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE,
                                new onUserExecuteCommand());
                Emulator.getPluginManager().registerEvents(RoleplayWebsocketsRoomsInfo.INSTANCE, new onEmulatorLoadItemsManagerEvent());
        }

}
