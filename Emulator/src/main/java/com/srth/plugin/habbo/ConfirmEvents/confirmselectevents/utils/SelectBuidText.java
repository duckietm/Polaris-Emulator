package com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.utils;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class SelectBuidText {

    public SelectBuidText() {
    }

    public void load() {
        // Buy ingredients
        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.ingredientes.buy.no.currency",
                "No tienes las suficientes monedas para realizar la compra.");
        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.ingredients.buy.success",
                "Acabas de realizar tu compra %br%%br% Ingredientes en tu nevera: %cantidad%");
        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.ingredients.sufic.gotoroom",
                "Tienes: %cantidad% ingredientes en tu nevera. Ahora puedes regresar a tu casa, presiona aca para regresar.");
        RoleplayWebsocketsRoomsInfo.getTexts().register("action_buy.ingredients.process",
                "Comprando %cantidad% ingredientes:");

        // Herramientas
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_herramientas_data",
                "title: Herramientas disponibles; title_confirm: Comprar; items_amount: 2");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_herramientas_item_1",
                "id: 1; title: Llave; descripcion: 1 cash; amount_items: 1; image: llave.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_herramientas_item_2",
                "id: 2; title: Arma ilegal; descripcion: 5 cash; amount_items: 1; image: arma_ilegal.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_herramientas_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_herramientas_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");

        // Electrodomesticos
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_electrodomesticos_orden_select_data",
                "title: Electrodomesticos disponibles; title_confirm: Comprar; items_amount: 1");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_electrodomesticos_orden_select_item_1",
                "id: 1; title: Horno; descripcion: 5 cash; amount_items: 1; image: ktchn_c15_stove_rp_icon.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_electrodomesticos_orden_select_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_electrodomesticos_orden_select_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");

        // Ingredientes items RP
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_data",
                "title: Ingredientes disponibles; title_confirm: Comprar; items_amount: 6");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_1",
                "id: 1; title: Pollo; descripcion: 5 cash; amount_items: 5; image: pollo.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_2",
                "id: 2; title: Bote de agua; descripcion: 3 cash; amount_items: 5; image: bote_agua.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_3",
                "id: 3; title: Vegetales variados; descripcion: 5 cash; amount_items: 6; image: vegetales_variados.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_4",
                "id: 4; title: Ingredientes variados; descripcion: 1 cash; amount_items: 1; image: ingredientes_variados.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_5",
                "id: 5; title: Ingredientes variados; descripcion: 5 cash; amount_items: 6; image: ingredientes_variados.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_item_6",
                "id: 6; title: Ingredientes variados; descripcion: 8 cash; amount_items: 10; image: ingredientes_variados.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_items_ingredientes_orden_select_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");

        // Crear casa
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_createRoom_data",
                "title: Electrodomesticos disponibles; title_confirm: Comprar; items_amount: 1");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_electrodomesticos_orden_select_item_1",
                "id: 1; title: Horno; descripcion: 5 cash; amount_items: 1; image: ktchn_c15_stove_rp_icon.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_createRoom_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_createRoom_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");

        // Canas pescar
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_canaspescar_data",
                "title: Cañas disponibles; title_confirm: Comprar; items_amount: 2");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_canaspescar_item_1",
                "id: 1; title: Caña basica; descripcion: 10 cash; amount_items: 1; image: cana_basica.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_canaspescar_item_2",
                "id: 2; title: Caña intermedia; descripcion: 50 cash; amount_items: 1; image: cana_intermedia.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_canaspescar_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_buy_canaspescar_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");

        // Platillos comprar
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_data",
                "title: Platillos disponibles; title_confirm: Comprar; items_amount: 6");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_1",
                "id: 1; title: Platillo de comida; descripcion: 3 cash; amount_items: 3; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_2",
                "id: 2; title: Platillo de comida; descripcion: 6 cash; amount_items: 6; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_3",
                "id: 3; title: Platillo de comida; descripcion: 10 cash; amount_items: 10; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_4",
                "id: 4; title: Platillo de comida; descripcion: 16 cash; amount_items: 16; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_5",
                "id: 5; title: Platillo de comida; descripcion: 25 cash; amount_items: 25; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_item_6",
                "id: 6; title: Platillo de comida; descripcion: 50 cash; amount_items: 50; image: rp_food_ktchn.png;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_confirm_data",
                "title: Confirma tu compra; descripcion: Confirma antes de comprar %accion%.; title_confirm: Confirmar; title_cancel: Cancelar;");
        RoleplayWebsocketsRoomsInfo.getTexts().register("window_select_buy_platillos_comida_texts",
                "success:Acabas de comprar %cantidad% exitosamente por el precio de %precio% cash.;error:Hubo un error en la compra.");
    }

}
