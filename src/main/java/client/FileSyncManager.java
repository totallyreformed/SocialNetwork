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

    // Lists files in the local directory.
    public void synchronize() {
        System.out.println("Synchronizing local directory: " + localDirectory);
        File folder = new File(localDirectory);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            System.out.println("Local files:");
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    System.out.println(file.getName());
                }
            }
        }
    }

    // Starts a watcher to detect changes.
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
                            // Trigger synchronization if needed.
                            synchronize();
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
