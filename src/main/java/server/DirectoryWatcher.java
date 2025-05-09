// File: server/DirectoryWatcher.java
package server;

import common.Util;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DirectoryWatcher implements Runnable {
    private final WatchService watcher;
    private final Path serverRoot;
    /** path -> last copy timestamp (ms) */
    private final Map<Path, Long> recent = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 2_000; // 2 seconds

    public DirectoryWatcher(String rootDir) throws IOException {
        this.serverRoot = Paths.get(rootDir);
        this.watcher = FileSystems.getDefault().newWatchService();

        // 1) Register any existing client subdirectories
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(serverRoot)) {
            for (Path d : ds) {
                if (Files.isDirectory(d)) {
                    d.register(watcher,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    System.out.println(Util.getTimestamp()
                            + " DirectoryWatcher: watching " + d);
                }
            }
        }

        // 2) Watch the root itself for new subdirectories
        serverRoot.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Path watchedDir = (Path) key.watchable();
            for (WatchEvent<?> ev : key.pollEvents()) {
                WatchEvent.Kind<?> kind = ev.kind();
                Path name = ((WatchEvent<Path>)ev).context();
                Path fullPath = watchedDir.resolve(name);

                // A) If a new client folder appeared under the root, register it
                if (watchedDir.equals(serverRoot)
                        && kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(fullPath)) {
                    try {
                        fullPath.register(watcher,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        System.out.println(Util.getTimestamp()
                                + " DirectoryWatcher: registered new folder " + fullPath);
                    } catch (IOException ioe) {
                        System.err.println(Util.getTimestamp()
                                + " DirectoryWatcher: can't watch " + fullPath + ": " + ioe.getMessage());
                    }
                    continue;
                }

                // B) Ignore events in the root itself (only subdirs matter)
                if (watchedDir.equals(serverRoot)) continue;
                // C) Only handle real files
                if (!Files.isRegularFile(fullPath)) continue;

                long now = System.currentTimeMillis();
                Long lastTs = recent.get(fullPath);
                // D) Debounce: if we just copied this path < DEBOUNCE_MS ago, skip
                if (lastTs != null && (now - lastTs) < DEBOUNCE_MS) {
                    continue;
                }

                // E) Perform the copy to the corresponding ClientFiles folder
                try {
                    Path clientDir = Paths.get("ClientFiles", watchedDir.getFileName().toString());
                    Files.createDirectories(clientDir);
                    Path dest = clientDir.resolve(name);
                    Files.copy(fullPath, dest, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(Util.getTimestamp()
                            + " DirectoryWatcher: copied " + name + " → " + clientDir);
                    recent.put(fullPath, now);
                } catch (IOException ioe) {
                    System.err.println(Util.getTimestamp()
                            + " DirectoryWatcher: copy failed for " + fullPath
                            + ": " + ioe.getMessage());
                }
            }

            if (!key.reset()) break;

            // F) Prune old entries
            long cutoff = System.currentTimeMillis() - DEBOUNCE_MS;
            recent.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }
}
