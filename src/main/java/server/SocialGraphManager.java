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

    // Loads the social graph from a predetermined file.
    // The file uses numeric client ids.
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

    // Checks if the requester (by numeric id) is following the target.
    public boolean isFollowing(String requesterId, String targetId) {
        Set<String> followers = socialGraph.get(targetId);
        boolean result = followers != null && followers.contains(requesterId);
        System.out.println("SocialGraphManager: isFollowing(" + requesterId + ", " + targetId + ") = " + result);
        return result;
    }

    // Handle a follow request.
    // Payload now contains the target’s username.
    public void handleFollow(Message msg) {
        String targetUsername = msg.getPayload();
        // Convert target username to numeric id.
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println("SocialGraphManager: Follow request error – target username '" + targetUsername + "' not found.");
            return;
        }
        // Requester's numeric id is in the sender field.
        String requesterNumericId = msg.getSenderId();

        // Add the requester as a follower of the target.
        socialGraph.putIfAbsent(targetNumericId, new HashSet<>());
        socialGraph.get(targetNumericId).add(requesterNumericId);
        System.out.println("SocialGraphManager: Client " + requesterNumericId + " now follows target " + targetNumericId + " (username: " + targetUsername + ")");
    }

    // Handle an unfollow request.
    // Payload contains the target’s username.
    public void handleUnfollow(Message msg) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            System.out.println("SocialGraphManager: Unfollow request error – target username '" + targetUsername + "' not found.");
            return;
        }
        String requesterNumericId = msg.getSenderId();
        Set<String> followers = socialGraph.get(targetNumericId);
        if (followers != null) {
            followers.remove(requesterNumericId);
            System.out.println("SocialGraphManager: Client " + requesterNumericId + " unfollowed target " + targetNumericId + " (username: " + targetUsername + ")");
        }
    }

    // Handle access_profile request.
    // The payload contains the target’s username.
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

    // Improved search: checks for file existence in ServerFiles.
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
}
