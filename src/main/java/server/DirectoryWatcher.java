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
    /** path -> lastCopyEpochMilli */
    private final Map<Path, Long> recent = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 1_000; // 1 s

    public DirectoryWatcher(String root) throws IOException {
        this.serverRoot = Paths.get(root);
        this.watcher = FileSystems.getDefault().newWatchService();

        // register each existing client folder
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(serverRoot)) {
            for (Path d : ds) {
                if (Files.isDirectory(d)) {
                    d.register(watcher,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    System.out.println(Util.getTimestamp() + " DirectoryWatcher: watching " + d);
                }
            }
        }
        // watch root for new client folders
        serverRoot.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try { key = watcher.take(); }
            catch (InterruptedException ie) { break; }

            Path watchedDir = (Path) key.watchable();
            for (WatchEvent<?> e : key.pollEvents()) {
                Path name = (Path) e.context();
                Path full = watchedDir.resolve(name);
                WatchEvent.Kind<?> kind = e.kind();

                /* register new client sub-dir */
                if (watchedDir.equals(serverRoot)
                        && kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(full)) {
                    try {
                        full.register(watcher,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        System.out.println(Util.getTimestamp()
                                + " DirectoryWatcher: registered new folder " + full);
                    } catch (IOException io) {
                        System.err.println(Util.getTimestamp()
                                + " DirectoryWatcher: cannot register " + full + " : " + io);
                    }
                    continue;
                }

                /* ignore root-level events */
                if (watchedDir.equals(serverRoot)) continue;
                if (!Files.isRegularFile(full)) continue;

                long now = System.currentTimeMillis();
                Long last = recent.get(full);

                /* debounce: handle only the first CREATE or if 1 s has passed */
                if (last != null && now - last < DEBOUNCE_MS) continue;
                if (kind == StandardWatchEventKinds.ENTRY_MODIFY && last == null) continue;

                try {
                    Path target = Paths.get("ClientFiles", watchedDir.getFileName().toString(), name.toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(Util.getTimestamp() + " DirectoryWatcher: copied " + name
                            + " → " + target.getParent());
                    recent.put(full, now);
                } catch (IOException io) {
                    System.err.println(Util.getTimestamp() + " DirectoryWatcher: copy failed "
                            + full + " : " + io.getMessage());
                }
            }
            key.reset();
            /* prune old entries */
            long cutoff = System.currentTimeMillis() - DEBOUNCE_MS;
            recent.entrySet().removeIf(ent -> ent.getValue() < cutoff);
        }
    }
}
