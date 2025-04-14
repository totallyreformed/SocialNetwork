package client;

import java.util.Scanner;
import common.Message;
import common.Message.MessageType;

public class CommandHandler {
    private ServerConnection connection;

    public CommandHandler(ServerConnection connection) {
        this.connection = connection;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Commands: signup, login, upload, download, search, follow, unfollow, access_profile. Type 'exit' to quit.");
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            processCommand(input);
        }
        scanner.close();
    }

    private void processCommand(String input) {
        // Format: command [payload]
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();
        String payload = parts.length > 1 ? parts[1] : "";

        // For simplicity, the clientId is included in the payload for signup/login.
        // For other commands, we assume the client has already logged in.
        String clientId = "clientID_placeholder";

        switch (command) {
            case "signup":
                // Expected payload: "clientId:password"
                connection.sendMessage(new Message(MessageType.SIGNUP, clientId, payload));
                break;
            case "login":
                // Expected payload: "clientId:password"
                connection.sendMessage(new Message(MessageType.LOGIN, clientId, payload));
                break;
            case "upload":
                // Expected payload: "photoName:<name>|caption:<text>|data:<dataString>"
                connection.sendMessage(new Message(MessageType.UPLOAD, clientId, payload));
                break;
            case "download":
                // Expected payload: photoName
                connection.sendMessage(new Message(MessageType.DOWNLOAD, clientId, payload));
                // Initiate file transfer handling on the client side.
                FileTransferHandler.downloadFile(payload, connection);
                break;
            case "search":
                connection.sendMessage(new Message(MessageType.SEARCH, clientId, payload));
                break;
            case "follow":
                connection.sendMessage(new Message(MessageType.FOLLOW, clientId, payload));
                break;
            case "unfollow":
                connection.sendMessage(new Message(MessageType.UNFOLLOW, clientId, payload));
                break;
            case "access_profile":
                connection.sendMessage(new Message(MessageType.ACCESS_PROFILE, clientId, payload));
                break;
            default:
                System.out.println("Unknown command.");
        }
    }
}
