package client;

import common.Message;
import common.Message.MessageType;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class FileTransferHandler {

    // Initiates the file download process.
    public static void downloadFile(String photoName, ServerConnection connection) {
        System.out.println("Initiating download for photo: " + photoName);
        // The CommandHandler has already sent a DOWNLOAD message.
        // The handshake and file chunk reception will be handled by ServerListener and
        // delegated to this handler via handleIncomingMessage.
    }

    // Handles incoming messages that are part of the file transfer.
    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE:
                    System.out.println("Received handshake from server: " + msg.getPayload());
                    // Immediately send ACK for handshake.
                    connection.sendMessage(new Message(MessageType.ACK, "clientID_placeholder", "handshake ACK"));
                    break;
                case FILE_CHUNK:
                    System.out.println("Received " + msg.getPayload());
                    // Immediately send ACK for this chunk.
                    connection.sendMessage(new Message(MessageType.ACK, "clientID_placeholder", "ACK for " + msg.getPayload().split(":")[0]));
                    break;
                case FILE_END:
                    System.out.println("Download complete: " + msg.getPayload());
                    // Optionally trigger directory synchronization.
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Error in file transfer handling: " + e.getMessage());
        }
    }
}
