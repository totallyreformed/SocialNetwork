// File: server/DirectoryWatcher.java
package server;

import common.Constants;
import common.Message;
import common.Message.MessageType;
import common.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Base64;

public class DirectoryWatcher implements Runnable {
    private final WatchService watchService;
    private final Path directory;

    public DirectoryWatcher(String directoryPath) {
        this.directory = Paths.get(directoryPath);
        WatchService ws = null;
        try {
            ws = FileSystems.getDefault().newWatchService();
            directory.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            System.out.println(Util.getTimestamp()
                    + " DirectoryWatcher: Monitoring directory: " + directoryPath);
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " DirectoryWatcher: Initialization error for "
                    + directoryPath + ": " + e.getMessage());
        }
        this.watchService = ws;
    }

    @Override
    public void run() {
        if (watchService == null) return;
        try {
            WatchKey key;
            while ((key = watchService.take()) != null) {
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Path filename = (Path) ev.context();
                    Path fullPath = dir.resolve(filename);

                    // Skip files that came via a client upload
                    if (SyncRegistry.shouldSkip(fullPath)) {
                        continue;
                    }

                    System.out.println(Util.getTimestamp()
                            + " DirectoryWatcher: Detected " + ev.kind()
                            + " on " + fullPath);

                    // Derive clientId from folder name, e.g. "34client2"
                    String folder = dir.getFileName().toString();
                    String clientId = folder.replace(Constants.GROUP_ID + "client", "");

                    ClientHandler handler = ClientHandler.activeClients.get(clientId);
                    if (handler != null) {
                        pushFileToClient(handler, fullPath.toFile(), clientId);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(Util.getTimestamp()
                    + " DirectoryWatcher: Interrupted, exiting.");
        }
    }

    private void pushFileToClient(ClientHandler handler, File file, String clientId) {
        try {
            // 1) Handshake
            handler.sendExternalMessage(new Message(
                    MessageType.HANDSHAKE,
                    "Server",
                    "HANDSHAKE for " + file.getName()
            ));

            // 2) File chunks
            byte[] buffer = new byte[Constants.CHUNK_SIZE];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    String encoded = Base64.getEncoder()
                            .encodeToString(Arrays.copyOf(buffer, bytesRead));
                    handler.sendExternalMessage(new Message(
                            MessageType.FILE_CHUNK,
                            "Server",
                            file.getName() + ":" + encoded
                    ));
                }
            }

            // 3) End‑of‑file
            handler.sendExternalMessage(new Message(
                    MessageType.FILE_END,
                    "Server",
                    file.getName()
            ));
        } catch (IOException ex) {
            System.out.println(Util.getTimestamp()
                    + " DirectoryWatcher: Error pushing " + file.getName()
                    + " to client " + clientId + ": " + ex.getMessage());
        }
    }
}
