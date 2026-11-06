package polaris.core.consolecommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThankyouArcturusCommand extends ConsoleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThankyouArcturusCommand.class);

    public ThankyouArcturusCommand() {
        super("thankyou", "A thankyou message.");
    }

    @Override
    public void handle(String[] args) throws Exception {
        LOGGER.info("Arcturus Morningstar is an opensource community fork of Arcturus Emulator by TheGeneral");
        LOGGER.info("Thankyou to the following people who have helped with it's development:");
        LOGGER.info("TheGeneral - For Creating Arcturus.");
        LOGGER.info("Capheus - Decompilation");
        LOGGER.info("Beny - Lead Developer");
        LOGGER.info("Alejandro - Lead Developer");
        LOGGER.info("Harmonic - Developer");
        LOGGER.info("ArpyAge - Developer");
        LOGGER.info("Mike - Developer");
        LOGGER.info("Skeletor - Developer");
        LOGGER.info("zGrav - Developer");
        LOGGER.info("Swirny - Developer");
        LOGGER.info("Quadral - Developer");
        LOGGER.info("Dome - Developer");
        LOGGER.info("Necmi - Developer");
        LOGGER.info("Oliver - Support");
        LOGGER.info("Rasmus - Support");
        LOGGER.info("Layne - Support");
        LOGGER.info("Bill - Support");
        LOGGER.info("Harmony - Support");
        LOGGER.info("Ridge - Catalogue");
        LOGGER.info("Tenshie - Catalogue");
        LOGGER.info("Wulles - Catalogue");
        LOGGER.info("Gizmo - Catalogue");
        LOGGER.info("TheJava - Motivation");
        LOGGER.info("The Entire Krews.org Community.");
    }
}
