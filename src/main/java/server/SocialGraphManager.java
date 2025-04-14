package server;

import common.Message;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.*;
import java.util.Scanner;

public class SocialGraphManager {
    private static SocialGraphManager instance = null;

    // Maps client IDs to sets of follower IDs.
    private HashMap<String, Set<String>> socialGraph = new HashMap<>();

    private SocialGraphManager() {}

    public static SocialGraphManager getInstance() {
        if (instance == null) {
            instance = new SocialGraphManager();
        }
        return instance;
    }

    // Load the social graph from a file.
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
            System.out.println("Social graph loaded successfully.");
        } catch (FileNotFoundException e) {
            System.out.println("SocialGraph file not found: " + e.getMessage());
        }
    }

    // Returns true if the requester is following the target.
    public boolean isFollowing(String requesterId, String targetId) {
        Set<String> followers = socialGraph.get(targetId);
        return followers != null && followers.contains(requesterId);
    }

    // Process a follow request.
    public void handleFollow(Message msg) {
        String targetId = msg.getPayload();
        String requesterId = msg.getSenderId();
        socialGraph.putIfAbsent(targetId, new HashSet<>());
        socialGraph.get(targetId).add(requesterId);
        System.out.println("Client " + requesterId + " now follows " + targetId);
    }

    // Process an unfollow request.
    public void handleUnfollow(Message msg) {
        String targetId = msg.getPayload();
        String requesterId = msg.getSenderId();
        Set<String> followers = socialGraph.get(targetId);
        if (followers != null) {
            followers.remove(requesterId);
            System.out.println("Client " + requesterId + " unfollowed " + targetId);
        }
    }

    // Search for a photo by checking if it exists in the ServerFiles directory.
    public String searchPhoto(String photoName, String requesterId) {
        File file = new File("ServerFiles/" + photoName);
        if (file.exists()) {
            return "Found photo " + photoName + " at clientID dummyOwner";
        } else {
            return "Photo " + photoName + " not found.";
        }
    }
}
