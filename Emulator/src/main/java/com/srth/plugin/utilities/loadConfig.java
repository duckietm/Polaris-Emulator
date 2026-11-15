package com.srth.plugin.utilities;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.utils.SelectBuidText;
import com.srth.plugin.utilities.rpconfigs.ConfigHackerCajeros;
import com.srth.plugin.utilities.rpconfigs.ConfigVidayHambre;

public class loadConfig {

    public static void ILoadConfig() throws Exception {
        
        new ConfigVidayHambre().load();
        new ConfigHackerCajeros().load();
        new SelectBuidText().load();
        
    }
}
