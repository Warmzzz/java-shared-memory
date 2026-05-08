package be.raffaelegigantelli.sharedvalue.internal;

public class StringUtils {

    /**
     * Sanitizes a string so it can safely be used
     * as a file or directory name.
     *
     * @param s the string to sanitize
     *
     * @return the sanitized string
     */
    public static String sanitize(String s) {
        return s.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_"
        );
    }

}
