package client;

import common.Message;
import common.Message.MessageType;

public class FileTransferHandler {
    private ServerConnection connection;

    public FileTransferHandler(ServerConnection connection) {
        this.connection = connection;
    }

    // Initiates the file download process (3-way handshake, then chunked transfer).
    public void downloadFile(String photoName) {
        // Step 1: Initiate the handshake.
        connection.sendMessage(new Message(MessageType.DOWNLOAD, "clientID_placeholder", photoName));
        System.out.println("Initiated download for photo: " + photoName);

        // Subsequent steps would:
        // - Wait for handshake responses,
        // - Receive file segments (APDUs),
        // - For each segment, send an ACK (simulate stop-and-wait),
        // - Handle timeouts/retransmission (for example, delayed ACKs for chunks 3 and 6),
        // - Conclude with synchronization and a final transmission complete message.
    }

    // Additional methods for retransmission logic and timeout management would be added.
}
