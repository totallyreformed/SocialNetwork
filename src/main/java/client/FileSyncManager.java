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

    // List current files and simulate synchronization.
    public void synchronize() {
        System.out.println("FileSyncManager: Synchronizing local directory: " + localDirectory);
        File folder = new File(localDirectory);
        if (!folder.exists()) {
            System.out.println("FileSyncManager: Directory not found. Creating " + localDirectory);
            folder.mkdirs();
        }
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            System.out.println("FileSyncManager: Local files:");
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    System.out.println(" - " + file.getName());
                }
            }
        }
        // In a complete implementation, compare with the server's directory.
    }

    // Start watching the directory for changes.
    public void startWatcher() {
        Path path = Paths.get(localDirectory);
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            new Thread(() -> {
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            System.out.println("FileSyncManager: Detected file change: " + event.context());
                            synchronize();
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        System.out.println("FileSyncManager: Watcher interrupted.");
                        break;
                    }
                }
            }).start();
        } catch (IOException e) {
            System.out.println("FileSyncManager: Error starting directory watcher: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
