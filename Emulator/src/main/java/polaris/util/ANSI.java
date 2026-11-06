package polaris.util;

import ch.qos.logback.core.pattern.color.ANSIConstants;

public class ANSI {

    public static final String RED = "\u001B[" + ANSIConstants.RED_FG + "m";
    public static final String GREEN = "\u001B[" + ANSIConstants.GREEN_FG + "m";
    public static final String YELLOW = "\u001B[" + ANSIConstants.YELLOW_FG + "m";
    public static final String BLUE = "\u001B[" + ANSIConstants.BLUE_FG + "m";
    public static final String MAGENTA = "\u001B[" + ANSIConstants.MAGENTA_FG + "m";
    public static final String CYAN = "\u001B[" + ANSIConstants.CYAN_FG + "m";
    public static final String WHITE = "\u001B[" + ANSIConstants.WHITE_FG + "m";
    public static final String DEFAULT = "\u001B[" + ANSIConstants.DEFAULT_FG + "m";
}
