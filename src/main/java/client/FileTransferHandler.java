package client;

import common.Message;
import common.Message.MessageType;

public class FileTransferHandler {

    // Called by CommandHandler when a DOWNLOAD command is issued.
    public static void downloadFile(String photoName, ServerConnection connection) {
        System.out.println("Initiating download for photo: " + photoName);
        // The handshake and file transfer will be handled via incoming messages.
    }

    // Processes incoming messages related to file transfer.
    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE:
                    System.out.println("Received handshake: " + msg.getPayload());
                    connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "handshake ACK"));
                    break;
                case FILE_CHUNK:
                    System.out.println("Received " + msg.getPayload());
                    // Extract chunk number from message payload for logging.
                    String chunkLabel = msg.getPayload().split(":")[0];
                    connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "ACK for " + chunkLabel));
                    break;
                case FILE_END:
                    System.out.println("Download complete: " + msg.getPayload());
                    // Optionally trigger file sync.
                    break;
                case NACK:
                    System.out.println("Received NACK: " + msg.getPayload());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Error in file transfer handling: " + e.getMessage());
        }
    }
}
