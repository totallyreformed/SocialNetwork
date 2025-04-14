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
        System.out.println("Enter commands (signup, login, upload, download, search, follow, unfollow, access_profile). Type 'exit' to quit.");
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            processCommand(input);
        }
        scanner.close();
    }

    private void processCommand(String input) {
        // Basic command parsing: commands followed by a payload.
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();
        String payload = parts.length > 1 ? parts[1] : "";

        // In a full implementation, the clientId would be set after signup/login.
        String clientId = "clientID_placeholder";

        switch (command) {
            case "signup":
                connection.sendMessage(new Message(MessageType.SIGNUP, clientId, payload));
                break;
            case "login":
                connection.sendMessage(new Message(MessageType.LOGIN, clientId, payload));
                break;
            case "upload":
                // Payload format example: "photoName:acropolis.jpg|caption:Beautiful view"
                connection.sendMessage(new Message(MessageType.UPLOAD, clientId, payload));
                break;
            case "download":
                connection.sendMessage(new Message(MessageType.DOWNLOAD, clientId, payload));
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
