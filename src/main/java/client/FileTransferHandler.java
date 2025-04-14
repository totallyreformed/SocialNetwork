package client;

import common.Message;
import common.Message.MessageType;

public class FileTransferHandler {

    // Initiates download (already minimal on the client).
    public static void downloadFile(String photoName, ServerConnection connection) {
        System.out.println("Download initiated for photo: " + photoName);
        // The handshake and reception of chunks will be handled by the listener.
    }

    // Process incoming file transfer messages.
    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE:
                    connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "handshake ACK"));
                    break;
                case FILE_CHUNK:
                    // Send ACK silently.
                    String chunkLabel = msg.getPayload().split(":")[0];
                    connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "ACK for " + chunkLabel));
                    break;
                case FILE_END:
                    // FILE_END message is printed by ServerListener.
                    break;
                case NACK:
                    System.out.println(msg.getPayload());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // Errors in file transfer are logged silently.
        }
    }
}
