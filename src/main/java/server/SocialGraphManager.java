package server;

import common.Message;
import common.Message.MessageType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.*;
import java.util.Scanner;

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
     * Handles a follow request.
     * The payload contains the target’s username.
     * Instead of automatic acceptance, a FOLLOW_REQUEST message is sent to the target client.
     */
    public void handleFollow(Message msg) {
        String targetUsername = msg.getPayload();
        // Convert target username to numeric ID.
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println("SocialGraphManager: Follow request error – target username '" + targetUsername + "' not found.");
            return;
        }
        String requesterNumericId = msg.getSenderId();
        // Get requester username for logging.
        String requesterUsername = getUsernameByNumericId(requesterNumericId);
        String requestPayload = requesterUsername + ":" + requesterNumericId;
        Message followRequest = new Message(MessageType.FOLLOW_REQUEST, "Server", requestPayload);
        // Lookup target's active ClientHandler.
        ClientHandler targetHandler = ClientHandler.activeClients.get(targetNumericId);
        if (targetHandler != null) {
            try {
                targetHandler.getOutputStream().writeObject(followRequest);
                targetHandler.getOutputStream().flush();
                System.out.println("SocialGraphManager: Sent FOLLOW_REQUEST to target (" + targetUsername + "). Awaiting response.");
            } catch (IOException e) {
                System.out.println("SocialGraphManager: Error sending FOLLOW_REQUEST to target: " + e.getMessage());
            }
        } else {
            System.out.println("SocialGraphManager: Target (" + targetUsername + ") not online. Cannot send follow request.");
        }
    }

    /**
     * Handles a follow response from the target client.
     * Expected payload format: "requesterUsername:decision" where decision is one of:
     * reciprocate, accept, or reject.
     */
    public void handleFollowResponse(Message msg) {
        String payload = msg.getPayload();
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            System.out.println("SocialGraphManager: FOLLOW_RESPONSE received with invalid format.");
            return;
        }
        String requesterUsername = parts[0];
        String decision = parts[1].toLowerCase();
        String requesterNumericId = AuthenticationManager.getClientIdByUsername(requesterUsername);
        if (requesterNumericId == null) {
            System.out.println("SocialGraphManager: FOLLOW_RESPONSE error – requester username '" + requesterUsername + "' not found.");
            return;
        }
        String targetNumericId = msg.getSenderId();
        String targetUsername = AuthenticationManager.getUsernameByNumericId(targetNumericId);
        System.out.println("SocialGraphManager: FOLLOW_RESPONSE from target " + targetUsername + " for requester " + requesterUsername + " with decision: " + decision);
        switch(decision) {
            case "reciprocate":
                socialGraph.putIfAbsent(targetNumericId, new HashSet<>());
                socialGraph.get(targetNumericId).add(requesterNumericId);
                socialGraph.putIfAbsent(requesterNumericId, new HashSet<>());
                socialGraph.get(requesterNumericId).add(targetNumericId);
                break;
            case "accept":
                socialGraph.putIfAbsent(targetNumericId, new HashSet<>());
                socialGraph.get(targetNumericId).add(requesterNumericId);
                break;
            case "reject":
                // Do nothing.
                break;
            default:
                System.out.println("SocialGraphManager: FOLLOW_RESPONSE received with unknown decision.");
                break;
        }
    }


    // Handles unfollow requests where the payload contains the target's username.
    public void handleUnfollow(Message msg) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println("SocialGraphManager: Unfollow request error – target username '" + targetUsername + "' not found.");
            return;
        }
        String requesterNumericId = msg.getSenderId();

        // Remove the requester from the target's followers.
        Set<String> targetFollowers = socialGraph.get(targetNumericId);
        if (targetFollowers != null) {
            targetFollowers.remove(requesterNumericId);
            System.out.println("SocialGraphManager: Client " + requesterNumericId + " unfollowed target " + targetNumericId + " (username: " + targetUsername + ")");
        }

        // Additionally, remove the target from the requester's followers to break mutual follow, if present.
        Set<String> requesterFollowers = socialGraph.get(requesterNumericId);
        if (requesterFollowers != null && requesterFollowers.contains(targetNumericId)) {
            requesterFollowers.remove(targetNumericId);
            System.out.println("SocialGraphManager: Also removed target " + targetNumericId + " from requester " + requesterNumericId + "'s follower list.");
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
