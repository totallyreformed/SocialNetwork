package server;

import common.Message;
import common.Message.MessageType;
import common.Util;

import java.util.*;
import java.io.*;

/**
 * Singleton managing the social graph of follower relationships among clients.
 * Supports loading from file, follow/unfollow operations, listing followers/following,
 * and dispatching follow request and response messages.
 */
public class SocialGraphManager {
    /** Singleton instance of SocialGraphManager. */
    private static SocialGraphManager instance = null;

    /**
     * Maps each client numeric ID to the set of IDs that follow them.
     */
    private HashMap<String, Set<String>> socialGraph = new HashMap<>();

    /** Private constructor for singleton pattern. */
    private SocialGraphManager() { }

    /**
     * Returns the singleton instance, creating it if necessary.
     *
     * @return the SocialGraphManager instance
     */
    public static SocialGraphManager getInstance() {
        if (instance == null) {
            instance = new SocialGraphManager();
        }
        return instance;
    }

    /**
     * Loads the social graph from a text file where each line contains a client ID
     * followed by its follower IDs separated by whitespace.
     *
     * @param filename the path to the social graph file
     */
    public void loadSocialGraph(String filename) {
        try (Scanner scanner = new Scanner(new File(filename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                String clientId = parts[0];
                Set<String> followers = new HashSet<>();
                for (int i = 1; i < parts.length; i++) {
                    followers.add(parts[i]);
                }
                socialGraph.put(clientId, followers);
            }
            System.out.println("SocialGraphManager: Social graph loaded successfully from " + filename);
        } catch (FileNotFoundException e) {
            System.out.println("SocialGraphManager: File " + filename + " not found. " + e.getMessage());
        }
    }

    /**
     * Determines whether a requester follows a target client.
     * Self-following is always false.
     *
     * @param requesterId the numeric ID of the requesting client
     * @param targetId    the numeric ID of the target client
     * @return true if requester follows target; false otherwise
     */
    public boolean isFollowing(String requesterId, String targetId) {
        // Do not include self-following
        if (requesterId.equals(targetId)) {
            System.out.println("SocialGraphManager: isFollowing(" + requesterId + ", " + targetId + ") = false (self-following)");
            return false;
        }
        Set<String> followers = socialGraph.get(targetId);
        boolean result = followers != null && followers.contains(requesterId);
        System.out.println("SocialGraphManager: isFollowing(" + requesterId + ", " + targetId + ") = " + result);
        return result;
    }

    /**
     * Retrieves the set of client IDs that the given client follows.
     *
     * @param clientId the numeric ID of the client
     * @return a Set of numeric IDs that clientId follows
     */
    public Set<String> getFollowees(String clientId) {
        Set<String> followees = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : socialGraph.entrySet()) {
            if (e.getValue().contains(clientId)) {
                followees.add(e.getKey());
            }
        }
        return followees;
    }

    /**
     * Processes an incoming follow request by queuing a notification for the target user,
     * confirming to the requester, and sending a live prompt if the target is online.
     *
     * @param msg the Message containing follower-target payload and sender ID
     */
    public void handleFollow(Message msg) {
        String requesterNumericId = msg.getSenderId();
        String requesterUsername  = AuthenticationManager.getUsernameByNumericId(requesterNumericId);
        String targetUsername     = msg.getPayload();
        String targetNumericId    = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Follow request error – target username '"
                    + targetUsername + "' not found.");
            return;
        }

        String notif = "User " + requesterUsername + " requested to follow you";

        // Queue offline notification
        NotificationManager.getInstance().addNotification(targetNumericId, notif);

        // Confirm to requester
        ClientHandler requesterHandler = ClientHandler.activeClients.get(requesterNumericId);
        if (requesterHandler != null) {
            requesterHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    "Follow request sent to " + targetUsername
            ));
        }

        // Live prompt if target is online and not the same handler
        ClientHandler targetHandler = ClientHandler.activeClients.get(targetNumericId);
        if (targetHandler != null && targetHandler != requesterHandler) {
            Message followRequest = new Message(
                    MessageType.FOLLOW_REQUEST,
                    "Server",
                    requesterUsername + ":" + requesterNumericId
            );
            targetHandler.sendExternalMessage(followRequest);
            // purge the queued copy
            NotificationManager.getInstance().removeNotification(targetNumericId, notif);
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Sent FOLLOW_REQUEST to " + targetUsername);
        } else if (targetHandler == null) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Target (" + targetUsername + ") not online. Notification queued.");
        }
    }

    /**
     * Processes a follow response by updating the social graph according to
     * "accept", "reciprocate", or "reject" decision, then notifying both parties.
     *
     * @param msg the Message containing requesterUsername:decision payload
     */
    public void handleFollowResponse(Message msg) {
        String payload = msg.getPayload();
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            System.out.println("SocialGraphManager: FOLLOW_RESPONSE received with invalid format.");
            return;
        }

        String requesterUsername = parts[0];
        String decision = parts[1].toLowerCase();
        String requesterNumericId = AuthenticationManager.getClientIdByUsername(requesterUsername);
        if (requesterNumericId == null) {
            System.out.println("SocialGraphManager: FOLLOW_RESPONSE error – requester username '"
                    + requesterUsername + "' not found.");
            return;
        }

        String targetNumericId = msg.getSenderId();
        String targetUsername = AuthenticationManager.getUsernameByNumericId(targetNumericId);

        String requesterNotification;
        String targetConfirmation;

        switch (decision) {
            case "reciprocate":
                socialGraph.putIfAbsent(targetNumericId, new HashSet<>());
                socialGraph.get(targetNumericId).add(requesterNumericId);
                socialGraph.putIfAbsent(requesterNumericId, new HashSet<>());
                socialGraph.get(requesterNumericId).add(targetNumericId);
                requesterNotification = "User " + targetUsername + " reciprocated your follow request";
                targetConfirmation  = "You have reciprocated the follow request from " + requesterUsername;
                break;

            case "accept":
                socialGraph.putIfAbsent(targetNumericId, new HashSet<>());
                socialGraph.get(targetNumericId).add(requesterNumericId);
                requesterNotification = "User " + targetUsername + " accepted your follow request";
                targetConfirmation  = "You have accepted the follow request from " + requesterUsername;
                break;

            case "reject":
                requesterNotification = "User " + targetUsername + " rejected your follow request";
                targetConfirmation  = "You have rejected the follow request from " + requesterUsername;
                break;

            default:
                System.out.println("SocialGraphManager: FOLLOW_RESPONSE received with unknown decision.");
                return;
        }

        // Queue and send notifications
        NotificationManager.getInstance().addNotification(requesterNumericId, requesterNotification);

        // Live push to requester if online, then purge queued copy
        ClientHandler requesterHandler = ClientHandler.activeClients.get(requesterNumericId);
        if (requesterHandler != null) {
            requesterHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    requesterNotification
            ));
            NotificationManager.getInstance()
                    .removeNotification(requesterNumericId, requesterNotification);
        }

        // Immediate confirmation to the responder
        ClientHandler targetHandler = ClientHandler.activeClients.get(targetNumericId);
        if (targetHandler != null) {
            targetHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    targetConfirmation
            ));
        }
    }

    /**
     * Processes an unfollow request by removing the relationship,
     * notifying the requester immediately and the target asynchronously.
     *
     * @param msg the Message containing the target username payload
     */
    public void handleUnfollow(Message msg) {
        String requesterNumericId = msg.getSenderId();
        String requesterUsername = AuthenticationManager.getUsernameByNumericId(requesterNumericId);
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Unfollow request error – target username '"
                    + targetUsername + "' not found.");
            return;
        }

        // Remove requester from target's followers
        Set<String> targetFollowers = socialGraph.get(targetNumericId);
        if (targetFollowers != null && targetFollowers.remove(requesterNumericId)) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: " + requesterUsername + " unfollowed " + targetUsername);
        }

        // Also remove mutual follow if present
        Set<String> requesterFollowers = socialGraph.get(requesterNumericId);
        if (requesterFollowers != null && requesterFollowers.remove(targetNumericId)) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Also removed " + targetUsername
                    + " from " + requesterUsername + "'s followers.");
        }

        // Notify the requester
        ClientHandler requesterHandler = ClientHandler.activeClients.get(requesterNumericId);
        if (requesterHandler != null) {
            requesterHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    "You have unfollowed " + targetUsername
            ));
        }

        String notif = "User " + requesterUsername + " unfollowed you";

        // Queue notification for the target
        NotificationManager.getInstance().addNotification(targetNumericId, notif);

        // If the target is online, push live and purge the queued copy
        ClientHandler targetHandler = ClientHandler.activeClients.get(targetNumericId);
        if (targetHandler != null) {
            targetHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    notif
            ));
            NotificationManager.getInstance().removeNotification(targetNumericId, notif);
        }
    }

    /**
     * Handles a request to list followers by sending back a comma-separated
     * list of usernames of clients who follow the requester.
     *
     * @param msg    the Message with sender indicating whose followers to list
     * @param output the stream to send LIST_FOLLOWERS_RESPONSE
     */
    public void handleListFollowers(Message msg, ObjectOutputStream output) {
        String requesterId = msg.getSenderId();
        Set<String> followers = socialGraph.getOrDefault(requesterId, Collections.emptySet());
        StringBuilder sb = new StringBuilder();
        for (String id : followers) {
            String name = AuthenticationManager.getUsernameByNumericId(id);
            sb.append(name != null ? name : id).append(", ");
        }
        String list = sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "";
        try {
            output.writeObject(new Message(
                    MessageType.LIST_FOLLOWERS_RESPONSE,
                    "Server",
                    list
            ));
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Error sending LIST_FOLLOWERS_RESPONSE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles a request to list followees by sending back a comma-separated
     * list of usernames that the requester is following.
     *
     * @param msg    the Message with sender indicating whose followees to list
     * @param output the stream to send LIST_FOLLOWING_RESPONSE
     */
    public void handleListFollowing(Message msg, ObjectOutputStream output) {
        String requesterId = msg.getSenderId();
        Set<String> followees = getFollowees(requesterId);
        StringBuilder sb = new StringBuilder();
        for (String id : followees) {
            String name = AuthenticationManager.getUsernameByNumericId(id);
            sb.append(name != null ? name : id).append(", ");
        }
        String list = sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "";
        try {
            output.writeObject(new Message(
                    MessageType.LIST_FOLLOWING_RESPONSE,
                    "Server",
                    list
            ));
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Error sending LIST_FOLLOWING_RESPONSE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the set of client IDs that follow the specified client.
     *
     * @param uploaderNumericId the numeric ID of the client whose followers to retrieve
     * @return a Set of follower numeric IDs
     */
    public Set<String> getFollowers(String uploaderNumericId) {
        return socialGraph.getOrDefault(uploaderNumericId, new HashSet<>());
    }
}