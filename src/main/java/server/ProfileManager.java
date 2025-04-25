// server/ProfileManager.java
package server;

import common.Constants;
import common.Message;
import common.Message.MessageType;
import common.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileManager {
    // --- Locking infrastructure for concurrent profile access ---
    private ConcurrentHashMap<String, Boolean> locks         = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Timer>    timers        = new ConcurrentHashMap<>();

    // --- Counters to assign unique post IDs per client ---
    private ConcurrentHashMap<String, AtomicInteger> postIdCounters = new ConcurrentHashMap<>();

    private static ProfileManager instance = null;
    private ProfileManager() { }
    public static ProfileManager getInstance() {
        if (instance == null) instance = new ProfileManager();
        return instance;
    }

    /**
     * Lock the profile for exclusive access; queue up others.
     */
    public synchronized boolean lockProfile(String clientId, String requesterId) {
        if (locks.containsKey(clientId)) {
            waitingQueues.putIfAbsent(clientId, new LinkedList<>());
            waitingQueues.get(clientId).offer(requesterId);
            System.out.println(Util.getTimestamp() + " ProfileManager: Client " + requesterId
                    + " queued for profile " + clientId);
            return false;
        } else {
            locks.put(clientId, true);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override public void run() {
                    System.out.println(Util.getTimestamp()
                            + " ProfileManager: Lock timeout for profile " + clientId);
                    unlockProfile(clientId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(clientId, timer);
            System.out.println(Util.getTimestamp() + " ProfileManager: Profile " + clientId + " locked.");
            return true;
        }
    }

    /**
     * Unlock and notify next waiter.
     */
    public synchronized void unlockProfile(String clientId) {
        locks.remove(clientId);
        Timer t = timers.remove(clientId);
        if (t != null) t.cancel();
        System.out.println(Util.getTimestamp() + " ProfileManager: Profile " + clientId + " unlocked.");
        Queue<String> q = waitingQueues.get(clientId);
        if (q != null && !q.isEmpty()) {
            String next = q.poll();
            ClientHandler handler = ClientHandler.activeClients.get(next);
            if (handler != null) {
                try {
                    handler.getOutputStream().writeObject(
                            new Message(MessageType.DIAGNOSTIC, "Server",
                                    "Profile " + clientId + " is now available."));
                    handler.getOutputStream().flush();
                } catch (IOException e) {
                    System.out.println(Util.getTimestamp()
                            + " ProfileManager: Error notifying client " + next);
                }
            }
        }
    }

    /**
     * Append a new post: assigns a unique postId and writes it.
     */
    public synchronized void updateProfile(String clientId, String content) {
        if (!lockProfile(clientId, clientId)) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Unable to acquire lock on profile " + clientId);
            return;
        }
        String username = AuthenticationManager.getUsernameByNumericId(clientId);
        AtomicInteger ctr = postIdCounters.computeIfAbsent(clientId, k -> new AtomicInteger(1));
        int postId = ctr.getAndIncrement();

        String fileName = "Profile_" + Constants.GROUP_ID + "client" + clientId + ".txt";
        String entry    = "PostID:" + postId
                + " [" + Util.getTimestamp() + "] "
                + username + " posted " + content + "\n";

        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(entry);
            System.out.println(Util.getTimestamp() + " ProfileManager: Updated " + fileName
                    + " with: " + entry.trim());
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error writing to " + fileName);
            e.printStackTrace();
        }
        unlockProfile(clientId);
    }

    /**
     * Add a comment to a specific post by postId, then queue notifications
     * for the original author and all of their followers.
     */
    public synchronized void addCommentToPost(String targetId,
                                              String postId,
                                              String commenterId,
                                              String comment) {
        if (!lockProfile(targetId, commenterId)) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Unable to lock profile " + targetId + " for commenting.");
            return;
        }
        String commenterName = AuthenticationManager.getUsernameByNumericId(commenterId);
        String fileName = "Profile_" + Constants.GROUP_ID + "client" + targetId + ".txt";
        String logEntry = "[" + Util.getTimestamp() + "] Comment on post "
                + postId + " from " + commenterName + ": " + comment + "\n";

        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(logEntry);
            System.out.println(Util.getTimestamp() + " ProfileManager: Appended comment to "
                    + fileName + ": " + logEntry.trim());
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error appending comment to "
                    + fileName);
            e.printStackTrace();
        }

        // Prepare notification text
        String notif = "New comment on post " + postId
                + " from " + commenterName + ": " + comment;

        // Queue for original author
        NotificationManager.getInstance().addNotification(targetId, notif);

        // Queue for all followers (except the commenter)
        Set<String> followers = SocialGraphManager.getInstance().getFollowers(targetId);
        for (String followerId : followers) {
            if (!followerId.equals(commenterId)) {
                NotificationManager.getInstance().addNotification(followerId, notif);
            }
        }

        unlockProfile(targetId);
    }

    /**
     * Simulate retrieval of profile content if needed.
     */
    public synchronized String getProfile(String clientId) {
        System.out.println(Util.getTimestamp() + " ProfileManager: Retrieving profile for client "
                + clientId);
        return "Profile content for client " + clientId;
    }

    /**
     * Handle access_profile requests unchanged.
     */
    public static void handleAccessProfile(Message msg,
                                           String requesterNumericId,
                                           ObjectOutputStream output) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Access_profile failed: User '" + targetUsername + "' not found."));
                output.flush();
            } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        boolean allowed = SocialGraphManager.getInstance()
                .isFollowing(requesterNumericId, targetNumericId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetNumericId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Access granted. " + profileContent));
                System.out.println(Util.getTimestamp() + " ProfileManager: Access granted for requester "
                        + requesterNumericId + " to profile " + targetNumericId);
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Access denied: You do not follow user '" + targetUsername + "'." ));
                System.out.println(Util.getTimestamp() + " ProfileManager: Access denied for requester "
                        + requesterNumericId + " to profile " + targetNumericId);
            }
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error handling access_profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle repost requests: append to server's Others file, notify reposting client,
     * instruct client to sync locally, and queue a notification for the original author.
     */
    public static void handleRepost(Message msg,
                                    String requesterNumericId,
                                    ObjectOutputStream output) {
        String payload = msg.getPayload();
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Repost failed: Invalid format. Use target_username:postId"));
                output.flush();
            } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        String targetUsername = parts[0];
        String postId         = parts[1];
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Repost failed: User '" + targetUsername + "' not found."));
                output.flush();
            } catch (IOException e) { e.printStackTrace(); }
            return;
        }

        // Read original post
        String profileFileName = "Profile_" + Constants.GROUP_ID + "client" + targetNumericId + ".txt";
        String originalLine = "";
        try (BufferedReader br = new BufferedReader(new FileReader(profileFileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("PostID:" + postId + " ")) {
                    originalLine = line;
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error reading profile file " + profileFileName);
            e.printStackTrace();
        }
        if (originalLine.isEmpty()) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Repost failed: Post " + postId + " not found for user " + targetUsername));
                output.flush();
            } catch (IOException e) { e.printStackTrace(); }
            return;
        }

        // Append to server-side Others file
        String othersFileName = "Others_" + Constants.GROUP_ID + "client" + requesterNumericId + ".txt";
        String entry = "[" + Util.getTimestamp() + "] Repost of post "
                + postId + " from " + targetUsername
                + ": " + originalLine;
        try (FileWriter fw = new FileWriter(new File(othersFileName), true)) {
            fw.write(entry + "\n");
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Client " + requesterNumericId
                    + " reposted post " + postId
                    + " from user " + targetUsername);
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error in repost for client " + requesterNumericId);
            e.printStackTrace();
        }

        try {
            // Notify reposting client of success
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "Repost successful: post " + postId
                            + " from " + targetUsername
                            + " added to your Others file."));
            output.flush();

            // Instruct client to sync its local Others file
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "SYNC_REPOST:" + entry));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Queue notification for the original author
        String reposterName = AuthenticationManager.getUsernameByNumericId(requesterNumericId);
        String notif = "Your post " + postId
                + " was reposted by " + reposterName;
        NotificationManager.getInstance().addNotification(targetNumericId, notif);
    }
}
