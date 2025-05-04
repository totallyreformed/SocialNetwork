// File: client/FileSyncManager.java
package client;

import java.io.IOException;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FileSyncManager {
    private final WatchService watcher;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();

    // Map of filename → timestamp until which we skip syncing
    private static final ConcurrentMap<String, Long> skipUntil = new ConcurrentHashMap<>();

    public FileSyncManager() throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        startWatcherThread();
    }

    /**
     * Begin watching a client-local directory for new/modified files,
     * mirroring them into ServerFiles/<dirName> unless the filename
     * is currently in skipUntil.
     */
    public void registerDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            keyToDir.put(key, dir);
            System.out.println("FileSyncManager: now watching " + dir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("FileSyncManager: cannot watch " + dir + ": " + e.getMessage());
        }
    }

    /**
     * Skip syncing this filename for the next `millis` milliseconds.
     * Used to prevent server‑to‑client copies from bouncing right back.
     */
    public static void skipSyncFor(String fileName, long millis) {
        skipUntil.put(fileName, System.currentTimeMillis() + millis);
    }

    private void startWatcherThread() {
        Thread t = new Thread(() -> {
            System.out.println("FileSyncManager: watcher thread started");
            while (true) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Path dir = keyToDir.get(key);
                if (dir == null) {
                    key.reset();
                    continue;
                }
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) ev).context();
                    String name = filename.toString();

                    // If we're within a skip window, drop this event
                    Long until = skipUntil.get(name);
                    if (until != null) {
                        if (System.currentTimeMillis() < until) {
                            continue;
                        } else {
                            skipUntil.remove(name);
                        }
                    }

                    // Otherwise, mirror to ServerFiles/<dirName>
                    Path source = dir.resolve(filename);
                    Path serverDir = Paths.get("ServerFiles", dir.getFileName().toString());
                    try {
                        if (!Files.exists(serverDir)) Files.createDirectories(serverDir);
                        Files.copy(source, serverDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("FileSyncManager: copied " +
                                source.getFileName() + " → " + serverDir);
                    } catch (IOException ioe) {
                        System.err.println("FileSyncManager: error copying " +
                                source + ": " + ioe.getMessage());
                    }
                }
                if (!key.reset()) {
                    keyToDir.remove(key);
                    if (keyToDir.isEmpty()) break;
                }
            }
        }, "FileSyncManager-Watcher");
        t.setDaemon(true);
        t.start();
    }
}
