// File: client/CommandHandler.java
package client;

import java.util.Scanner;
import common.Message;
import common.Message.MessageType;
import common.Constants;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
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
        System.out.println("   Example:         signup john:pass123\n");
        System.out.println("2. login:           Format -> login username:password");
        System.out.println("   Example:         login john:pass123\n");
        System.out.println("3. upload:          Format -> upload photoTitle:<filename> <caption>");
        System.out.println("   Example:         upload photoName:image.jpg <A beautiful sunset>\n");
        System.out.println("4. download:        Format -> download ownerName:filename");
        System.out.println("   Example:         download alice:beach.jpg\n");
        System.out.println("5. search:          Format -> search <photoTitle>");
        System.out.println("   Example:         search Acropolis\n");
        System.out.println("6. follow:          Format -> follow <target_username>");
        System.out.println("   Example:         follow alice\n");
        System.out.println("7. unfollow:        Format -> unfollow <target_username>");
        System.out.println("   Example:         unfollow alice\n");
        System.out.println("8. respondfollow:   Format -> respondfollow <requester_username>:<decision>");
        System.out.println("   Example:         respondfollow alice:reciprocate\n");
        System.out.println("9. access_profile:  Format -> access_profile <target_username>");
        System.out.println("   Example:         access_profile alice\n");
        System.out.println("10. repost:         Format -> repost <target_username>:<postId>");
        System.out.println("   Example:         repost alice:3\n");
        System.out.println("11. comment:        Format -> comment <target_username>:<postId>:<comment text>");
        System.out.println("   Example:         comment alice:3:Nice shot!\n");
        System.out.println("Type 'exit' to quit.");
        System.out.println("======================================");
    }

    private void processCommand(String input) {
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();
        String payload = parts.length > 1 ? parts[1].trim() : "";

        if (!command.equals("signup") &&
                !command.equals("login") &&
                connection.getClientId().equals("clientID_placeholder")) {
            System.out.println("You must login first. Use: login username:password");
            return;
        }

        switch (command) {
            case "signup":
                connection.sendMessage(new Message(MessageType.SIGNUP, connection.getClientId(), payload));
                break;

            case "login":
                connection.sendMessage(new Message(MessageType.LOGIN, connection.getClientId(), payload));
                break;

            case "upload":
                processUploadCommand(payload, connection.getClientId());
                break;

            case "download":
                if (payload.contains(":")) {
                    connection.sendMessage(new Message(MessageType.DOWNLOAD, connection.getClientId(), payload));
                    FileTransferHandler.downloadFile(payload, connection);
                } else {
                    System.out.println("Download command format invalid. Usage: download ownerName:filename");
                }
                break;

            case "search":
                connection.sendMessage(new Message(MessageType.SEARCH, connection.getClientId(), payload));
                break;

            case "follow":
                connection.sendMessage(new Message(MessageType.FOLLOW, connection.getClientId(), payload));
                break;

            case "unfollow":
                connection.sendMessage(new Message(MessageType.UNFOLLOW, connection.getClientId(), payload));
                break;

            case "access_profile":
                connection.sendMessage(new Message(MessageType.ACCESS_PROFILE, connection.getClientId(), payload));
                break;

            case "respondfollow":
                connection.sendMessage(new Message(MessageType.FOLLOW_RESPONSE, connection.getClientId(), payload));
                break;

            case "repost":
                String[] repostTokens = payload.split(":", 2);
                if (repostTokens.length == 2) {
                    connection.sendMessage(new Message(MessageType.REPOST, connection.getClientId(), payload));
                } else {
                    System.out.println("Repost command format invalid. Usage: repost <target_username>:<postId>");
                }
                break;

            case "comment":
                String[] commentTokens = payload.split(":", 3);
                if (commentTokens.length == 3) {
                    connection.sendMessage(new Message(MessageType.COMMENT, connection.getClientId(), payload));
                } else {
                    System.out.println("Comment command format invalid. Usage: comment <target_username>:<postId>:<comment text>");
                }
                break;

            default:
                System.out.println("Unknown command. Please refer to the command list below:");
                printCommandList();
        }
    }

    private void processUploadCommand(String payload, String clientId) {
        Pattern pattern = Pattern.compile("^([^:]+):(\\S+)\\s+<(.+)>$");
        Matcher matcher = pattern.matcher(payload);
        if (matcher.find()) {
            String photoTitle = matcher.group(1).trim();
            String fileName   = matcher.group(2).trim();
            String caption    = matcher.group(3).trim();

            // Build per-user path
            String localDir = Constants.GROUP_ID + "client" + clientId;
            Path clientFilePath = Paths.get("ClientFiles", localDir, fileName);

            if (!Files.exists(clientFilePath)) {
                System.out.println("Upload Error: Unable to read file '" + fileName
                        + "'. Ensure it exists in ClientFiles/" + localDir);
                return;
            }

            try {
                byte[] fileData       = Files.readAllBytes(clientFilePath);
                String fileDataBase64 = Base64.getEncoder().encodeToString(fileData);
                String newPayload     = "photoTitle:" + photoTitle +
                        "|fileName:"  + fileName +
                        "|caption:"   + caption +
                        "|data:"      + fileDataBase64;
                connection.sendMessage(new Message(MessageType.UPLOAD, clientId, newPayload));
            } catch (IOException e) {
                System.out.println("Upload Error: I/O problem reading '" + fileName + "': " + e.getMessage());
            }
        } else {
            System.out.println("Upload command format invalid.");
            System.out.println("Usage: upload <photoTitle>:<fileName> <caption>");
        }
    }
}