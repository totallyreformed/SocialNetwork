package client;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.Random;

import common.Message;
import common.Message.MessageType;

public class ServerListener implements Runnable {
    private ObjectInputStream input;
    private ServerConnection connection;
    private ProfileClientManager profileClientManager;

    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input = input;
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                // On successful auth, record clientId and set up local profile manager
                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    connection.setClientId(msg.getSenderId());
                    profileClientManager = new ProfileClientManager(msg.getSenderId());
                    System.out.println(msg.getPayload());
                }
                else if (msg.getType() == MessageType.AUTH_FAILURE) {
                    System.out.println(msg.getPayload());
                }
                else if (msg.getType() == MessageType.DIAGNOSTIC) {
                    String p = msg.getPayload();

                    // Handle search results
                    if (p.startsWith("Search: found photo ")) {
                        // 1) show the server’s line first
                        System.out.println(p);
                        // 2) parse out "<photo> at: id(user),id2(user2),…"
                        String[] parts = p.split(" at: ");
                        if (parts.length == 2) {
                            String file = parts[0].substring("Search: found photo ".length());
                            String[] owners = parts[1].split(",");
                            // pick one at random
                            String chosen = owners[new java.util.Random().nextInt(owners.length)];
                            System.out.println("Initiating download of " + file
                                    + " from client " + chosen);
                            // kick off the download
                            connection.sendMessage(new Message(
                                    MessageType.DOWNLOAD,
                                    connection.getClientId(),
                                    file));
                            FileTransferHandler.downloadFile(file, connection);
                        }
                    }
                    else if (p.startsWith("Search: no followees")) {
                        System.out.println(p);
                    }
                    // Handle repost‐sync messages
                    else if (p.startsWith("SYNC_REPOST:")) {
                        String entry = p.substring("SYNC_REPOST:".length());
                        profileClientManager.appendRepost(entry);
                        System.out.println("Local Others file updated with repost.");
                    }
                    // Only append & show queued notifications upon login:
                    else if (p.startsWith("Notification:")) {
                        profileClientManager.appendPost(p);
                        System.out.println(p);
                    }
                    // Otherwise show normal diagnostic (excluding handshake/chunk logs)
                    else if (!p.contains("handshake") && !p.contains("Chunk")) {
                        System.out.println(p);
                    }
                }

                // Follow requests
                if (msg.getType() == MessageType.FOLLOW_REQUEST) {
                    System.out.println("Follow request received: " + msg.getPayload());
                    continue;
                }

                // File transfer messages handled silently
                if (msg.getType() == MessageType.HANDSHAKE
                        || msg.getType() == MessageType.FILE_CHUNK
                        || msg.getType() == MessageType.FILE_END
                        || msg.getType() == MessageType.NACK) {
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server.");
        }
    }
}
