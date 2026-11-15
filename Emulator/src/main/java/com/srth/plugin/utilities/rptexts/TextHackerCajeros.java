package com.srth.plugin.utilities.rptexts;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextHackerCajeros {

        private static final Logger LOGGER = LoggerFactory.getLogger(TextHackerCajeros.class);

        public void load() {
                this.HackerCajeros();
        }

        public void HackerCajeros() {
                try {
                        ////////////////////////////////////////
                        // Registro de textos para el roleplay
                        ///////////////////////////////////////

                        Emulator.getTexts().register("seasonal.name.2", "Cash");
                        Emulator.getTexts().update("seasonal.name.2", "Cash");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("seasonal.name.rol", "Cash");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("seasonal.generic.redeemed",
                                        "Canjeaste %amount% %type% que tenias en tu inventario.");

                        ///////////////////////
                        // LLAVE HACK CAJEROS
                        ///////////////////////
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero_tablet.tablet.success",
                                        "Obtuviste una llave para forzar cajeros.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero_tablet.tablet.success.accion",
                                        "Ya tienes tu llave en mano, ahora puedes forzar cajeros.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero_tablet.tablet.success.accion.2",
                                        "Acercate a un cajero disponible, y presionalo 2 veces.");

                        ///////////////////////
                        // HACK CAJEROS
                        ///////////////////////
                        RoleplayWebsocketsRoomsInfo.getTexts().register("forzar.cajero.delito", "Robo de cajero");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.inicio",
                                        "Preparando su llave para forzar este cajero y extraer una carga monetaria.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.success",
                                        "Acabo de forzar este cajero;Cajero forzado exitosamente;*Toma su llave y logra forzar este cajero*; Estoy a otro level. Acabe de forzar este cajero...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.nosuccess",
                                        "Vaya... No has logrado forzar este cajero;");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.cajero.disabled",
                                        "Este cajero ya fue forzado, intentalo en unos minutos.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.tablet.disabled",
                                        "Necesitas una llave para poder forzar cajeros.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.notif.success",
                                        "Cajero forzado exitosamente.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.notif.msg.success",
                                        "Cajero forzado exitosamente.;Acabo de forzar este cajero;Habilidades como ladron de cajeros mejoradas, acabe de saquear este cajero.;Cajero saqueado exitosamente;Cajero forzado exitosamente, soy un experto en esto.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.notif.msg.nosuccess",
                                        "No pude saquear este cajero; No tuve suerte para forzar este cajero; Vaya, no he logrado forzar este cajero.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero.hackeo.delito",
                                        "Ladron de cajero");

                        // GLOBAL
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.error.not_found",
                                        "No se ha encontrado el usuario %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.error.no_user",
                                        "No escribiste el nombre de ningun usuario.");

                        // ROBAR
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_robar.pistola.disabled",
                                        "Necesitas equipar tu arma ilegal para poder realizar esta acción.");

                        // CURAR
                        RoleplayWebsocketsRoomsInfo.getTexts().register("necesited_item_for_used",
                                        "Necesitas equipar %NAME_OBJETO% para poder realizar esta acción.");

                        //////////////////////////
                        // HACK CAJEROS DORADOS
                        //////////////////////////
                        RoleplayWebsocketsRoomsInfo.getTexts().register("hack_cajero_dorado.hackeo.success",
                                        "Acabo de forzar un cajero dorado;Cajero dorado forzado exitosamente;Buaah force este cajero dorado;Estoy a otro level. Acabe de saquear este cajero dorado...");

                        //////////
                        // GLOBAL
                        //////////
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.porcentaje.exito",
                                        "Conseguiste un %porcentaje%% de %cantidad_total%%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.notif.money.cash",
                                        "Obtuviste %monto% %type%, revisa ahora tu inventario.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.time.interval",
                                        "Debes esperar %total_time% segundos para volver a intentar %descripcion_rol%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.vip.disabled",
                                        "Necesitas ser HNUBE PRIME para poder realizar esta acción.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.level_increment.warning",
                                        "Error, no puede haber ningun objeto o usuario delante del furni, por ende, no te entregara puntos de aprendizaje.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "interactions_global_level_increment.time.espera",
                                        "Espera %total_time% segundos, para obtener un punto de aprendizaje.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "interactions_global_level_increment.level.info",
                                        "Tu nivel actual es %level_actual%/%level_final%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global_level_increment.stats",
                                        "Llevas %total_process%/%total_required% puntos de aprendizaje para subir de nivel.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global_level_increment.next",
                                        "Obtuviste un punto de aprendizaje, llevas un total de %total_process%/%total_required% para subir de nivel.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global_level_next.success",
                                        "Enhorabuena, tu nivel ha incrementado %total_level%/%level_final%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global_delito_user",
                                        "%username% cometio un delito, arrestalo ahora. Descripcion: %descripcion%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("global_work_active.delito.disabled",
                                        "No puedes hacer eso, tienes un trabajo activo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interactions_global.delito.reporting.polices",
                                        "Los policias activos han recibido un reporte de tu delito, ahora podran arrestarte.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.commandrp.room.no.permited",
                                        "No esta permitido usar este comando en salas roleplay.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.commandrp.room.no.rp",
                                        "No esta permitido usar este comando en salas que no son de roleplay.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_global.other.work.active",
                                        "No puedes obtener este trabajo ya que tienes el empleo de %empleo% activo. Desactivalo e intentalo de nuevo.");

                        // RANKING
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interaction_global_rankings.info",
                                        "Estos son los usuarios con mas %accion_descripcion%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interaction_global_rankings.my.puntuacion",
                                        "Tus robos de cajeros exitosos son: %cantidad%");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("interaction_global_rankings.time.espera",
                                        "La premiacion sera entregada en %total_time%.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("interaction_global_rankings.premio.success",
                                        "Acabas de recibir %monto% %name_moneda% por haber quedado en el lugar #%puesto% en el ranking de %interaction%. %br%%br% El premio puede variar para la proxima.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.talk",
                                        "*%user% ha sido neutralizado con un arma electrica*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.talk.2",
                                        "*Se lleva a %user% a prision*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.talk.arma.avised",
                                        "Te quedaste sin un arma, acercate a la cartelera y realiza tu reporte para conseguir otra.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.no.permited",
                                        "No puedes arrestar a %username%, su expendiente esta limpio.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.no.enable.active",
                                        "*Intenta arrestar a %username%, pero se da cuenta que se ha quedado sin un arma*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.user.alert.1",
                                        "Acabas de ser arrestado y fuiste enviado a prision.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.user.alert.2",
                                        "Acercate a una roca y presionala dos veces para cumplir tu labor social..%br%%br% Deberas realizar esta accion cada vez que seas arrestado para poder salir de prision.");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.success.1",
                                        "Acabas de hacer un arresto.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.success.2",
                                        "Acercate a la base policial y encuentra la cartelera de reportes.%br%%br%Una vez la encuentres, deberas comenzar a hacer tu reporte del arresto para incrementar tu sueldo.");

                        // FORZANDO PUERTA DE PRISION
                        // FORZANDO PUERTA DE PRISION
                        // FORZANDO PUERTA DE PRISION
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.init_1",
                                        "¡Comenzaste a forzar la puerta para intentar huir de prisión!...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.init_2",
                                        "Debes estar atento, si un poli te pilla podria aumentar tu condena...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "action.force_puerta_prision.notif.accion.progress",
                                        "Lograste forzar una parte de la cerradura pero se complica;Has conseguido girar un poco el perno, ¡falta poco!;Escuchas un 'click', una de las trabas ha cedido;La ganzúa se ha atascado momentáneamente, pero sigues avanzando;Sientes cómo el mecanismo interno empieza a ceder ante la presión;Has liberado el primer pasador, la puerta vibra ligeramente;Te tiemblan las manos pero logras avanzar con otro mecanismo de seguridad");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.finalice",
                                        "*Logra abrir la puerta y escapa de prisión*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.no_ganzua",
                                        "Necesitas una ganzúa para forzar la cerradura.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.busy",
                                        "La cerradura no está disponible en este momento.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.force_puerta_prision.cancelled",
                                        "Abandonaste el intento y la cerradura volvió a atascarse.");

                        // ENCARCELADO
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_encarceled.notif.time",
                                        "Comenzaste a picar esta roca, espera unos segundos...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_encarceled.notif.accion.progress",
                                        "Haz picado un porcentaje de esta roca...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_encarceled.notif.increment.time.espera",
                                        "Espera %total_time% segundos, para poder salir de prision.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_encarceled.notif.time.success",
                                        "Fuiste liberado de prision, ahora eres libre.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("prision.roca.disabled",
                                        "Hay alguien mas picando esta roca, intentalo en unos segundos.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_encarceled.notif.msg.success",
                                        "Acabo de picar esta piedra;Pude picar esta piedra.");

                        // HACKEOS
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action_tablet_hacker.notif.accion.progress",
                                        "Se le complica el intento de forceo, prepara nuevo metodo de hacerlo..;Logra romper una parte del cajetin del cajero;");

                        // POLICES
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_polices.need.active",
                                        "Necesitas ser policia para realizar esa accion.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_expediente.success.auto",
                                        "Se ha abierto tu expediente.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_expediente.talk.show.my.expediente",
                                        "*Mirando mi expediente*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_expediente.talk.show.other.expediente",
                                        "*Mirando el expediente de %username%*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_expediente.talk.show.error.expediente",
                                        "*Intenta mirar el expediente de %username%, pero no lo consigue*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_expediente.success.other",
                                        "Estas viendo el expediente de %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.user.disabled",
                                        "Tu trabajo de policia fue desactivado.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.user.active",
                                        "Tu trabajo de policia ha sido activado.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.info.sueldo",
                                        "Sueldo acumulado: %sueldo% %br% Reportes pendientes: %reportes% %br%%br% Sigue arrestando si quieres incrementar tu sueldo...");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.info.sueldo.success",
                                        "Acabas de recibir %monto% de salario. %br%%br% Esperamos te vaya mejor para la proxima.");

                        // GIVE POLICES
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police_cmd_give.active",
                                        "Le has dado trabajo de policia a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police_cmd_give.disabled",
                                        "Le has quitado trabajo de policia a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police_cmd_give.recibe.active",
                                        "Recibiste el trabajo de policia.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police_cmd_give.recibe.active.accion",
                                        "Acercate a este escaner en la base policial para obtener tu kit policial.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police_cmd_give.recibe.disabled",
                                        "Tu trabajo de policia ha culminado.");

                        // EXPEDIENTES
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.notif.report.inicie",
                                        "Comenzaste a preparar tu reporte... Debes esperar unos segundos");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.notif.report.success",
                                        "Tu reporte fue realizado exitosamente.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register(
                                        "work_police.notif.report.increment.time.espera",
                                        "Espera %total_time% segundos, para completar tu reporte.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.report.furni.disabled",
                                        "Hay alguien más haciendo un reporte, debes esperar tu turno.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.report.pendiente",
                                        "Tienes reportes pendientes, debes realizarlos para volver a obtener tu arma.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.empty.report.pendiente",
                                        "No tienes reportes pendientes por realizar.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.empty.arma",
                                        "No tienes un arma para arrestar.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.success.arma",
                                        "Conseguiste un arma, ahora puedes usar el comando :arrestar usuario");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.utilice.arma",
                                        "Utilizaste tu arma, debes conseguir otra.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("work_police.empty.arma.accion",
                                        "Tienes reportes pendientes... Ve a completar tus reportes.");

                        // ROBAR COMMAND
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.delito", "Robo de pertenencias");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.too_far",
                                        "Estas demasiado lejos para robar a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.self",
                                        "Espera.. No te puede robar a ti mismo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.not_found",
                                        "No se ha encontrado el usuario %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.no_user",
                                        "No escribiste el nombre de ningun usuario.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.user.no.amount",
                                        "%username% no tiene suficientes monedas como para robarle.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.time.espera",
                                        "*Intento robar a %username%, pero no lo consigo.*");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.user.police",
                                        "%username% es policia, te aseguramos que no querras robar a un policia.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.error.user.no.success",
                                        "*Intento robar a %username% pero no lo consigo*");

                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.robo.self.talk",
                                        "*Tomo mi arma*... *Apunto a %username% y le obligo a entregarme %monto% %type% de su cartera*;*Echa un vistazo que no hayan polis*.. *Le arrebata a %username% %monto% %type% de su monedero*");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_robar.robo.user.talk",
                                        "Acabo de ser robado por %username% :(;%username% acaba de robarme. *Lo intenta reportar a la policia*");

                        // ARRESTAR COMMAND
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.error.too_far",
                                        "Estas demasiado lejos para arrestar a %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.error.self",
                                        "Espera.. No te puede arrestar a ti mismo.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.error.not_found",
                                        "No se ha encontrado el usuario %username%.");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("cmd_arrestar.error.no_user",
                                        "No escribiste el nombre de ningun usuario.");

                        // NEW: ACCIONES
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.hackeo_cajero.process",
                                        "Forzando este cajero:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.roca.process", "Picando esta roca:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.reporte.process",
                                        "Armando mi reporte:");
                        RoleplayWebsocketsRoomsInfo.getTexts().register("action.level_hacker.process",
                                        "Aprendiendo a forzar cajeros:");

                        //////////////////////////////////
                        // EMULATOR CONFIGURACIONES /////
                        // COMMAND ARRESTAR
                        Emulator.getTexts().register("cmd_arrestar.keys", "arrestar;arresto");
                        Emulator.getTexts().register("commands.description.cmd_arrestar",
                                        "<b>:arrestar</b> [usuario] envíalo a la comisiaria.");
                        // COMMAND EXPEDIENTE
                        Emulator.getTexts().register("cmd_expediente.keys", "expediente");
                        Emulator.getTexts().register("commands.description.cmd_expediente",
                                        "<b>:expediente</b> [usuario] mira sus delitos.");
                        // COMMAND GIVE POLICE
                        Emulator.getTexts().register("cmd_give_police.keys", "give_police");
                        Emulator.getTexts().register("commands.description.cmd_give_police",
                                        "<b>:give_police</b> [usuario] Le daras trabajo al usuario de policia.");
                        // COMMAND ROBAR
                        Emulator.getTexts().register("cmd_robar.keys", "robar");
                        Emulator.getTexts().register("commands.description.cmd_robar",
                                        "<b>:robar</b> [usuario] roba monedas de su inventario (delito).");

                        // PERMISOS
                        RoleplayWebsocketsRoomsInfo.getTexts().register("job_permise_staff.need.active", "Necesitas que un staff te de el permiso para activar este empleo, contacta con uno.");

                } catch (Exception e) {
                        LOGGER.error("[New Command] Error al introducir los textos", e);
                }
        }

}
