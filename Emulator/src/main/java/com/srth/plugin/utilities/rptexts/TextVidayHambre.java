package com.srth.plugin.utilities.rptexts;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextVidayHambre {

        private static final Logger LOGGER = LoggerFactory.getLogger(TextVidayHambre.class);

        public void load() {
                this.VidayHambre();
        }

        public void VidayHambre() {
                try {

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.commandrp.room.no.permited",
                                        "No esta permitido usar este comando en salas roleplay.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.no.accion.progress.pendiente",
                                        "Ahora mismo no puedes realizar esta accion, intentalo en otro momento.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.no.accion.curacion.pendiente",
                                        "Ahora mismo no puedes comer para recuperar vida, debes esperar a ser curado o deberas comer estando en el hospital.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("seasonal.name.rol", "Cash");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.hospital.true.vida.empty",
                                        "Te quedaste sin vida, fuiste enviado al hospital.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.hospital.true.vida.empty_new",
                                        "Te quedaste sin vida, espera a ser asistido o dirigite al hospital.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.hospital.true_hospital_action",
                                        "Te encuentras en el hospital, espera a ser curado por un personal medico... come platillos de tu inventario o usa las maquinas de alimentos.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.dormir.avise",
                                        "Te estas quedando sin energia, consigue una cama donde dormir.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.dormir.death.avise",
                                        "Moriste debido al cansancio ¡Esta mas alerta la próxima!");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.dormir.empty",
                                        "Ya no tienes energia, consigue una cama donde dormir.....");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.hospital.true.dormir.empty",
                                        "Te quedaste sin energia y moriste, fuiste enviado al hospital.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.hospital.true.vida.success",
                                        "Acabas de recuperar toda tu vida.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.room.actual",
                                        "Ya te encuentras en la sala que quieres ir.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_alert.room.pvp.no.permited",
                                        "No permitido en esta habitacion.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.other.work.active",
                                        "Tienes el empleo de %empleo% activo, desactivalo para poder activar el empleo de %new_empleo%.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.time.interval",
                                        "Debes esperar %total_time% para volver a %descripcion_rol%.");

                        // ARRESTAR COMMAND
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.error.too_far",
                                        "Estas demasiado lejos para curar a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.error.self",
                                        "Espera.. No te puedes curar a ti mismo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.error.not_found",
                                        "No se ha encontrado el usuario %username% para curarlo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.error.no_user",
                                        "No escribiste el nombre de ningun usuario para curar.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.need.active",
                                        "No puedes hacer eso, necesitas tener el trabajo de medico activo.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.need.active",
                                        "No puedes hacer eso, necesitas tener el trabajo de policia activo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.user.vida.suficiente",
                                        "%username%, tiene más de la mitad de vida, por lo que no puede ser curado.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.talk",
                                        "*%user% esta recibiendo tratemiento medico*.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_curar.talk.user.success",
                                        "*Acabo de ser curado por %user%*.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("buy_foods.disabled",
                                        "Se esta procesando una compra reciente. Intentalo en varios segundos...");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_aseo.need.active",
                                        "No puedes hacer eso, necesitas tener el trabajo de aseo activo.");

                        // Maquinas de comer
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.disabled",
                                        "La maquina esta procesando una compra reciente. Intentalo en varios segundos...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.user.shop.process",
                                        "Acabas de abrir la puerta de esta maquina de comida rapida..");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.user.shop.success",
                                        "Realizaste la compra de %COMIDA% por el precio de %COSTO% %TYPE%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.hambre.full",
                                        "No puedes volver a comer, estas lleno.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.desc.buy",
                                        "comprar comida chatarra");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.action.pendiente",
                                        "Comprando comida rapida:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.0",
                                        "una Super Pizza");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.1", "una Pizza");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.2",
                                        "unas Super Papas Fritas");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.3",
                                        "unas Papas Fritas");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.4",
                                        "unos Super Cup Cakes");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("foods_machine.comida.type.6",
                                        "unos Cup Cackes");

                        // WORKS GLOBAL
                        RoleplayWebsocketsRoomsInfo.getTexts().register("works.info.sueldo.success",
                                        "Acabas de recibir %monto% de salario. %br%%br% Esperamos te vaya mejor para la proxima.");

                        // WORK MEDICO
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_medico.user.disabled",
                                        "Acabas de desactivar tu empleo como medico");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_medico.user.enabled", 
                                        "Acabas de activar tu empleo como medico");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_medico.sueldo.increment",
                                        "Acabas acumular %monto% a tu sueldo, para un total de %total% acumulado");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_medico_recibe.jeringa.accion",
                                        "Tomaste de tu inventario una jeringa, la tienes a la mano.");

                        // WORK ASEO
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_aseo.user.disabled",
                                        "Acabas de desactivar tu empleo como personal de aseo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_aseo.user.enabled",
                                        "Acabas de activar tu empleo como personal de aseo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_aseo.sueldo.increment",
                                        "Acabas acumular %monto% a tu sueldo, para un total de %total% acumulado.");

                        // CAFETERO
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_cafetero.user.disabled",
                                        "Acabas de desactivar tu empleo como cafetero.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_cafetero.user.enabled",
                                        "Acabas de activar tu empleo como cafetero.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_cafetero.sueldo.increment",
                                        "Acabas acumular %monto% a tu sueldo, para un total de %total% acumulado.");

                        // Dormir
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_dormir.nombres.suenos",
                                        "No estas sonando con nada...;Estas sonando cosas divertidas...;Estas sonando cosas muy malas...;Estas sonando cosas aterradoras...;Estas sonando con momentos en tu trabajo...;No estas sonando con nada;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("dormir.not.sueño",
                                        "No puedes seguir durmiendo...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("dormir.success",
                                        "Acabas de recuperar toda tu energia, levantate ahora de la cama.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_dormir.text.talk",
                                        "ZzzzzzZZZZz;zzzzZZZZZZzzz; zzZZZzzZZZ; zzZZZZZZZZZ");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.dormir.process",
                                        "Tiempo para despertar:");

                        // Cocinar
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.select",
                                        "Acabas de encender la cocina, y estas pensando que cocinar esta vez...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.select.success",
                                        "Esta vez vas a cocinar: %br% %select%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.select.action",
                                        "Acercate a la nevera para buscar los ingredientes necesarios...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.ingredients.success",
                                        "Acabas de tomar de la nevera los ingredientes necesarios...%br% Ahora acercate a la cocina y comienza a cocinar.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.no.suf.ingredients",
                                        "Te quedaste sin ingredientes variados en tu nevera, ve y compra en el super mercado ahora.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.no.suf.ingredients2",
                                        "Te quedaste sin ingredientes variados, presiona aquí para ir al super mercado y comprar mas.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.success",
                                        "Acabas de cocinar %cantidad% platillos. %br% Puedes encontrarlos en tu inventario.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.success.ingredients",
                                        "Utilizaste %number_ingredientes% ingredientes para realizar este platillo. %br% Ingredientes restantes en tu nevera: %total%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_nevera.ingredients.mano",
                                        "Ya tienes los ingredientes necesarios en mano, acercate a la cocina para preparar tu platillo...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.nombres.platillos",
                                        "Pollo frito; Ensalada de gallina; Guiso de carne; Sopa pre cocida; Comida rapida");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.select.process",
                                        "Seleccionando que cocinar:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_cocinar.process.pendient",
                                        "Cocinando platillos:");

                        // Command comer
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_comer.success",
                                        "Acabas de comerte un plato de comida, te quedan: %cantidad%");

                        // MANZANAS
                        RoleplayWebsocketsRoomsInfo.getTexts().register("comer_objetos.success",
                                        "Acabas de comerte %nombre_comida%, te quedan: %cantidad%");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_comer.hambre.full",
                                        "No puedes seguir comiendo, tu barra de comida esta llena.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_comer.empty",
                                        "No tienes mas platillos en tu inventario para comer.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_comer.hambre.no.permited.tuto",
                                        "Aún no puedes comer de esta forma, primero debe ser de tu inventario normal.");

                        // EVENT ENERGIA
                        RoleplayWebsocketsRoomsInfo.getTexts().register("energia.full.cafe.action",
                                        "No puedes seguir tomando café, tu energía está al máximo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("energia.succes_action",
                                        "¡Tomaste una taza de café y recuperaste %cantidad% de energía!");

                        // Ventanas confirmaciones
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_comer_platillo_data",
                                        "title: Confirma antes de comer; descripcion: Quieres comer este platillo?;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_buy_ingredientes_data",
                                        "title: Confirma tu compra; descripcion: Confirma si quieres comprar 3 ingredientes por el precio de %cantidad% %seasonal_type_name%...;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_buy_foods_machine_data",
                                        "title: Confirmar; descripcion: Confirma si quieres comprar comida rapida por el precio de %cantidad% %seasonal_type_name%..;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_bote_basura_data",
                                        "title: Confirmar; descripcion: Confirma si quieres botar %cantidad% de basura de tu inventario.;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_bote_basura_empty_data",
                                        "title: Confirmar; descripcion: Confirma si quieres recoger %cantidad% de basura de este bote.;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_aseo_contenedor_data",
                                        "title: Confirmar; descripcion: Confirma si quieres botar al contenedor %cantidad% bolsas de basura, recibiras de dinero %cantidad_dinero% cash como recompensa.;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_GotoHospital_data",
                                        "title: Confirmar; descripcion: Te quedaste sin vida, ¿Deseas ir al hospital?;image:vida_vacia.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");

                        // RECORRIDOS DATA
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_exit_recorrido_data",
                                        "title: Confirmar acción; descripcion: ¿Seguro quieres finalizar el recorrido y quedarte en la sala actual?;image:casa.png; title_next:Confirmar; title_ant:Rechazar; color_button_next: green;");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("recorrido_msg_init_taxi",
                                        "[TAXI] Dirigiendo a: [%room_id%] %room_name%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("recorrido_msg_finalice_taxi",
                                        "[TAXI] Deja al ciudadano en la sala actual");

                        // Ultimo msg
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_tutorial_finish_msg_1",
                                        "descripcion: Asegurate de no quedarte sin platillos de comida. Acercate a la Cocina ahora y prepara los platillos que creas necesarios.; img: platillos.png;");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura.no.basure",
                                        "No tienes platillos en tu inventario para tirar a la basura.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_user_basura.no.basure",
                                        "Este bote esta limpio, no contiene basura.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura.success",
                                        "Botaste a la basura %cantidad% platillos de tu inventario.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura.show.cantidad",
                                        "Tienes %cantidad% platillos acumulados en tu basura.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura_empty.success",
                                        "Acabas de recoger %cantidad% de este bote de basura, tienes %cantidad_total% acumulada.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_contenedor_cash.success",
                                        "Acabas de recibir %cantidad% de cash por tu trabajo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_contenedor_empty.success",
                                        "Acabas de botar %cantidad% de basura al contenedor.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_contenedor.error",
                                        "No tienes suficiente basura para botar, tienes %cantidad% bolsas de basuras, recolecta mas...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_contenedor.action_bote_basura",
                                        "Botando %cantidad% bolsas de basura:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_job_no_active",
                                        "No puedes hacer eso, necesitar tener el empleo de aseo activo.");

                        // ASEO
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_aseo_contenedor_process_pendient",
                                        "Recogiendo basura:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "action_basura_urban_contenedor_process_pendient",
                                        "Recogiendo %amount% basura:");

                        // NEW: ACCIONES
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.rechargmunition.process",
                                        "Recargando municion:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.energyspeed.process",
                                        "Activando velocidad de movimiento:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.desequipeequipearm.process",
                                        "Guardando tu arma:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.usebotiquin.process",
                                        "Usando botiquin para regenerar %value% de vida:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.useshield.process",
                                        "Usando %value% de proteccion:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.usetaximap.process",
                                        "El taxi esta por recogerte:");

                        // SHOOTER
                        RoleplayWebsocketsRoomsInfo.getTexts().register("shooter_alert.hospital.true.vida.empty",
                                        "Fuiste asesinado por %username%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("shooter_alert.coofre.success",
                                        "*Encuentra %type% en este cofre*");

                        // MAPA
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type0_name",
                                        "Todas las salas roleplay:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type0_desc",
                                        "Salas roleplay populares:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type1_name",
                                        "Todas las salas roleplay:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type1_desc",
                                        "Estas son todas las salas roleplay disponibles:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type2_name",
                                        "Empleos disponibles:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type2_desc",
                                        "Estos son los empleos disponibles en el hotel, en ellos podras acumular experiencia y obtener mucho cash, moneda que te servira para comprar muchas cosas.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type3_name", "Normales:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type3_desc",
                                        "Estas son las salas roleplay frecuentes y casuales del hotel:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type4_name", "Habitaciones:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.type4_desc",
                                        "Estas son las ultimas habitaciones de los usuarios del hotel:");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("window_mapa.msg_visit_exitosa",
                                        "Tomo un taxi para ir a: %room_name%");

                        // USERS ACTIONS BUTTONS
                        RoleplayWebsocketsRoomsInfo.getTexts().register("user_action.donate.msg.bubble",
                                        "%username% acaba de donarte %amount% cash.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("user_action.youdonate.msg.bubble",
                                        "Acabas de donarle %amount% cash a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("user_action.exitprision.msg.bubble",
                                        "Saca a %username% de prision.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("user_action.emptyexpediente.msg.bubble",
                                        "He vaciado el expediente de %username%.");

                        //////////////////////////////////
                        // EMULATOR CONFIGURACIONES /////
                        Emulator.getTexts().register("commands.keys.cmd_update_rp_config", "update_rp_config");
                        Emulator.getTexts().register("commands.description.cmd_update_rp_config",
                                        "<b>:update_rp_config</b> Actualiza toda la configuración del roleplay.");
                        Emulator.getTexts().register("commands.description.cmd_update_rp_config",
                                        "<b>:update_rp_config</b> Actualiza toda la configuracion del roleplay.");

                        Emulator.getTexts().register("commands.keys.cmd_update_rp_text", "update_rp_text");
                        Emulator.getTexts().register("commands.description.cmd_update_rp_text",
                                        "<b>:update_rp_text</b> Actualiza todos los textos del roleplay.");

                        Emulator.getTexts().register("commands.succes.cmd_update_rp_config",
                                        "<b>Actualizaste las configuraciones roleplay.");
                        Emulator.getTexts().register("commands.succes.cmd_update_rp_text",
                                        "<b>Actualizaste los textos roleplay.");

                        // COMMAND CURAR
                        Emulator.getTexts().register("commands.keys.cmd_curar", "curar");
                        Emulator.getTexts().register("commands.description.cmd_curar",
                                        "<b>:curar</b> [usuario] cura al usuario (debes tener trabajo medico activo)");

                        Emulator.getTexts().register("commands.keys.cmd_comer", "comer");
                        Emulator.getTexts().register("commands.description.cmd_comer",
                                        "<b>:comer</b> (salas roleplay) comerás los platos que tengas en tu inventario para evitar el hambre.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global.success.equip.item",
                                        "Acabas de poner en tu mano este objeto.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("tiempo_dias_horas_minutos",
                                        "%dias% dias, %horas% horas, %minutos% minutos.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("inventory_used_item_max",
                                        "No puedes equipar mas objetos de tu inventario, desequipa alguno que ya tengas y vuelve a intentarlo.");

                        // createRoomRoleplay
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createRoomRoleplay_title",
                                        "¿Quieres crear una nueva sala roleplay?");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createRoomRoleplay_desc",
                                        "Confirma si quieres crear una nueva sala roleplay.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createRoomRoleplay_evento",
                                        "CreateNewRoomRoleplayConfirmed");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createRoomRoleplay_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createRoomRoleplay_cancell",
                                        "Cancelar");

                        // visitRoomTaxiNormal
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_visitRoomTaxiNormal_title",
                                        "¿Quieres llamar un taxi?");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_visitRoomTaxiNormal_desc",
                                        "Confirma si quieres llamar un taxi por el precio de 2 cash para ir a esta sala.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_visitRoomTaxiNormal_evento",
                                        "VisitRoomTaxiNormal");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_visitRoomTaxiNormal_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_visitRoomTaxiNormal_cancell",
                                        "Cancelar");

                        // ConfirmVisitRoomTaxi
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_ConfirmVisitRoomTaxi_title",
                                        "Confirma el recorrido");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_ConfirmVisitRoomTaxi_desc",
                                        "Confirma ahora mismo si quieres comenzar el recorrido hasta esta sala.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_ConfirmVisitRoomTaxi_evento",
                                        "ConfirmVisitRoomTaxi");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_ConfirmVisitRoomTaxi_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_ConfirmVisitRoomTaxi_cancell",
                                        "Cancelar");

                        // createConexionTitlesTeleport
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createConexionTitlesTeleport_title",
                                        "¿Estas seguro que quieres crear la conexion entre estos 2 teleports?");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createConexionTitlesTeleport_desc",
                                        "Confirma si quieres hacerlo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createConexionTitlesTeleport_evento",
                                        "createConexionTitlesTeleport");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createConexionTitlesTeleport_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_createConexionTitlesTeleport_cancell",
                                        "Cancelar");

                        // Prod realiceFunctionPROD
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_realiceFunctionPROD_title",
                                        "¿Quieres realizar esta funcion?");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_realiceFunctionPROD_desc",
                                        "Confirma si quieres realizar esta funcion de prueba.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_realiceFunctionPROD_evento",
                                        "RealiceFunctionTestPROD");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_realiceFunctionPROD_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_realiceFunctionPROD_cancell",
                                        "Cancelar");

                        // confirmBuyCafeCafeteria
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_confirmBuyCafeCafeteria_title",
                                        "¿Quieres comprar 1 taza de café por 5 cash?");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_confirmBuyCafeCafeteria_desc",
                                        "Confirma si quieres comprar una taza de cafe por 5 cash.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_confirmBuyCafeCafeteria_evento",
                                        "successConfirmBuyCafe");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_confirmBuyCafeCafeteria_confirm",
                                        "Confirmar");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("confirm_confirmBuyCafeCafeteria_cancell",
                                        "Cancelar");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_buyCafeSuccess",
                                        "Acabas de comprar %AMOUNT% café por %CANTIDAD% cash.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_buyCafeSuccess_msg",
                                        "*Acaba de comprar %AMOUNT% café por %CANTIDAD% cash*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("avise_cafe_empty",
                                        "¡No hay café disponible! Espera a que el personal prepare más...");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.banera.process",
                                        "Acabas de llenar tu bañera con agua, espera a que se llene para poder usarla...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.banera.success",
                                        "Lograste bañarte y recuperar toda tu energía, ahora puedes salir de la bañera.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_banera.nombres.suenos",
                                        "Llenando la bañera;Agregando sales de baño;Sumergiendote en el agua caliente;Relajandote en la bañera;Disfrutando del baño;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_banera.success.msg",
                                        "Cierra el grifo, se sacude el agua y se siente totalmente renovado e impecable.; Sale de la bañera con la piel fresca y un aroma revitalizante, recuperando toda su higiene.; Finaliza su aseo personal, sintiéndose como nuevo tras un baño reparador.; Se seca rápidamente mientras disfruta de la sensación de limpieza absoluta.; Tras un último enjuague, se siente listo para continuar el día con la higiene al máximo.; Se relaja por última vez bajo el agua antes de dar por terminada su sesión de aseo.;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_banera.progress.msg",
                                        "Abre las llaves dejando que el agua recorra su cuerpo mientras el vapor llena el lugar.; Se enjabona cuidadosamente, eliminando el rastro de suciedad y cansancio del día.; Ajusta la temperatura del agua y se sumerge en una nube de espuma relajante.; Frota su piel con energía mientras el sonido del agua cayendo crea un ambiente tranquilo.; Disfruta del contacto del agua templada, dejando que el jabón haga su trabajo.; Se toma un momento para masajear sus hombros bajo el chorro de agua caliente.;");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_camilla_hospital.progress",
                                        "Recuperando vida en la camilla del hospital; Descansando en la camilla del hospital mientras el personal médico trabaja; Acostado en la camilla del hospital, sintiendo cómo su salud mejora; Descansando en la camilla del hospital, con la esperanza de una pronta recuperación;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("vida.not.full",
                                        "Tu salud y energía ya están completas.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "action_camilla_hospital.progress.energia",
                                        "Descansas en la camilla para recuperar tu energía;El personal médico te ayuda a recuperar fuerzas");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_camilla_hospital.success",
                                        "Acabas de recuperar toda tu vida, levantate ahora de la camilla.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("tutorial.camilla.warning",
                                        "No puedes realizar esta acción, debes completar el tutorial...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.camilla.process",
                                        "Acabas de acostarte en la camilla del hospital, espera mientras te recuperas...");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("banera.not.full",
                                        "No puedes seguir bañandote, tu barra de higiene esta llena.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("tutorial.banera.warning",
                                        "No puedes realizar esta acción, debes completar el tutorial...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.receibed.objets.message.bano",
                                        "Recibiste una banera en tu inventario, colocala en la sala para poder usarla y recuperar tu higiene.");

                        //
                        RoleplayWebsocketsRoomsInfo.getTexts().register("no_cmd_pickall_rp",
                                        "No puedes usar el comando pickall en salas roleplay.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("commands.succes.cmd_empty.cleared",
                                        "¡Inventario limpiado! fueron eliminados todos los items, excepto los de roleplay.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_enter_room_rp_name",
                                        "Entraste a: [%ROOM_ID%] %ROOM_NAME%");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_process_create_room_generic_roleplay",
                                        "Espera mientras creamos su nueva sala roleplay.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_timer_give.cash.success",
                                        "Acabas de recibir %AMOUNT% cash, por jugar durante 10 min.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_action_canceled",
                                        "Cancelaste la accion");

                        RoleplayWebsocketsRoomsInfo.getConfig().register("rank_minimo_permission_create_room", "7");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_tarea_quest_completed",
                                        "Acabas de completar una tarea.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_window",
                                        "PASE DE BATALLA: ROLEPLAY");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_part_1", "MI EXPERIENCIA");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_part_2",
                                        "PREMIOS POR SUBIR DE NIVEL");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_part_3",
                                        "SELECCIONA EL TIPO DE TAREAS");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_part_4",
                                        "TIPO DE TAREAS SELECCIONADAS");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("rp_pase_title_part_5", "LISTADO DE TAREAS");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_give_points_cash",
                                        "Acabas de obtener %AMOUNT% %TYPE%");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_equip_objet",
                                        "*Acaba de equipar su %OBJET_NAME%*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_desequip_objet",
                                        "*Guarda su %OBJET_NAME%*");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura_contenedor.success",
                                        "*Bota al contenedor %cantidad% bolsas de basura*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_bote_basura_calle_empty.success",
                                        "*Recoge %cantidad% bolsas de este bote de basura*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_buy_productos.success",
                                        "*Compra %cantidad% %producto% por %amount% %type%*");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("food_name_global", "comida");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("ingredients_name_global", "ingredientes");

                        // PESCAR
                        RoleplayWebsocketsRoomsInfo.getTexts().register("pescar.cana.break",
                                        "Se ha roto tu cana de pescar %TYPE%, vuelve a comprar una.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("pescar.cana.disabled",
                                        "Necesitas equiparte tu cana para poder pescar.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("pescar.cana.escape_peces",
                                        "*Intenta tirar muy duro pero se escapa el pez*");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cana.use.success.accion",
                                        "Ya tienes tu cana en mano, ahora acercate al lago y presionalo dos veces.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("pescar.cana.no_peces_active",
                                        "No hay peces disponibles, intentalo en unos segundos...");
                        // Acciones
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.pescar.process",
                                        "Lanza la cana muy lejos;Recoge la cuerda;Comienza a halar la cuerda");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.pescar.action_success",
                                        "*Logro pescar %cantidad% peces*; *Hala la cuerda y pesca %cantidad% peces*;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.pescar.action_notsuccess",
                                        "*Se le escapan los peces, no logra pescar*; *Hala la cuerda pero se escapan los peces*;");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.balas_empty",
                                        "Te quedaste sin balas, recarga el cartucho...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.cartucho_municion_use",
                                        "Acabas de recargar 20 balas de este cartucho...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.balas_full",
                                        "Tu cartucho ya esta completo");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("avise.room_block_pvp",
                                        "No puedes usar tu arma en esta sala.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_recibe_cash_empleo.success",
                                        "Acabas de recibir %cantidad% de cash por tu trabajo.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_horario_tarde_delite_avise",
                                        "Acaba de atardecer, en poco sera de noche y las calles se volveran inseguras (Mantente alerta).");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_horario_nocturo_delite_avise",
                                        "Se acaba de hacer de noche, te recomendamos regresar a tu casa, las calles no son seguras.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_combat_daytime_disabled",
                                        "No puedes combatir durante el dia, espera a que sea de tarde/noche.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("handitem_need_used.disabled",
                                        "Necesitas %name_objeto% %descripcion%");

                         // COMMAND GIVE MEDICO
                        Emulator.getTexts().register("cmd_give_medico.keys", "give_medico");
                        Emulator.getTexts().register("commands.description.cmd_give_medico",
                                        "<b>:give_medico</b> [usuario] Le daras trabajo al usuario de medico.");

                } catch (Exception e) {
                        LOGGER.error("[WEBSOCKETS ROOMS INFO] Error al introducir los textos", e);
                }
        }

}
