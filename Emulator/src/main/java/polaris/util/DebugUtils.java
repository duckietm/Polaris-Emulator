package polaris.util;

public class DebugUtils {

    // Code from https://stackoverflow.com/a/11306854/11849181
    public static StackTraceElement getCallerCallerStacktrace() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        String callerClassName = null;
        for (int i = 1; i < stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(DebugUtils.class.getName())
                    && ste.getClassName().indexOf("java.lang.Thread") != 0) {
                if (callerClassName == null) {
                    callerClassName = ste.getClassName();
                } else if (!callerClassName.equals(ste.getClassName())) {
                    return ste;
                }
            }
        }
        return null;
    }
}
