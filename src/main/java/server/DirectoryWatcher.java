package server;

import common.Util;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches a server root directory for new and modified client subdirectories
 * and files, mirroring changes to the corresponding ClientFiles folder
 * with a debounce to prevent rapid duplicate copies.
 */
public class DirectoryWatcher implements Runnable {

    /** WatchService to monitor filesystem events. */
    private final WatchService watcher;

    /** Root directory path under which client folders reside. */
    private final Path serverRoot;

    /** Map tracking last copy timestamp (milliseconds) for each path. */
    private final Map<Path, Long> recent = new ConcurrentHashMap<>();

    /** Minimum interval in milliseconds between successive copies of the same file. */
    private static final long DEBOUNCE_MS = 2_000;

    /**
     * Constructs a DirectoryWatcher for the given root directory, registers
     * existing subdirectories and the root for creation events.
     *
     * @param rootDir path to the server root directory to watch
     * @throws IOException if an I/O error occurs creating the watch service
     */
    public DirectoryWatcher(String rootDir) throws IOException {
        this.serverRoot = Paths.get(rootDir);
        this.watcher = FileSystems.getDefault().newWatchService();

        // 1) Register existing client subdirectories
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

    /**
     * Runs the watch loop, handling directory and file events:
     * Registers new client folders
     * Copies new or modified files with debouncing
     * Prunes old timestamp entries
     */
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
                Path name = ((WatchEvent<Path>) ev).context();
                Path fullPath = watchedDir.resolve(name);

                // A) New client folder created under root: register it
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

                // B) Ignore root-level events (only subdirectories matter)
                if (watchedDir.equals(serverRoot)) continue;
                // C) Only process regular files
                if (!Files.isRegularFile(fullPath)) continue;

                long now = System.currentTimeMillis();
                Long lastTs = recent.get(fullPath);
                // D) Debounce: skip if recently processed
                if (lastTs != null && (now - lastTs) < DEBOUNCE_MS) {
                    continue;
                }

                // E) Copy file to corresponding ClientFiles folder
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

            // F) Remove entries older than debounce interval
            long cutoff = System.currentTimeMillis() - DEBOUNCE_MS;
            recent.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }
}
