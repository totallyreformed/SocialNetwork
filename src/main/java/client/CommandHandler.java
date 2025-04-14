package client;

import java.util.Scanner;
import common.Message;
import common.Message.MessageType;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Base64;

public class CommandHandler {
    private ServerConnection connection;

    public CommandHandler(ServerConnection connection) {
        this.connection = connection;
    }

    public void start() {
        printCommandList();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            processCommand(input);
        }
        scanner.close();
    }

    private void printCommandList() {
        System.out.println("======================================");
        System.out.println("Available Commands:");
        System.out.println("1. signup:          Format -> signup username:password");
        System.out.println("   Example:         signup john:pass123" + "\n");
        System.out.println("2. login:           Format -> login username:password");
        System.out.println("   Example:         login john:pass123" + "\n");
        System.out.println("3. upload:          Format -> upload photoName:<filename> <caption>");
        System.out.println("   Example:         upload photoName:image.jpg <A beautiful sunset>" + "\n");
        System.out.println("4. download:        Format -> download <filename>");
        System.out.println("   Example:         download image.jpg" + "\n");
        System.out.println("5. search:          Format -> search <filename>");
        System.out.println("   Example:         search image.jpg" + "\n");
        System.out.println("6. follow:          Format -> follow <target_username>");
        System.out.println("   Example:         follow alice" + "\n");
        System.out.println("7. unfollow:        Format -> unfollow <target_username>");
        System.out.println("   Example:         unfollow alice" + "\n");
        System.out.println("8. respondfollow:   Format -> respondfollow <requester_username>:<decision>");
        System.out.println("   Example:         respondfollow alice:reciprocate" + "\n");
        System.out.println("9. access_profile:  Format -> access_profile <target_username>");
        System.out.println("   Example:         access_profile alice" + "\n");
        System.out.println("10. repost:         Format -> repost <postContent>");
        System.out.println("   Example:         repost This is an amazing photo!" + "\n");
        System.out.println("11. sync:           Format -> sync");
        System.out.println("   Example:         sync" + "\n");
        System.out.println("Type 'exit' to quit.");
        System.out.println("======================================");
    }

    private void processCommand(String input) {
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();
        String payload = parts.length > 1 ? parts[1] : "";
        String clientId = connection.getClientId();

        switch (command) {
            case "signup":
                connection.sendMessage(new Message(MessageType.SIGNUP, clientId, payload));
                break;
            case "login":
                connection.sendMessage(new Message(MessageType.LOGIN, clientId, payload));
                break;
            case "upload":
                processUploadCommand(payload, clientId);
                break;
            case "download":
                connection.sendMessage(new Message(MessageType.DOWNLOAD, clientId, payload));
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
            case "repost":
                connection.sendMessage(new Message(MessageType.REPOST, clientId, payload));
                break;
            case "sync":
                System.out.println("Synchronizing local directory...");
                FileSyncManager syncManager = new FileSyncManager("ClientFiles");
                syncManager.synchronize();
                break;
            case "respondfollow":
                connection.sendMessage(new Message(MessageType.FOLLOW_RESPONSE, clientId, payload));
                break;
            default:
                System.out.println("Unknown command. Please refer to the command list below:");
                printCommandList();
                break;
        }
    }

    // Process upload command with simplified syntax.
    private void processUploadCommand(String payload, String clientId) {
        Pattern pattern = Pattern.compile("^photoName:(\\S+)\\s+<(.+)>$");
        Matcher matcher = pattern.matcher(payload);
        if (matcher.find()) {
            String fileName = matcher.group(1);
            String caption = matcher.group(2);
            try {
                byte[] fileData = Files.readAllBytes(Paths.get("ClientFiles", fileName));
                String fileDataBase64 = Base64.getEncoder().encodeToString(fileData);
                String newPayload = "photoName:" + fileName + "|caption:" + caption + "|data:" + fileDataBase64;
                connection.sendMessage(new Message(MessageType.UPLOAD, clientId, newPayload));
            } catch (IOException e) {
                System.out.println("Upload Error: Unable to read file '" + fileName + "'. Ensure it exists in the ClientFiles directory.");
            }
        } else {
            System.out.println("Upload command format invalid.");
            System.out.println("Usage: upload photoName:<filename> <caption>");
            System.out.println("Example: upload photoName:image.jpg <A beautiful sunset>");
        }
    }
}
