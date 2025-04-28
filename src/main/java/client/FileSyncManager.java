package client;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

import common.Message;
import common.Message.MessageType;

public class FileSyncManager implements Runnable {
    private final Path dir;
    private final ServerConnection conn;

    public FileSyncManager(String localDirectory, ServerConnection conn) {
        this.dir  = Paths.get(localDirectory);
        this.conn = conn;
    }

    /** Launches the watcher on its own thread */
    public void startWatcher() {
        new Thread(this, "FileSyncManager").start();
    }

    @Override
    public void run() {
        try {
            // 1) Ensure directory exists
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            System.out.println("FileSyncManager: Watching directory: " + dir);

            // 2) Initial scan: upload any pre-existing image+txt pairs
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String file = p.getFileName().toString();
                    if (file.endsWith(".jpg") || file.endsWith(".png")) {
                        attemptUpload(file, companionCaption(file));
                    } else if (file.endsWith(".txt")) {
                        attemptUpload(companionImage(file), file);
                    }
                }
            }

            // 3) Now begin watching for new/newly modified files
            WatchService ws = FileSystems.getDefault().newWatchService();
            dir.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = ws.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    String file = ev.context().toString();

                    if (file.endsWith(".jpg") || file.endsWith(".png")) {
                        attemptUpload(file, companionCaption(file));
                    } else if (file.endsWith(".txt")) {
                        attemptUpload(companionImage(file), file);
                    }
                }
                key.reset();
            }

        } catch (InterruptedException | IOException e) {
            System.out.println("FileSyncManager: watcher error: " + e.getMessage());
        }
    }

    private String companionCaption(String imageFile) {
        int dot = imageFile.lastIndexOf('.');
        return imageFile.substring(0, dot) + ".txt";
    }

    private String companionImage(String captionFile) {
        return captionFile.replaceAll("\\.txt$", ".jpg");
    }

    /**
     * If both the imageFile **and** captionFile exist in ClientFiles/, upload them.
     */
    private void attemptUpload(String imageFile, String captionFile) {
        Path imgPath = dir.resolve(imageFile);
        Path txtPath = dir.resolve(captionFile);

        if (Files.exists(imgPath) && Files.exists(txtPath)) {
            try {
                byte[] imgBytes = Files.readAllBytes(imgPath);
                String caption  = new String(
                        Files.readAllBytes(txtPath),
                        StandardCharsets.UTF_8
                );
                String b64      = Base64.getEncoder().encodeToString(imgBytes);

                String title = imageFile.substring(0, imageFile.lastIndexOf('.'));
                String payload =
                        "photoTitle:" + title
                                + "|fileName:"  + imageFile
                                + "|caption:"   + caption
                                + "|data:"      + b64;

                conn.sendMessage(new Message(
                        MessageType.UPLOAD,
                        conn.getClientId(),
                        payload
                ));
                System.out.println("FileSyncManager: auto-uploaded " + imageFile);

            } catch (IOException ex) {
                System.out.println("FileSyncManager: I/O error: " + ex.getMessage());
            }
        }
    }
}
