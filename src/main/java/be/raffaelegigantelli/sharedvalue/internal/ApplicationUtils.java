package be.raffaelegigantelli.sharedvalue.internal;

public class ApplicationUtils {

    /**
     * Detects the default application folder name
     * based on the current Java command.
     *
     * <p>
     * If the application name cannot be determined,
     * a fallback name is used.
     * </p>
     *
     * @return the sanitized default application folder name
     */
    public static String detectDefaultAppFolder() {
        String command = System.getProperty("sun.java.command");
        if(command == null || command.isBlank()) {
            return "JavaSharedMemory";
        }

        String appName = command.split(" ")[0];
        int lastDotIndex = appName.lastIndexOf(".");
        if(lastDotIndex >= 0) {
            appName = appName.substring(lastDotIndex + 1);
        }

        return StringUtils.sanitize(appName);
    }

}
