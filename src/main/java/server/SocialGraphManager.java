package server;

import common.Message;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.*;
import java.util.Scanner;

public class SocialGraphManager {
    private static SocialGraphManager instance = null;

    // Maps client IDs to their set of followers
    private HashMap<String, Set<String>> socialGraph = new HashMap<>();

    private SocialGraphManager() {}

    public static SocialGraphManager getInstance() {
        if (instance == null) {
            instance = new SocialGraphManager();
        }
        return instance;
    }

    // Loads the social graph from the provided file.
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

    // Processes follow requests.
    public void handleFollow(Message msg) {
        String targetId = msg.getPayload();
        String requesterId = msg.getSenderId();
        socialGraph.computeIfAbsent(targetId, k -> new HashSet<>()).add(requesterId);
        System.out.println("Client " + requesterId + " now follows " + targetId);
    }

    // Processes unfollow requests.
    public void handleUnfollow(Message msg) {
        String targetId = msg.getPayload();
        String requesterId = msg.getSenderId();
        Set<String> followers = socialGraph.get(targetId);
        if (followers != null) {
            followers.remove(requesterId);
            System.out.println("Client " + requesterId + " unfollowed " + targetId);
        }
    }

    // A stub for searching a photo in the social graph.
    public String searchPhoto(String photoName, String requesterId) {
        // In a full implementation, search through the directories of the clients
        // that are in the requester’s social graph.
        return "Found photo " + photoName + " at clientID 1";
    }
}
