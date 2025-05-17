package client;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import common.Message;
import common.Message.MessageType;
import common.Constants;

/**
 * Listens for incoming messages from the server and dispatches
 * actions such as authentication handling, follow requests,
 * search responses, and file transfer events.
 */
public class ServerListener implements Runnable {

    private ObjectInputStream input;
    private ServerConnection connection;
    private ProfileClientManager profileClientManager;
    private String lastDownloadFileName = null;

    /**
     * Constructs a ServerListener with the given input stream and connection.
     *
     * @param input      the ObjectInputStream for receiving Message objects
     * @param connection the ServerConnection to use for sending replies
     */
    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input = input;
        this.connection = connection;
    }

    /**
     * Continuously reads Message objects from the server and handles
     * them based on their MessageType, including auth, follow,
     * search, notifications, and file transfer events.
     */
    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                // Handle authentication success
                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    connection.setClientId(msg.getSenderId());
                    profileClientManager = new ProfileClientManager(msg.getSenderId());
                    System.out.println(msg.getPayload());
                    continue;
                }
                // Handle authentication failure
                if (msg.getType() == MessageType.AUTH_FAILURE) {
                    System.out.println(msg.getPayload());
                    continue;
                }
                // Handle listing followers response
                if (msg.getType() == MessageType.LIST_FOLLOWERS_RESPONSE) {
                    String payload = msg.getPayload();
                    if (payload.isEmpty()) {
                        System.out.println("You have no followers.");
                    } else {
                        System.out.println("Followers: " + payload);
                    }
                    continue;
                }
                // Handle listing following response
                if (msg.getType() == MessageType.LIST_FOLLOWING_RESPONSE) {
                    String payload = msg.getPayload();
                    if (payload.isEmpty()) {
                        System.out.println("You are not following anyone.");
                    } else {
                        System.out.println("Following: " + payload);
                    }
                    continue;
                }
                // Handle diagnostic messages (search, captions, repost sync, notifications)
                if (msg.getType() == MessageType.DIAGNOSTIC) {
                    String p = msg.getPayload();

                    // New search handling:
                    if (p.startsWith("Search: found photo ")) {
                        System.out.println(p);
                        String[] parts = p.split(" at: ");
                        if (parts.length == 2) {
                            // filename without the " (en)" suffix
                            String raw = parts[0].substring("Search: found photo ".length());
                            int idx = raw.indexOf(" (");
                            String file = (idx == -1 ? raw : raw.substring(0, idx)).trim();

                            // pick a random "id(username)" token
                            String[] owners = parts[1].split(",");
                            int ri = new java.util.Random().nextInt(owners.length);
                            String token = owners[ri].trim();          // e.g. "2(makis)"

                            // NEW: extract the username between '(' and ')'
                            String ownerName = token.substring(
                                    token.indexOf('(') + 1,
                                    token.indexOf(')')
                            );

                            System.out.println("Initiating download of " + file + " from user " + ownerName);
                            lastDownloadFileName = file;

                            // build payload with username, not numeric ID
                            String lang = connection.getLanguagePref();
                            String downloadPayload = "lang:" + lang
                                    + "|ownerFilename:" + ownerName + ":" + file;

                            connection.sendMessage(new Message(
                                    MessageType.DOWNLOAD,
                                    connection.getClientId(),
                                    downloadPayload
                            ));
                            FileTransferHandler.downloadFile(downloadPayload, connection);
                        }
                        continue;
                    }
                    if (p.startsWith("Search: no followees")) {
                        System.out.println(p);
                        continue;
                    }
                    if (p.startsWith("Caption: ")) {
                        String captionText = p.substring("Caption: ".length());
                        saveCaptionFile(captionText);
                        continue;
                    }
                    if (p.equals("No caption available")) {
                        saveCaptionFile("");
                        continue;
                    }
                    if (p.startsWith("SYNC_REPOST:")) {
                        String entry = p.substring("SYNC_REPOST:".length());
                        profileClientManager.appendRepost(entry);
                        System.out.println("Local Others file updated with repost: " + entry);
                        continue;
                    }
                    if (p.startsWith("Notification:")) {
                        profileClientManager.appendPost(p);
                        System.out.println(p);
                        continue;
                    }
                    if (!p.contains("handshake") && !p.contains("Chunk")) {
                        System.out.println(p);
                    }
                }
                // Handle follow request notifications
                if (msg.getType() == MessageType.FOLLOW_REQUEST) {
                    String[] parts = msg.getPayload().split(":", 2);
                    String requesterUsername = parts[0];
                    System.out.println("\n>>> User '" + requesterUsername + "' wants to follow you.");
                    System.out.println("    Type: respondfollow " + requesterUsername + ":<accept|reject|reciprocate>");
                    System.out.print("> ");
                    continue;
                }
                // Handle file transfer protocol messages
                if (msg.getType() == MessageType.HANDSHAKE
                        || msg.getType() == MessageType.FILE_CHUNK
                        || msg.getType() == MessageType.FILE_END
                        || msg.getType() == MessageType.NACK) {
                    if (msg.getType() == MessageType.HANDSHAKE) {
                        String hs = msg.getPayload();
                        int idx = hs.indexOf("for ");
                        if (idx != -1) {
                            lastDownloadFileName = hs.substring(idx + 4).trim();
                        }
                    }
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server.");
        }
    }

    /**
     * Saves the caption text for the last downloaded file to a .txt file
     * in the client-specific directory.
     *
     * @param captionText the caption content to save; empty string if none
     */
    private void saveCaptionFile(String captionText) {
        if (lastDownloadFileName == null) {
            System.out.println("Warning: no filename recorded for caption; skipping save.");
            return;
        }
        try {
            String base = lastDownloadFileName;
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);

            String clientId = connection.getClientId();
            File dir = new File("ClientFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) dir.mkdirs();

            File captionFile = new File(dir, base + ".txt");
            try (FileWriter fw = new FileWriter(captionFile)) {
                fw.write(captionText);
            }
            System.out.println("FileTransferHandler: Caption saved to " + captionFile.getPath());
        } catch (IOException e) {
            System.out.println("Error saving caption file: " + e.getMessage());
        }
    }
}