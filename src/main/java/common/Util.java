package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Parses a payload of the form "key1:val1|key2:val2|…" into a map.
     */
    public static Map<String, String> parsePayload(String payload) {
        Map<String,String> map = new HashMap<>();
        for (String part : payload.split("\\|")) {
            int idx = part.indexOf(':');
            if (idx > 0) {
                String key = part.substring(0, idx);
                String val = part.substring(idx + 1);
                map.put(key, val);
            }
        }
        return map;
    }
}