package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Util {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String getTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
}
