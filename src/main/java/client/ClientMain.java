package client;

import common.Constants;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Entry point for the client application that manages file synchronization
 * and server connection for a social network project.
 */
public class ClientMain {

    /**
     * Main method to start the file synchronization manager, establish
     * a server connection, and register directories for synchronization.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        FileSyncManager syncer;
        try {
            // Initialize the FileSyncManager responsible for file operations
            syncer = new FileSyncManager();
        } catch (IOException e) {
            // Log and exit if the sync manager fails to start
            System.err.println("Failed to start FileSyncManager: " + e.getMessage());
            return;
        }

        // Create and attempt to connect to the server
        ServerConnection connection = new ServerConnection();
        if (!connection.connect()) {
            // Inform user if connection fails
            System.out.println("Failed to connect to server.");
            return;
        }
        System.out.println("Connected to server.");

        // Start the command handler in a separate daemon thread
        CommandHandler handler = new CommandHandler(connection);
        Thread cmdThread = new Thread(handler::start, "CommandHandler-Thread");
        cmdThread.setDaemon(true);
        cmdThread.start();

        // Track registered client IDs to avoid duplicate directory registrations
        Set<String> registeredIds = new HashSet<>();
        while (true) {
            String clientId = connection.getClientId();
            // Register a new directory when a valid client ID is received
            if (!"clientID_placeholder".equals(clientId) && registeredIds.add(clientId)) {
                String localDir = Paths.get("ClientFiles", Constants.GROUP_ID + "client" + clientId)
                        .toString();
                syncer.registerDirectory(localDir);
            }
            try {
                // Pause briefly before checking for new client IDs again
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Restore interrupt status and exit loop on interruption
                Thread.currentThread().interrupt();
                System.err.println("Main thread interrupted, exiting");
                break;
            }
        }
    }
}