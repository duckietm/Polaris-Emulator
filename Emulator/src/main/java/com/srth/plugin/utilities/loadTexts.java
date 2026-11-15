package com.srth.plugin.utilities;

import com.srth.plugin.habbo.Frank.Frank.Utils.TextFrank;
import com.srth.plugin.utilities.rptexts.TextHackerCajeros;
import com.srth.plugin.utilities.rptexts.TextVidayHambre;

public class loadTexts {

    public static void ILoadTexts() throws Exception {

        new TextVidayHambre().load();
        new TextHackerCajeros().load();
        new TextFrank().load();

    }

}
