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
            // Initialize object streams.
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input = new ObjectInputStream(clientSocket.getInputStream());

            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                System.out.println("Received: " + msg);
                handleMessage(msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client disconnected or error: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null)
                    clientSocket.close();
            } catch (IOException e) { }
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case SIGNUP:
                // Expected payload format: "clientId:password"
                String[] signupParts = msg.getPayload().split(":");
                if (signupParts.length == 2 && AuthenticationManager.signup(signupParts[0], signupParts[1])) {
                    clientId = signupParts[0];
                    sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Welcome client " + clientId));
                } else {
                    sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Signup failed for payload: " + msg.getPayload()));
                }
                break;
            case LOGIN:
                // Expected payload format: "clientId:password"
                String[] loginParts = msg.getPayload().split(":");
                if (loginParts.length == 2 && AuthenticationManager.login(loginParts[0], loginParts[1])) {
                    clientId = loginParts[0];
                    sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Welcome back client " + clientId));
                } else {
                    sendMessage(new Message(Message.MessageType.DIAGNOSTIC, "Server", "Login failed for payload: " + msg.getPayload()));
                }
                break;
            case UPLOAD:
                FileManager.handleUpload(msg, clientId, output);
                break;
            case DOWNLOAD:
                // Pass both output and input streams so the handshake can be handled.
                FileManager.handleDownload(msg, clientId, input, output);
                break;
            case ACCESS_PROFILE:
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
