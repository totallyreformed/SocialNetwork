package client;

import java.io.ObjectInputStream;
import common.Message;
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
                // If the message is part of the file transfer protocol, delegate to FileTransferHandler.
                if (msg.getType() == Message.MessageType.HANDSHAKE ||
                        msg.getType() == Message.MessageType.FILE_CHUNK ||
                        msg.getType() == Message.MessageType.FILE_END) {
                    // Let FileTransferHandler process it.
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
