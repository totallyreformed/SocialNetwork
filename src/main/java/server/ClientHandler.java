package server;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import common.Message;
import common.Message.MessageType;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ObjectInputStream input;
    private ObjectOutputStream output;

    // Internal numeric client id (as a String) assigned upon successful signup or login.
    private String clientId;
    // The username provided by the user.
    private String username;

    // Map of active client connections, keyed by the numeric client id.
    public static ConcurrentHashMap<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        System.out.println("ClientHandler: New instance created for socket " + socket.getInetAddress());
    }

    @Override
    public void run() {
        try {
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input = new ObjectInputStream(clientSocket.getInputStream());
            System.out.println("ClientHandler: Streams established.");

            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                System.out.println("ClientHandler: Received message: " + msg);
                handleMessage(msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("ClientHandler: Error or disconnection: " + e.getMessage());
        } finally {
            try { if (clientSocket != null) clientSocket.close(); } catch (IOException e) { }
            System.out.println("ClientHandler: Socket closed for client " + clientId);
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case SIGNUP:
                // Payload format: "username:password"
                String[] signupParts = msg.getPayload().split(":");
                if (signupParts.length == 2) {
                    String providedUsername = signupParts[0];
                    String password = signupParts[1];
                    String newClientId = AuthenticationManager.signup(providedUsername, password);
                    if (newClientId != null) {
                        clientId = newClientId;
                        username = providedUsername;
                        activeClients.put(clientId, this);
                        sendMessage(new Message(MessageType.AUTH_SUCCESS, clientId, "Signup successful. Welcome " + username + ". Your client id is " + clientId));
                    } else {
                        sendMessage(new Message(MessageType.AUTH_FAILURE, "Server", "Signup failed: Username already exists."));
                    }
                } else {
                    sendMessage(new Message(MessageType.AUTH_FAILURE, "Server", "Signup failed: Invalid format. Use username:password."));
                }
                break;
            case LOGIN:
                // Payload format: "username:password"
                String[] loginParts = msg.getPayload().split(":");
                if (loginParts.length == 2) {
                    String providedUsername = loginParts[0];
                    String password = loginParts[1];
                    String loginClientId = AuthenticationManager.login(providedUsername, password);
                    if (loginClientId != null) {
                        clientId = loginClientId;
                        username = providedUsername;
                        activeClients.put(clientId, this);
                        sendMessage(new Message(MessageType.AUTH_SUCCESS, clientId, "Welcome back " + username));
                    } else {
                        sendMessage(new Message(MessageType.AUTH_FAILURE, "Server", "Login failed: Incorrect credentials."));
                    }
                } else {
                    sendMessage(new Message(MessageType.AUTH_FAILURE, "Server", "Login failed: Invalid format. Use username:password."));
                }
                break;
            case UPLOAD:
                FileManager.handleUpload(msg, clientId, output);
                break;
            case DOWNLOAD:
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
            case FOLLOW_RESPONSE:
                SocialGraphManager.getInstance().handleFollowResponse(msg);
                break;
            case SEARCH:
                FileManager.handleSearch(msg, clientId, output);
                break;
            case REPOST:
                ProfileManager.handleRepost(msg, clientId, output);
                break;
            default:
                sendMessage(new Message(MessageType.DIAGNOSTIC, "Server", "Unknown command: " + msg.getType()));
                break;
            case COMMENT:
                // Expect payload: "target_username:comment text"
                String[] commentParts = msg.getPayload().split(":", 2);
                if(commentParts.length == 2) {
                    String targetUsername = commentParts[0];
                    String commentText = commentParts[1];
                    // Convert target username to numeric id.
                    String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
                    if(targetNumericId != null) {
                        // Append the comment to the target's profile.
                        ProfileManager.getInstance().addComment(targetNumericId, clientId, commentText);
                        // Optionally, send a diagnostic message back.
                        sendMessage(new Message(MessageType.DIAGNOSTIC, "Server", "Comment added to " + targetUsername + "'s profile."));
                    } else {
                        sendMessage(new Message(MessageType.DIAGNOSTIC, "Server", "Comment failed: User '" + targetUsername + "' not found."));
                    }
                } else {
                    sendMessage(new Message(MessageType.DIAGNOSTIC, "Server", "Comment failed: Invalid format. Use target_username:comment text"));
                }
                break;
        }
    }

    private void sendMessage(Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
            System.out.println("ClientHandler: Sent message: " + msg);
        } catch (IOException e) {
            System.out.println("ClientHandler: Error sending message to client " + clientId + ": " + e.getMessage());
        }
    }

    // Expose output stream for notifications.
    public ObjectOutputStream getOutputStream() {
        return output;
    }

    public void sendExternalMessage(Message msg) {
        sendMessage(msg);
    }
}
