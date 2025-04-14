package client;

import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;

public class FileSyncManager {
    private String localDirectory;

    public FileSyncManager(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    // Synchronizes the local directory with the server's directory.
    public void synchronize() {
        System.out.println("Synchronizing local directory: " + localDirectory);
        // Implementation: compare files and update as needed.
    }

    // Start a file watcher to monitor local changes.
    public void startWatcher() {
        Path path = Paths.get(localDirectory);
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            new Thread(() -> {
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            System.out.println("Detected change: " + event.context());
                            // Trigger synchronization if necessary.
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
