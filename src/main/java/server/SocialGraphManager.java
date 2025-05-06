package server;

import common.Message;
import common.Message.MessageType;
import common.Util;

import java.util.*;
import java.io.*;

public class SocialGraphManager {
    private static SocialGraphManager instance = null;

    // Maps numeric client IDs (as Strings) to sets of follower numeric IDs.
    private HashMap<String, Set<String>> socialGraph = new HashMap<>();

    private SocialGraphManager() { }

    public static SocialGraphManager getInstance() {
        if (instance == null) {
            instance = new SocialGraphManager();
        }
        return instance;
    }

    // Loads the social graph from a predetermined file (with numeric IDs).
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

    // Checks if the requester (numeric ID) follows the target (numeric ID).
    public boolean isFollowing(String requesterId, String targetId) {
        Set<String> followers = socialGraph.get(targetId);
        boolean result = followers != null && followers.contains(requesterId);
        System.out.println("SocialGraphManager: isFollowing(" + requesterId + ", " + targetId + ") = " + result);
        return result;
    }

    /**
     * Returns the set of clientIds that the given client follows.
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
     * Handles a follow request.
     * Notifies the target (immediately if online, or upon next login) and
     * confirms to the requester.
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

        // 1) Queue offline notification
        NotificationManager.getInstance().addNotification(
                targetNumericId,
                "User " + requesterUsername + " requested to follow you"
        );

        // 2) Confirm to the requester
        ClientHandler requesterHandler = ClientHandler.activeClients.get(requesterNumericId);
        if (requesterHandler != null) {
            requesterHandler.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    "Follow request sent to " + targetUsername
            ));
        }

        // 3) If the target is online *and* is a different handler than the requester, push live
        ClientHandler targetHandler = ClientHandler.activeClients.get(targetNumericId);
        if (targetHandler != null && targetHandler != requesterHandler) {
            Message followRequest = new Message(
                    MessageType.FOLLOW_REQUEST,
                    "Server",
                    requesterUsername + ":" + requesterNumericId
            );
            targetHandler.sendExternalMessage(followRequest);
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Sent FOLLOW_REQUEST to " + targetUsername);
        } else if (targetHandler == null) {
            System.out.println(Util.getTimestamp()
                    + " SocialGraphManager: Target (" + targetUsername + ") not online. Notification queued.");
        }
    }

    /**
     * Handles a follow response from the target client.
     * Expected payload format: "requesterUsername:decision"
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

        // Queue notification for the requester; delivered upon their next login
        NotificationManager.getInstance().addNotification(requesterNumericId, requesterNotification);

        // Immediate confirmation to the target (responder)
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
     * Handles an unfollow request.
     * Updates the graph, notifies the requester immediately, and queues a notification
     * for the target.
     */
    public void handleUnfollow(Message msg) {
        String requesterNumericId = msg.getSenderId();
        String requesterUsername = AuthenticationManager.getUsernameByNumericId(requesterNumericId);
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println(Util.getTimestamp() + " SocialGraphManager: Unfollow request error – target username '" + targetUsername + "' not found.");
            return;
        }

        // Remove requester from target's followers
        Set<String> targetFollowers = socialGraph.get(targetNumericId);
        if (targetFollowers != null && targetFollowers.remove(requesterNumericId)) {
            System.out.println(Util.getTimestamp() + " SocialGraphManager: " + requesterUsername + " unfollowed " + targetUsername);
        }

        // Also remove mutual follow if present
        Set<String> requesterFollowers = socialGraph.get(requesterNumericId);
        if (requesterFollowers != null && requesterFollowers.remove(targetNumericId)) {
            System.out.println(Util.getTimestamp() + " SocialGraphManager: Also removed " + targetUsername + " from " + requesterUsername + "'s followers.");
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

        // Queue notification for the target
        NotificationManager.getInstance().addNotification(
                targetNumericId,
                "User " + requesterUsername + " unfollowed you"
        );
    }

    /**
     * NEW: Handle client's request to list their followers.
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
     * NEW: Handle client's request to list who they follow.
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

    // Improved search: checks for file existence in the ServerFiles directory.
    public String searchPhoto(String photoName, String requesterNumericId) {
        File file = new File("ServerFiles/" + photoName);
        if (file.exists()) {
            String result = "Found photo " + photoName + " at clientID dummyOwner";
            System.out.println("SocialGraphManager: SEARCH found file " + photoName);
            return result;
        } else {
            String result = "Photo " + photoName + " not found.";
            System.out.println("SocialGraphManager: SEARCH did not find file " + photoName);
            return result;
        }
    }

    // Handles access_profile where the payload contains the target's username.
    public static void handleAccessProfile(Message msg, String requesterNumericId, ObjectOutputStream output) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access_profile failed: User '" + targetUsername + "' not found."));
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        boolean allowed = getInstance().isFollowing(requesterNumericId, targetNumericId);
        try {
            if (allowed) {
                String profileContent = ProfileManager.getInstance().getProfile(targetNumericId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access granted. " + profileContent));
                System.out.println("SocialGraphManager: Access_profile granted for requester " + requesterNumericId + " to target " + targetNumericId);
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access denied: You do not follow user '" + targetUsername + "'."));
                System.out.println("SocialGraphManager: Access_profile denied for requester " + requesterNumericId + " to target " + targetNumericId);
            }
            output.flush();
        } catch (IOException e) {
            System.out.println("SocialGraphManager: Error handling access_profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method: Given a numeric id, retrieve its corresponding username.
    private String getUsernameByNumericId(String numericId) {
        for (ClientRecord record : AuthenticationManager.getAllClientRecords()) {
            if (record.numericId.equals(numericId)) {
                return record.username;
            }
        }
        return null;
    }

    // Helper method: Given a numeric id, retrieve its corresponding client record.
    public Set<String> getFollowers(String uploaderNumericId) {
        return socialGraph.getOrDefault(uploaderNumericId, new HashSet<>());
    }

}
