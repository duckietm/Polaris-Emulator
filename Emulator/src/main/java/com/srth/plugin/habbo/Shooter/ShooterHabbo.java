package com.srth.plugin.habbo.Shooter;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import gnu.trove.map.hash.THashMap;

public class ShooterHabbo {

    private THashMap<Integer, Habbo> death_list = new THashMap<Integer, Habbo>();

    private Habbo habbo;
    private HabboRp habboRp;

    private int pvp_mano_active;
    private int pvp_active;
    private int vida;
    private int balas;

    public ShooterHabbo(Habbo habbo) {
        this.habbo = habbo;
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            this.pvp_active = 0;
            this.pvp_mano_active = 0;
            this.vida = habboRp.getVidaHambreDormir().getVida();
            this.balas = habboRp.checkSettings("pvp.balas", 20);
        } catch (Exception e) {
        }
    }

    public void renderEnableVida() {
        Room room = this.habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo == null) {
            return;
        }

        if (this.habboRp.getEnable() != this.getEnableVida()) {
            this.habboRp.setEnable(this.getEnableVida());
            room.giveEffect(habbo, this.getEnableVida(), -1);
        }
    }

    public int restarVida(int value) {
        try {
            this.habboRp.getVidaHambreDormir().restarVida(value);

            int newVida = this.habboRp.getVidaHambreDormir().getVida();
            this.vida = newVida;

            this.renderEnableVida();

            return newVida;
        } catch (Exception e) {
        }
        return 0;
    }

    public int getVida() {
        return this.vida;
    }

    public void setPvpManoActive(int value) {
        this.pvp_mano_active = value;
    }

    public int getPvpManoActive() {
        return this.pvp_mano_active;
    }

    public void setPvpActive(int value) {
        this.pvp_active = value;
    }

    public int getPvpActive() {
        return this.pvp_active;
    }

    public int getEnableVida() {

        int enable = 2000;
        if (this.getVida() <= 1) {
            enable = 2007;
        } else if (this.getVida() <= 10) {
            enable = 2006;
        } else if (this.getVida() <= 20) {
            enable = 2005;
        } else if (this.getVida() <= 40) {
            enable = 2004;
        } else if (this.getVida() <= 60) {
            enable = 2003;
        } else if (this.getVida() <= 80) {
            enable = 2002;
        } else if (this.getVida() <= 90) {
            enable = 2001;
        } else if (this.getVida() >= 95) {
            enable = 2000;
        }

        return enable;
    }

    public THashMap<Integer, Habbo> getDeathList() {
        return death_list;
    }

    public void useBalas() {
        int operacion = this.balas - 1;
        this.setBalas(operacion);
    }

    public void setBalas(int value) {
        this.habboRp.updateData("pvp.balas", value + "");
        this.balas = value;
    }

    public int getBalas() {
        return this.balas;
    }

}
