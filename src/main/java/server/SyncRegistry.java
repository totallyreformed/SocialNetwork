package server;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for file synchronization events, allowing DirectoryWatcher
 * to ignore filesystem changes that were initiated by the server itself.
 *<p>
 * Files written by FileManager mark their Path in this registry, and
 * the watcher checks shouldSkip to avoid reprocessing those events.
 */
public class SyncRegistry {
    /**
     * Map of Paths to a boolean flag indicating recent server-initiated changes.
     */
    private static final ConcurrentMap<Path, Boolean> skipMap = new ConcurrentHashMap<>();

    /**
     * Marks the given path so that DirectoryWatcher should skip the next event
     * corresponding to it.
     *
     * @param path the filesystem Path of the file change to mark
     */
    public static void markEvent(Path path) {
        skipMap.put(path, Boolean.TRUE);
    }

    /**
     * Checks if the given path was marked by markEvent, clears the mark,
     * and returns whether it should be skipped.
     *
     * @param path the filesystem Path to check
     * @return true if the path was marked and is now cleared; false otherwise
     */
    public static boolean shouldSkip(Path path) {
        return skipMap.remove(path) != null;
    }
}