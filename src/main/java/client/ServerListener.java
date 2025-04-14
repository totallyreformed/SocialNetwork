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
                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    System.out.println("Authentication successful: " + msg.getPayload());
                    connection.setClientId(msg.getSenderId());
                }
                // Delegate file-transfer messages.
                if (msg.getType() == MessageType.HANDSHAKE ||
                        msg.getType() == MessageType.FILE_CHUNK ||
                        msg.getType() == MessageType.FILE_END ||
                        msg.getType() == MessageType.NACK) {
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                } else {
                    System.out.println("Server: " + msg.getPayload());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        }
    }
}
