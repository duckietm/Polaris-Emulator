package com.srth.plugin.interactions.hackercajeros.control;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.srth.plugin.interactions.hackercajeros.*;
import com.srth.plugin.interactions.hackercajeros.wireds.conditions.*;


public class InteractionManagerHackerCajeros {
    public void load(){
        this.Interactions();
    }

    public void Interactions(){
        
        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("wf_cnd_habbo_its_encarceled",  WiredConditionHabboItsEncarceled.class));
        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("wf_cnd_habbo_its_not_encarceled",  WiredConditionHabboItsNotEncarceled.class));

        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("wf_cnd_habbo_its_police",  WiredConditionHabboItsPolice.class));
        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("wf_cnd_habbo_its_not_police",  WiredConditionHabboItsNotPolice.class));

        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("manager_police",  InteractionManagerJobPolice.class));
        Emulator.getGameEnvironment().getItemManager().addItemInteraction(new ItemInteraction("report_police",  InteractionReportWorkPolice.class));
    }

}
