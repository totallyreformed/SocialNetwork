package server;

import common.Util;
import java.nio.file.*;
import java.io.IOException;

public class DirectoryWatcher implements Runnable {
    private String directoryPath;

    public DirectoryWatcher(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(directoryPath);
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            System.out.println(Util.getTimestamp() + " DirectoryWatcher: Monitoring directory: " + directoryPath);
            while (true) {
                WatchKey key = watchService.take();
                key.pollEvents().forEach(event -> {
                    System.out.println(Util.getTimestamp() + " DirectoryWatcher: Detected " + event.kind() + " on file: " + event.context());
                });
                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println(Util.getTimestamp() + " DirectoryWatcher: Error: " + e.getMessage());
        }
    }
}
