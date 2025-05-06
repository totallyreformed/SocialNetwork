// File: client/ClientSyncRegistry.java
package client;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry to mark file events originating from server pushes/downloads,
 * so the client-side FileSyncManager can skip re-uploading them.
 */
public class ClientSyncRegistry {
    private static final ConcurrentMap<Path, Boolean> skipMap = new ConcurrentHashMap<>();

    /** Mark that this path was just written by a server push or download. */
    public static void markEvent(Path path) {
        skipMap.put(path, Boolean.TRUE);
    }

    /** Returns true if this path was just marked; clears the mark. */
    public static boolean shouldSkip(Path path) {
        return skipMap.remove(path) != null;
    }
}
