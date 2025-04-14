package client;

public class ClientMain {
    public static void main(String[] args) {
        // Establish connection with the server.
        ServerConnection connection = new ServerConnection();
        if (connection.connect()) {
            System.out.println("Connected to server.");
            // Start the command loop to process user input.
            CommandHandler handler = new CommandHandler(connection);
            handler.start();
        } else {
            System.out.println("Failed to connect to server.");
        }
    }
}
