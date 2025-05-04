// File: server/DirectoryWatcher.java
package server;

import common.Message;
import common.Message.MessageType;
import common.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;

@SuppressWarnings("unchecked")
public class DirectoryWatcher implements Runnable {
    private final Path basePath;
    private final WatchService watcher;

    public DirectoryWatcher(String baseDir) throws IOException {
        this.basePath = Paths.get(baseDir);
        this.watcher  = FileSystems.getDefault().newWatchService();
        // Watch for new client‑directories under ServerFiles
        basePath.register(watcher, ENTRY_CREATE);

        // Also register any already‑existing client dirs for file creates
        File[] clientDirs = basePath.toFile().listFiles(File::isDirectory);
        if (clientDirs != null) {
            for (File dir : clientDirs) {
                dir.toPath().register(watcher, ENTRY_CREATE);
            }
        }

        System.out.println(Util.getTimestamp()
                + " DirectoryWatcher: watching base + existing client dirs");
    }

    @Override
    public void run() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                Path watched = (Path) key.watchable();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == OVERFLOW) continue;
                    Path name = ((WatchEvent<Path>) ev).context();
                    Path full  = watched.resolve(name);

                    // New client-dir under ServerFiles?
                    if (watched.equals(basePath)
                            && ev.kind() == ENTRY_CREATE
                            && Files.isDirectory(full)) {
                        full.register(watcher, ENTRY_CREATE);
                        System.out.println(Util.getTimestamp()
                                + " DirectoryWatcher: now watching new client-dir " + full);
                        continue;
                    }

                    // Otherwise a new file under a client-dir
                    String clientDirName = watched.getFileName().toString(); // e.g. "34client1"
                    String fileName      = name.toString();
                    System.out.println(Util.getTimestamp()
                            + " DirectoryWatcher: Detected " + ev.kind()
                            + " on " + full);

                    notifyClient(clientDirName, fileName);
                }
                if (!key.reset()) {
                    System.err.println(Util.getTimestamp()
                            + " DirectoryWatcher: watch key invalid, exiting");
                    break;
                }
            }
        } catch (InterruptedException|IOException e) {
            Thread.currentThread().interrupt();
            System.err.println(Util.getTimestamp()
                    + " DirectoryWatcher error: " + e.getMessage());
        }
    }

    private void notifyClient(String clientDirName, String fileName) {
        // Extract only the number after "client"
        int idx = clientDirName.indexOf("client");
        if (idx < 0) return;
        String clientId = clientDirName.substring(idx + "client".length());

        ClientHandler handler = ClientHandler.activeClients.get(clientId);
        if (handler == null) {
            System.err.println(Util.getTimestamp()
                    + " DirectoryWatcher: client " + clientId
                    + " not connected, skipping AUTO_DOWNLOAD for " + fileName);
            return;
        }

        Message auto = new Message(MessageType.DIAGNOSTIC, "Server",
                "AUTO_DOWNLOAD:" + fileName);
        try {
            handler.sendExternalMessage(auto);
            System.out.println(Util.getTimestamp()
                    + " DirectoryWatcher: sent AUTO_DOWNLOAD for " + fileName
                    + " → client " + clientId);
        } catch (Exception e) {
            System.err.println(Util.getTimestamp()
                    + " DirectoryWatcher: failed to send AUTO_DOWNLOAD to client "
                    + clientId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Scan a single client directory on demand (e.g. right after login)
     * and send AUTO_DOWNLOAD for every file already present.
     */
    public void initialScanFor(String clientDirName) {
        Path dir = basePath.resolve(clientDirName);
        File[] files = dir.toFile().listFiles(File::isFile);
        if (files == null) return;
        for (File f : files) {
            notifyClient(clientDirName, f.getName());
        }
    }
}
