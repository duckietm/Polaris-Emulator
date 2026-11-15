package com.srth.plugin.habbo;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;

import gnu.trove.map.hash.THashMap;

public class HabboRpManager {

  private THashMap<Integer, HabboRp> HabboRPs = new THashMap<Integer, HabboRp>();
  private THashMap<Integer, HabboRp> HabboRPsOffline = new THashMap<Integer, HabboRp>();

  public HabboRpManager() {
  }

  public void update() {
    HabboRPs.clear();
    HabboRPsOffline.clear();
  }

  public void setHabboRP(Habbo habbo) throws SQLException {
    if (this.getHabboRp(habbo) == null) {
      HabboRPs.put(habbo.getHabboInfo().getId(), new HabboRp(habbo.getHabboInfo().getId()));
    } else {
      HabboRPs.get(habbo.getHabboInfo().getId()).update();
    }
  }

  public HabboRp getHabboRp(Habbo habbo) throws SQLException {
    if (this.getHabboRps().get(habbo.getHabboInfo().getId()) != null) {
      return this.getHabboRps().get(habbo.getHabboInfo().getId());
    } else {
      return null;
    }
  }

  public HabboRp getHabboRpOffline(Integer habbo_id) throws SQLException {
    if (this.getHabboRpsOffline().get(habbo_id) == null) {
      HabboRp newHabboRP = new HabboRp(habbo_id);
      HabboRPsOffline.put(habbo_id, newHabboRP);
      return newHabboRP;
    } else {
      return this.getHabboRpsOffline().get(habbo_id);
    }
  }

  public THashMap<Integer, HabboRp> getHabboRpsOffline() {
    return HabboRPsOffline;
  }

  public THashMap<Integer, HabboRp> getHabboRps() {
    return HabboRPs;
  }

}
