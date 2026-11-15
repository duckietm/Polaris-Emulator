package com.srth.plugin.interactions.vidayhambre.control;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.srth.plugin.interactions.vidayhambre.*;
import com.srth.plugin.interactions.vidayhambre.Teleports.InteractionTeleports;

public class InteractionManagerVidayHambre {

        public void load() {
                this.Interactions();
        }

        public void Interactions() {
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_manager_work_medico",
                                                InteractionManagerJobMedico.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_manager_work_aseo",
                                                InteractionManagerJobAseo.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_manager_job_cafetero",
                                                InteractionManagerJobCafeteros.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_cama_dormir", InteractionCamaDormir.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_cocinar", InteractionCocinar.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_cocinar_nevera",
                                                InteractionNeveraIngredients.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_comido_food", InteractionComidoFood.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_food_comer", InteractionFoodsComer.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_bote_basura", InteractionBoteBasura.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("rp_basurero",
                                                InteractionAseoContenedorBasura.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(
                                                new ItemInteraction("rp_urban_basura", InteractionBasuraUrban.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("teleporttilehome",
                                                InteractionTeleportTileHome.class));

                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("teleports", InteractionTeleports.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("ropero_rp", InteractionRoperoRP.class));
                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("bano_rp", InteractionBanera.class));

                Emulator.getGameEnvironment().getItemManager()
                                .addItemInteraction(new ItemInteraction("camilla_hospital_rp",
                                                InteracionCamillaHospital.class));
        }

}
