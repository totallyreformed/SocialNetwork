package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class providing common helper methods for the application,
 * such as generating formatted timestamps.
 */
public class Util {

    /**
     * Formatter for timestamps in the pattern "yyyy-MM-dd HH:mm:ss".
     */
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Returns the current local date and time as a formatted string.
     *
     * @return the current timestamp in "yyyy-MM-dd HH:mm:ss" format
     */
    public static String getTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
}