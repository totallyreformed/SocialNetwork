package server;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import common.Message;
import common.Message.MessageType;

/**
 * Handles communication with a connected client, processing incoming messages
 * according to the social network protocol and sending responses.
 * Also maintains registries of active clients and their addresses.
 */
public class ClientHandler implements Runnable {

    /** The client socket for network communication. */
    private Socket clientSocket;
    /** Stream for receiving messages from the client. */
    private ObjectInputStream input;
    /** Stream for sending messages to the client. */
    private ObjectOutputStream output;

    private String clientId;     // Numeric ID assigned on signup/login
    private String username;     // Username of this client

    /**
     * Registry of all active ClientHandler instances, keyed by clientId.
     */
    public static ConcurrentHashMap<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    /**
     * Registry mapping clientId to "ip:port" address of each client.
     */
    public static ConcurrentHashMap<String, String> clientAddressMap = new ConcurrentHashMap<>();

    /**
     * Constructs a new handler for the given client socket and logs its creation.
     *
     * @param socket the client's connected socket
     */
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        System.out.println("ClientHandler: New instance created for socket " + socket.getInetAddress());
    }

    /**
     * Main execution loop: establishes I/O streams, listens for incoming
     * Message objects, and dispatches them to be handled.
     */
    @Override
    public void run() {
        try {
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input  = new ObjectInputStream(clientSocket.getInputStream());
            System.out.println("ClientHandler: Streams established for " + clientSocket.getInetAddress());

            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                System.out.println("ClientHandler: Received message: " + msg);
                handleMessage(msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("ClientHandler: Error or disconnection: " + e.getMessage());
        } finally {
            // Clean up registry entries on disconnect
            if (clientId != null) {
                activeClients.remove(clientId);
                clientAddressMap.remove(clientId);
                System.out.println("ClientHandler: Removed client " + clientId + " from registry");
            }
            try {
                clientSocket.close();
            } catch (IOException e) { }
            System.out.println("ClientHandler: Socket closed for client " + clientId);
        }
    }

    /**
     * Processes a received Message by enforcing authentication state
     * and routing each MessageType to the appropriate server manager.
     *
     * @param msg the Message object received from the client
     * @throws IOException if sending a response fails
     */
    private void handleMessage(Message msg) throws IOException {
        // Enforce login before other commands
        if (msg.getType() != MessageType.SIGNUP
                && msg.getType() != MessageType.LOGIN
                && (clientId == null || clientId.equals("clientID_placeholder"))) {
            sendMessage(new Message(MessageType.DIAGNOSTIC, "Server", "Not logged in. Please login first."));
            return;
        }

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

                        // Register IP and port
                        String address = clientSocket.getInetAddress().getHostAddress()
                                + ":" + clientSocket.getPort();
                        clientAddressMap.put(clientId, address);

                        // Welcome message showing both username and ID
                        sendMessage(new Message(
                                MessageType.AUTH_SUCCESS,
                                clientId,
                                "Welcome " + username + " (ClientID: " + clientId + ")"
                        ));
                    } else {
                        sendMessage(new Message(MessageType.AUTH_FAILURE, "Server",
                                "Signup failed: Username already exists."));
                    }
                } else {
                    sendMessage(new Message(MessageType.AUTH_FAILURE, "Server",
                            "Signup failed: Invalid format. Use username:password."));
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

                        // Register IP and port
                        String address = clientSocket.getInetAddress().getHostAddress()
                                + ":" + clientSocket.getPort();
                        clientAddressMap.put(clientId, address);

                        // Welcome back message with username and ID
                        sendMessage(new Message(
                                MessageType.AUTH_SUCCESS,
                                clientId,
                                "Welcome back " + username + " (ClientID: " + clientId + ")"
                        ));

                        // Replay any pending notifications
                        for (String notification : NotificationManager.getInstance().getNotifications(clientId)) {
                            sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                                    "Notification: " + notification));
                        }
                    } else {
                        sendMessage(new Message(MessageType.AUTH_FAILURE, "Server",
                                "Login failed: Incorrect credentials."));
                    }
                } else {
                    sendMessage(new Message(MessageType.AUTH_FAILURE, "Server",
                            "Login failed: Invalid format. Use username:password."));
                }
                break;

            case UPLOAD:
                FileManager.handleUpload(msg, clientId, output);
                break;

            case DOWNLOAD:
                FileManager.handleDownload(msg, clientId, clientSocket, input, output);
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

            case LIST_FOLLOWERS:
                SocialGraphManager.getInstance().handleListFollowers(msg, output);
                break;

            case LIST_FOLLOWING:
                SocialGraphManager.getInstance().handleListFollowing(msg, output);
                break;

            case SEARCH:
                FileManager.handleSearch(msg, clientId, output);
                break;

            case REPOST:
                // Expected payload: "target_username:postId"
                String[] repostTokens = msg.getPayload().split(":", 2);
                if (repostTokens.length == 2) {
                    String targetUsername = repostTokens[0];
                    String postId         = repostTokens[1];
                    String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
                    if (targetNumericId != null) {
                        // Queue the repost notification
                        ProfileManager.handleRepost(msg, clientId, output);
                    } else {
                        sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Repost failed: User '" + targetUsername + "' not found."));
                    }
                } else {
                    sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                            "Repost failed: Invalid format. Use target_username:postId"));
                }
                break;

            case COMMENT:
                // Expected payload: "target_username:postId:commentText"
                String[] commentParts = msg.getPayload().split(":", 3);
                if (commentParts.length == 3) {
                    String targetUsername = commentParts[0];
                    String postId         = commentParts[1];
                    String commentText    = commentParts[2];
                    String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
                    if (targetNumericId != null) {
                        // Queue the comment (ProfileManager will add notification)
                        ProfileManager.getInstance().addCommentToPost(targetNumericId, postId, clientId, commentText);
                        sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Comment added to post " + postId + " of user " + targetUsername));
                    } else {
                        sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Comment failed: User '" + targetUsername + "' not found."));
                    }
                } else {
                    sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                            "Comment failed: Invalid format. Use target_username:postId:comment text"));
                }
                break;

            case FOLLOW_RESPONSE:
                SocialGraphManager.getInstance().handleFollowResponse(msg);
                break;

            default:
                sendMessage(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Unknown command: " + msg.getType()));
                break;
        }
    }

    /**
     * Sends a Message to this client over the output stream.
     * Logs the send operation.
     *
     * @param msg the Message to send
     */
    private void sendMessage(Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
            System.out.println("ClientHandler: Sent message: " + msg);
        } catch (IOException e) {
            System.out.println("ClientHandler: Error sending message to client "
                    + clientId + ": " + e.getMessage());
        }
    }

    /**
     * Retrieves the ObjectOutputStream for external use (e.g., notifications).
     *
     * @return the output stream to send Message objects to the client
     */
    public ObjectOutputStream getOutputStream() {
        return output;
    }

    /**
     * Sends a Message to the client asynchronously from outside this class.
     *
     * @param msg the Message to send
     */
    public void sendExternalMessage(Message msg) {
        sendMessage(msg);
    }
}
