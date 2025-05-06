// server/SyncRegistry.java
package server;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SyncRegistry {
    private static final ConcurrentMap<Path, Boolean> skipMap = new ConcurrentHashMap<>();

    /**
     * Call when a file is written to ServerFiles by a client upload,
     * so the watcher can ignore that event.
     */
    public static void markEvent(Path path) {
        skipMap.put(path, Boolean.TRUE);
    }

    /**
     * Returns true if this path was just marked, and clears the mark.
     */
    public static boolean shouldSkip(Path path) {
        return skipMap.remove(path) != null;
    }
}
