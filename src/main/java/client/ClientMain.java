package client;

public class ClientMain {
    public static void main(String[] args) {
        ServerConnection connection = new ServerConnection();
        if (connection.connect()) {
            System.out.println("Connected to server.");

            // Start automatic directory‐sync
            FileSyncManager sync = new FileSyncManager("ClientFiles", connection);
            sync.startWatcher();

            CommandHandler handler = new CommandHandler(connection);
            handler.start();
        } else {
            System.out.println("Failed to connect to server.");
        }
    }
}
