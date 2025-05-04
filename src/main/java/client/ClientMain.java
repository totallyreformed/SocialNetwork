// File: client/ClientMain.java
package client;

import common.Constants;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class ClientMain {
    public static void main(String[] args) {
        FileSyncManager syncer;
        try {
            syncer = new FileSyncManager();
        } catch (IOException e) {
            System.err.println("Failed to start FileSyncManager: " + e.getMessage());
            return;
        }

        ServerConnection connection = new ServerConnection();
        if (!connection.connect()) {
            System.out.println("Failed to connect to server.");
            return;
        }
        System.out.println("Connected to server.");

        CommandHandler handler = new CommandHandler(connection);
        Thread cmdThread = new Thread(handler::start, "CommandHandler-Thread");
        cmdThread.setDaemon(true);
        cmdThread.start();

        Set<String> registeredIds = new HashSet<>();
        while (true) {
            String clientId = connection.getClientId();
            if (!"clientID_placeholder".equals(clientId) && registeredIds.add(clientId)) {
                // **FIX**: prepend "ClientFiles/" so we watch SocialNetwork/ClientFiles/34clientX
                String localDir = Paths.get("ClientFiles", Constants.GROUP_ID + "client" + clientId)
                        .toString();
                syncer.registerDirectory(localDir);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Main thread interrupted, exiting");
                break;
            }
        }
    }
}
