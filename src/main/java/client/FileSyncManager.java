// File: client/FileSyncManager.java
package client;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Watches ClientFiles/<dir> for CREATE/MODIFY events and mirrors them
 * into ServerFiles/<dir>, unless ClientSyncRegistry.shouldSkip(source) is true.
 */
public class FileSyncManager {
    private final WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();

    public FileSyncManager() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        startWatcherThread();
    }

    /** Start watching the given local directory for file changes. */
    public void registerDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        try {
            if (Files.notExists(dir)) Files.createDirectories(dir);
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
                try { key = watchService.take(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                Path dir = keyToDir.get(key);
                if (dir == null) { key.reset(); continue; }

                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == OVERFLOW) continue;
                    Path filename = ((WatchEvent<Path>) ev).context();
                    Path source   = dir.resolve(filename);

                    boolean isCreate = ev.kind() == ENTRY_CREATE;
                    boolean isTxtModify = ev.kind() == ENTRY_MODIFY
                            && filename.toString().endsWith(".txt");

                    if (!isCreate || isTxtModify) { continue; }

                    /* skip if just landed from server: identical size & nearly same mtime */
                    Path serverDir = Paths.get("ServerFiles", dir.getFileName().toString());
                    Path dest      = serverDir.resolve(filename);

                    try {
                        if (Files.exists(dest)) {
                            long sizeLocal  = Files.size(source);
                            long sizeRemote = Files.size(dest);
                            long dt = Math.abs(Files.getLastModifiedTime(source).toMillis()
                                    - Files.getLastModifiedTime(dest).toMillis());
                            if (sizeLocal == sizeRemote && dt < 2_000) {
                                continue;   // downloaded moments ago → do not re-upload
                            }
                        }
                        Files.createDirectories(serverDir);
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("FileSyncManager: copied "
                                + filename + " → " + serverDir);
                    } catch (IOException io) {
                        System.err.println("FileSyncManager: copy failed for "
                                + source + " : " + io.getMessage());
                    }
                }
                key.reset();
            }
        }, "FileSyncManager-Watcher");

        t.setDaemon(true);
        t.start();
    }
}
