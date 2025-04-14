package client;

public class ClientMain {
    public static void main(String[] args) {
        ServerConnection connection = new ServerConnection();
        if (connection.connect()) {
            System.out.println("Connected to server.");
            CommandHandler handler = new CommandHandler(connection);
            handler.start();
        } else {
            System.out.println("Failed to connect to server.");
        }
    }
}
