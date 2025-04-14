package server;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import common.Message;
import common.Constants;
import java.io.IOException;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String clientId;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            // Initialize streams for sending and receiving messages
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input = new ObjectInputStream(clientSocket.getInputStream());

            // Process client messages in a loop
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                System.out.println("Received from client: " + msg);
                handleMessage(msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client disconnected or error occurred: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null)
                    clientSocket.close();
            } catch (IOException e) {
                // Ignore cleanup errors.
            }
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case SIGNUP:
                // Handle signup logic here and assign clientId
                clientId = msg.getSenderId();
                sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Welcome client " + clientId));
                break;
            case LOGIN:
                // Handle login logic
                break;
            case UPLOAD:
                // Delegate upload processing to FileManager
                FileManager.handleUpload(msg, clientId, output);
                break;
            case DOWNLOAD:
                // Delegate download processing to FileManager (with handshake & stop-and-wait)
                FileManager.handleDownload(msg, clientId, output);
                break;
            case ACCESS_PROFILE:
                // Process profile access (only if allowed by social graph)
                ProfileManager.handleAccessProfile(msg, clientId, output);
                break;
            case FOLLOW:
                SocialGraphManager.getInstance().handleFollow(msg);
                break;
            case UNFOLLOW:
                SocialGraphManager.getInstance().handleUnfollow(msg);
                break;
            case SEARCH:
                FileManager.handleSearch(msg, clientId, output);
                break;
            // Add additional case statements as required.
            default:
                sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Unknown command: " + msg.getType()));
                break;
        }
    }

    private void sendMessage(Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
        } catch (IOException e) {
            System.out.println("Error sending message to client " + clientId + ": " + e.getMessage());
        }
    }
}
