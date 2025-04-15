package client;

import java.io.ObjectInputStream;
import common.Message;
import common.Message.MessageType;
import java.io.IOException;

public class ServerListener implements Runnable {
    private ObjectInputStream input;
    private ServerConnection connection;

    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input = input;
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                // Process authentication messages.
                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    connection.setClientId(msg.getSenderId());
                    System.out.println(msg.getPayload());
                } else if (msg.getType() == MessageType.AUTH_FAILURE) {
                    System.out.println(msg.getPayload());
                } else if (msg.getType() == MessageType.DIAGNOSTIC) {
                    // Show only final diagnostic responses.
                    if (!msg.getPayload().contains("handshake") && !msg.getPayload().contains("Chunk")) {
                        System.out.println(msg.getPayload());
                    }
                } else if (msg.getType() == MessageType.FILE_END) {
                    System.out.println(msg.getPayload());
                }

                // File transfer messages (handshake, chunk, NACK) are handled silently.
                if (msg.getType() == MessageType.HANDSHAKE ||
                        msg.getType() == MessageType.FILE_CHUNK ||
                        msg.getType() == MessageType.FILE_END ||
                        msg.getType() == MessageType.NACK) {
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server.");
        }
    }
}
