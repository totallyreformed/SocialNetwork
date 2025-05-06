// File: client/FileSyncManager.java
package client;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileSyncManager {
    private final WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();

    public FileSyncManager() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        startWatcherThread();
    }

    /**
     * Begin watching the given directory. Any CREATE or MODIFY event in that
     * directory will be mirrored into ServerFiles/<dirName>.
     */
    public void registerDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            keyToDir.put(key, dir);
            System.out.println("FileSyncManager: now watching " + dir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("FileSyncManager: cannot watch " + dir + ": " + e.getMessage());
        }
    }

    private void startWatcherThread() {
        Thread t = new Thread(() -> {
            System.out.println("FileSyncManager: watcher thread started");
            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
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
                    Path source = dir.resolve(filename);

                    // copy into ServerFiles/<dirName>
                    Path serverDir = Paths.get("ServerFiles", dir.getFileName().toString());
                    try {
                        if (!Files.exists(serverDir)) {
                            Files.createDirectories(serverDir);
                        }
                        Path dest = serverDir.resolve(filename);
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("FileSyncManager: copied " +
                                source.getFileName() + " → " + serverDir);
                    } catch (IOException ioe) {
                        System.err.println("FileSyncManager: copy failed for " +
                                source + ": " + ioe.getMessage());
                    }
                }

                if (!key.reset()) {
                    keyToDir.remove(key);
                    if (keyToDir.isEmpty()) {
                        System.out.println("FileSyncManager: no directories left, exiting watcher");
                        break;
                    }
                }
            }
        }, "FileSyncManager-Watcher");

        t.setDaemon(true);
        t.start();
    }
}
