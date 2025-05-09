package client;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Manages local file synchronization by watching a specified client directory
 * for CREATE and MODIFY events and mirroring changes to the corresponding
 * server directory, unless skipped by ClientSyncRegistry.
 */
public class FileSyncManager {
    private final WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();

    /**
     * Constructs a FileSyncManager and initializes the watch service,
     * then starts the watcher thread to process file events.
     *
     * @throws IOException if the watch service cannot be created
     */
    public FileSyncManager() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        startWatcherThread();
    }

    /**
     * Registers the given directory path for file CREATE and MODIFY events.
     * Creates the directory if it does not already exist.
     *
     * @param directoryPath the path of the client directory to watch
     */
    public void registerDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        try {
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            keyToDir.put(key, dir);
            System.out.println("FileSyncManager: now watching " + dir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("FileSyncManager: cannot watch " + dir + ": " + e.getMessage());
        }
    }

    /**
     * Starts a background daemon thread that listens for file system events
     * on all registered directories and processes create/modify events by
     * mirroring files to the server directory.
     */
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
                    if (ev.kind() == OVERFLOW) {
                        continue;
                    }
                    Path filename = ((WatchEvent<Path>) ev).context();
                    Path source   = dir.resolve(filename);

                    boolean isCreate = ev.kind() == ENTRY_CREATE;
                    boolean isTxtModify = ev.kind() == ENTRY_MODIFY
                            && filename.toString().endsWith(".txt");

                    // Only process CREATE events and non-.txt MODIFY events
                    if (!isCreate || isTxtModify) {
                        continue;
                    }
                    if (ClientSyncRegistry.shouldSkip(source)) {
                        continue;
                    }

                    // Determine destination in ServerFiles
                    Path serverDir = Paths.get("ServerFiles", dir.getFileName().toString());
                    Path dest      = serverDir.resolve(filename);

                    try {
                        if (Files.exists(dest)) {
                            long sizeLocal  = Files.size(source);
                            long sizeRemote = Files.size(dest);
                            long dt = Math.abs(
                                    Files.getLastModifiedTime(source).toMillis() -
                                            Files.getLastModifiedTime(dest).toMillis()
                            );
                            // Skip re-upload if file was just downloaded
                            if (sizeLocal == sizeRemote && dt < 2_000) {
                                continue;
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
