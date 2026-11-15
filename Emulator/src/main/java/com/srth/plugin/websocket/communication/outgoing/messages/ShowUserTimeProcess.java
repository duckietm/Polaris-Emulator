package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowUserTimeProcess extends OutgoingMessage {
    public ShowUserTimeProcess(String header, boolean show, int mostrar, String imagen, String descripcion, int tiempo) throws  SQLException {
        super("ShowUserTimeProcess");
            this.data.addProperty("show_button", show);
            this.data.addProperty("mostrar", mostrar);
            this.data.addProperty("imagen", imagen);
            this.data.addProperty("descripcion", descripcion);
            this.data.addProperty("tiempo", tiempo);
            // this.data.add("tasks", tasksobj);
            this.dataArray.add(this.data);
            this.outgoing.addProperty("header", header);
            this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
