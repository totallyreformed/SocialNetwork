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

/**
 * Handles user commands by reading input from the console, parsing
 * command strings, and sending appropriate messages to the server.
 */
public class CommandHandler {

    /**
     * Connection to the server for sending and receiving messages.
     */
    private ServerConnection connection;

    /**
     * The client’s preferred caption language ("en" or "gr").
     */
    private String languagePref;   // NEW

    /**
     * Constructs a CommandHandler with the given server connection.
     *
     * @param connection the ServerConnection to use for communication
     */
    public CommandHandler(ServerConnection connection) {
        this.connection = connection;
    }

    /**
     * Starts the interactive command loop, printing available commands,
     * reading user input, and dispatching commands until 'exit' is entered.
     */
    public void start() {
        loadLanguagePref();        // NEW
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

    /**
     * Prints the list of supported commands along with their formats
     * and examples to guide the user.
     */
    private void printCommandList() {
        System.out.println("======================================");
        System.out.println("Available Commands:");
        System.out.println("1. signup:          Format -> signup username:password");
        System.out.println("   Example:         signup john:pass123\n");
        System.out.println("2. login:           Format -> login username:password");
        System.out.println("   Example:         login john:pass123\n");
        System.out.println("3. setlang:         Format -> setlang en|gr");
        System.out.println("   Example:         setlang gr\n");
        System.out.println("4. upload:          Format -> upload photoTitle:<filename> <captionEn> [<captionGr>]");
        System.out.println("   Example:         upload photoName:image.jpg <A beautiful sunset> <Ένα όμορφο ηλιοβασίλεμα>\n");
        System.out.println("5. download:        Format -> download ownerName:filename");
        System.out.println("   Example:         download alice:beach.jpg\n");
        System.out.println("6. search:          Format -> search <photoTitle>");
        System.out.println("   Example:         search Acropolis\n");
        System.out.println("7. follow:          Format -> follow <target_username>");
        System.out.println("   Example:         follow alice\n");
        System.out.println("8. unfollow:        Format -> unfollow <target_username>");
        System.out.println("   Example:         unfollow alice\n");
        System.out.println("9. respondfollow:   Format -> respondfollow <requester_username>:<decision>");
        System.out.println("   Example:         respondfollow alice:reciprocate\n");
        System.out.println("10. access_profile:  Format -> access_profile <target_username>");
        System.out.println("   Example:         access_profile alice\n");
        System.out.println("11. repost:         Format -> repost <target_username>:<postId>");
        System.out.println("   Example:         repost alice:3\n");
        System.out.println("12. comment:        Format -> comment <target_username>:<postId>:<comment text>");
        System.out.println("   Example:         comment alice:3:Nice shot!\n");
        System.out.println("13. list_followers: Format -> list_followers");
        System.out.println("   Lists users who follow you\n");
        System.out.println("14. list_following: Format -> list_following");
        System.out.println("   Lists users you are following\n");
        System.out.println("Type 'exit' to quit.");
        System.out.println("======================================");
    }

    /**
     * Parses and processes a single user command string, checking login status
     * and dispatching to the appropriate handler or printing an error message.
     *
     * @param input the full command line entered by the user
     */
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

            case "setlang":    // NEW
                processSetLang(payload);
                break;

            case "upload":
                processUploadCommand(payload, connection.getClientId());
                break;

            case "download":
                if (payload.contains(":")) {
                    // Include language preference
                    String combined = "lang:" + languagePref + "|ownerFilename:" + payload;
                    connection.sendMessage(new Message(MessageType.DOWNLOAD, connection.getClientId(), combined));
                    FileTransferHandler.downloadFile(combined, connection);
                } else {
                    System.out.println("Download command format invalid. Usage: download ownerName:filename");
                }
                break;

            case "search":
                // MODIFIED to include language preference
                String combined = "lang:" + languagePref + "|query:" + payload;
                connection.sendMessage(new Message(MessageType.SEARCH, connection.getClientId(), combined));
                break;

            case "follow":
                connection.sendMessage(new Message(MessageType.FOLLOW, connection.getClientId(), payload));
                break;

            case "unfollow":
                connection.sendMessage(new Message(MessageType.UNFOLLOW, connection.getClientId(), payload));
                break;

            case "list_followers":
                connection.sendMessage(new Message(
                        MessageType.LIST_FOLLOWERS,
                        connection.getClientId(),
                        ""
                ));
                break;

            case "list_following":
                connection.sendMessage(new Message(
                        MessageType.LIST_FOLLOWING,
                        connection.getClientId(),
                        ""
                ));
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

    /**
     * Parses and sends an UPLOAD message with one or two captions.
     *
     * @param payload  the raw string after the word "upload"
     * @param clientId this client’s identifier
     */
    private void processUploadCommand(String payload, String clientId) {
        // Now allow optional second <captionGr>
        Pattern pattern = Pattern.compile(
                "^([^:]+):(\\S+)\\s+<([^>]+)>(?:\\s+<([^>]+)>)?$"
        );
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            System.out.println("Upload command format invalid.");
            System.out.println("Usage: upload photoTitle:<fileName> <captionEn> [<captionGr>]");
            return;
        }

        String photoTitle = matcher.group(1).trim();
        String fileName   = matcher.group(2).trim();
        String captionEn  = matcher.group(3).trim();
        String captionGr  = matcher.group(4) != null
                ? matcher.group(4).trim()
                : "";   // empty if none provided

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

            // include both captions in payload
            String newPayload = "photoTitle:" + photoTitle +
                    "|fileName:"   + fileName +
                    "|captionEn:"  + captionEn +
                    "|captionGr:"  + captionGr +
                    "|data:"       + fileDataBase64;

            connection.sendMessage(
                    new Message(MessageType.UPLOAD, clientId, newPayload)
            );
        } catch (IOException e) {
            System.out.println("Upload Error: I/O problem reading '"
                    + fileName + "': " + e.getMessage());
        }
    }

    // ────── NEW METHODS ──────

    /**
     * Loads the saved language preference from disk or defaults to English.
     */
    private void loadLanguagePref() {
        Path prefs = Paths.get("ClientFiles", "prefs_" + connection.getClientId() + ".txt");
        try {
            if (Files.exists(prefs)) {
                this.languagePref = new String(Files.readAllBytes(prefs)).trim();
            } else {
                this.languagePref = "en";
            }
        } catch (IOException e) {
            System.err.println("Could not read language prefs: " + e.getMessage());
            this.languagePref = "en";
        }
    }

    /**
     * Handles the setlang command, validating and persisting the preference.
     *
     * @param payload should be "en" or "gr"
     */
    private void processSetLang(String payload) {
        String lang = payload.toLowerCase();
        if (!lang.equals("en") && !lang.equals("gr")) {
            System.out.println("Usage: setlang en|gr");
            return;
        }
        this.languagePref = lang;
        connection.setLanguagePref(lang);
        try {
            Path prefs = Paths.get("ClientFiles", "prefs_" + connection.getClientId() + ".txt");
            Files.write(prefs, lang.getBytes());
            System.out.println("Language set to " +
                    (lang.equals("en") ? "English" : "Greek"));
        } catch (IOException e) {
            System.err.println("Failed to save language preference: " + e.getMessage());
        }
    }

}