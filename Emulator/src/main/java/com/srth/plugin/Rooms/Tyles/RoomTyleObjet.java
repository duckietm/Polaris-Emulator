package com.srth.plugin.Rooms.Tyles;

import java.util.TreeMap;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.RoomObjet;

public class RoomTyleObjet {

    private RoomObjet roomObjet;

    private TreeMap<Short, HabboItem> backRightFlechas;
    private TreeMap<Short, HabboItem> backLeftFlechas;
    private TreeMap<Short, HabboItem> frontRightFlechas;
    private TreeMap<Short, HabboItem> frontLeftFlechas;
    private TreeMap<Short, HabboItem> homeFlechas;

    public RoomTyleObjet(RoomObjet roomObjet) {
        this.roomObjet = roomObjet;
        this.backRightFlechas = new TreeMap<Short, HabboItem>();
        this.backLeftFlechas = new TreeMap<Short, HabboItem>();
        this.frontRightFlechas = new TreeMap<Short, HabboItem>();
        this.frontLeftFlechas = new TreeMap<Short, HabboItem>();
        this.homeFlechas = new TreeMap<Short, HabboItem>();
    }

    public RoomObjet getRoomObjet() {
        return this.roomObjet;
    }

    public void setBackRightFlechas(HabboItem item) {
        this.backRightFlechas.put(item.getX(), item);
    }

    public void setBackLeftFlechas(HabboItem item) {
        this.backLeftFlechas.put(item.getY(), item);
    }

    public void setFrontRightFlechas(HabboItem item) {
        this.frontRightFlechas.put(item.getY(), item);
    }

    public void setFrontLeftFlechas(HabboItem item) {
        frontLeftFlechas.put(item.getX(), item);
    }

    public void setHomeFlechas(HabboItem item) {
        this.homeFlechas.put(item.getX(), item);
    }

    public TreeMap<Short, HabboItem> getBackRightFlechas() {
        return this.backRightFlechas;
    }

    public TreeMap<Short, HabboItem> getBackLeftFlechas() {
        return this.backLeftFlechas;
    }

    public TreeMap<Short, HabboItem> getFrontRightFlechas() {
        return this.frontRightFlechas;
    }

    public TreeMap<Short, HabboItem> getFrontLeftFlechas() {
        return this.frontLeftFlechas;
    }

    public TreeMap<Short, HabboItem> getHomeFlechas() {
        return this.homeFlechas;
    }

    // --- NUEVOS GETTERS PARA ACCESO CON INTEGER (INT) ---
    public HabboItem getBackRightByX(int x) {
        return this.backRightFlechas.get((short) x);
    }

    public HabboItem getBackLeftByX(int x) {
        return this.backLeftFlechas.get((short) x);
    }

    public HabboItem getFrontRightByX(int x) {
        return this.frontRightFlechas.get((short) x);
    }

    public HabboItem getFrontLeftByX(int x) {
        return this.frontLeftFlechas.get((short) x);
    }

    public HabboItem getHomeByX(int x) {
        return this.homeFlechas.get((short) x);
    }

}
