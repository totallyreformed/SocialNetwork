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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages user profile operations including locking for concurrent access,
 * appending posts and comments, handling profile viewing, and reposts.
 * Uses fine-grained locking, timeout warnings, and notification queuing.
 */
public class ProfileManager {

    // --- Locking infrastructure for concurrent profile access ---
    /** Indicates locked state per client profile. */
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> lockOwners = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    /** Timers to enforce lock timeouts and warnings. */
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    // --- Counters to assign unique post IDs per client ---
    /** Per-client counters to generate unique post IDs. */
    private ConcurrentHashMap<String, AtomicInteger> postIdCounters = new ConcurrentHashMap<>();

    /** Singleton instance. */
    private static ProfileManager instance = null;

    /** Private constructor for singleton pattern. */
    private ProfileManager() { }

    /**
     * Returns the singleton ProfileManager instance.
     * @return the ProfileManager instance
     */
    public static ProfileManager getInstance() {
        if (instance == null) instance = new ProfileManager();
        return instance;
    }

    /**
     * Attempts to lock the specified client profile for exclusive access.
     * If already locked, queues requester and sends a diagnostic denial.
     * Issues a timeout warning and automatic unlock after configured delay.
     *
     * @param clientId     the profile owner ID
     * @param requesterId  the requesting client ID
     * @return true if lock acquired; false if queued for later
     */
    public synchronized boolean lockProfile(String clientId, String requesterId) {
        if (locks.containsKey(clientId)) {
            waitingQueues.putIfAbsent(clientId, new LinkedList<>());
            waitingQueues.get(clientId).offer(requesterId);
            // Notify denial to requester
            ClientHandler handler = ClientHandler.activeClients.get(requesterId);
            if (handler != null) {
                try {
                    handler.getOutputStream().writeObject(
                            new Message(MessageType.DIAGNOSTIC, "Server",
                                    "Profile locked—please retry later"));
                    handler.getOutputStream().flush();
                } catch (IOException ignored) { }
            }
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Client " + requesterId
                    + " queued for profile " + clientId);
            return false;
        } else {
            locks.put(clientId, true);
            lockOwners.put(clientId, requesterId);
            // Schedule timeout warning and auto-unlock
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Warn lock owner
                    String owner = lockOwners.get(clientId);
                    ClientHandler ownerHandler = ClientHandler.activeClients.get(owner);
                    if (ownerHandler != null) {
                        try {
                            ownerHandler.getOutputStream().writeObject(
                                    new Message(MessageType.DIAGNOSTIC, "Server",
                                            "Warning: your lock on Profile_"
                                                    + Constants.GROUP_ID + "client" + clientId
                                                    + " has timed out and will be released."));
                            ownerHandler.getOutputStream().flush();
                        } catch (IOException ignored) { }
                    }
                    // Actually release the lock
                    System.out.println(Util.getTimestamp()
                            + " ProfileManager: Lock timeout for profile " + clientId);
                    unlockProfile(clientId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(clientId, timer);

            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Profile " + clientId + " locked.");
            return true;
        }
    }

    /**
     * Releases the lock on the given profile and notifies the next waiting client.
     * Automatically grants the lock to that client.
     *
     * @param clientId the profile owner ID
     */
    public synchronized void unlockProfile(String clientId) {
        locks.remove(clientId);
        lockOwners.remove(clientId);
        Timer t = timers.remove(clientId);
        if (t != null) t.cancel();
        System.out.println(Util.getTimestamp() + " ProfileManager: Profile " + clientId + " unlocked.");

        Queue<String> q = waitingQueues.get(clientId);
        if (q != null && !q.isEmpty()) {
            String next = q.poll();
            // Automatically acquire lock for next client
            lockProfile(clientId, next);

            // Notify next that the profile is available
            ClientHandler handler = ClientHandler.activeClients.get(next);
            if (handler != null) {
                try {
                    handler.getOutputStream().writeObject(
                            new Message(MessageType.DIAGNOSTIC, "Server",
                                    "Profile is now available"));
                    handler.getOutputStream().flush();
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Appends a new post entry to the owner's profile file with a unique post ID.
     * Uses locking to ensure exclusive write access and releases lock afterward.
     *
     * @param clientId the owner client ID
     * @param content  the post content text
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

        // Ensure server-side per-client directory exists
        String dirPath = "ServerFiles/" + Constants.GROUP_ID + "client" + clientId;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Write into that directory
        String fileName = dirPath + "/Profile_" + Constants.GROUP_ID + "client" + clientId + ".txt";
        String entry = "PostID:" + postId
                + " [" + Util.getTimestamp() + "] "
                + username + " posted " + content + "\n";

        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(entry);
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Updated " + fileName + " with: " + entry.trim());
            // mark event so DirectoryWatcher skips this change
            SyncRegistry.markEvent(new File(fileName).toPath());
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error writing to " + fileName);
            e.printStackTrace();
        } finally {
            unlockProfile(clientId);
        }
    }

    /**
     * Appends a comment to a specific post in a target user's profile and
     * sends notifications to the post author and their followers.
     * @param targetId      the profile owner ID
     * @param postId        the ID of the post being commented on
     * @param commenterId   the client ID of the commenter
     * @param comment       the comment text
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

        try { Thread.sleep(10000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        String commenterName = AuthenticationManager.getUsernameByNumericId(commenterId);

        // Ensure server-side per-client directory exists
        String dirPath = "ServerFiles/" + Constants.GROUP_ID + "client" + targetId;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = dirPath + "/Profile_" + Constants.GROUP_ID + "client" + targetId + ".txt";
        String logEntry = "[" + Util.getTimestamp() + "] Comment on post "
                + postId + " from " + commenterName + ": " + comment + "\n";

        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(logEntry);
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Appended comment to " + fileName);
            // mark event so DirectoryWatcher skips this change
            SyncRegistry.markEvent(new File(fileName).toPath());
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error appending comment to " + fileName);
            e.printStackTrace();
        }

        // Notify author and followers
        String notif = "New comment on post " + postId
                + " from " + commenterName + ": " + comment;

        // Author: queue, live push & purge
        NotificationManager.getInstance().addNotification(targetId, notif);
        ClientHandler authorH = ClientHandler.activeClients.get(targetId);
        if (authorH != null) {
            authorH.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    notif
            ));
            NotificationManager.getInstance()
                    .removeNotification(targetId, notif);
        }

        // Followers: queue, live push & purge
        Set<String> followers = SocialGraphManager.getInstance().getFollowers(targetId);
        for (String fid : followers) {
            if (!fid.equals(commenterId)) {
                NotificationManager.getInstance().addNotification(fid, notif);
                ClientHandler fh = ClientHandler.activeClients.get(fid);
                if (fh != null) {
                    fh.sendExternalMessage(new Message(
                            MessageType.DIAGNOSTIC,
                            "Server",
                            notif
                    ));
                    NotificationManager.getInstance()
                            .removeNotification(fid, notif);
                }
            }
        }

        unlockProfile(targetId);
    }

    /**
     * Handles access_profile requests by verifying follow relationship,
     * reading and parsing the target's profile file, and streaming posts
     * and their associated comments back to the requester.
     *
     * @param msg                  the access_profile Message
     * @param requesterNumericId   the numeric ID of the requesting client
     * @param output               the ObjectOutputStream to send messages
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
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        boolean allowed = SocialGraphManager.getInstance()
                .isFollowing(requesterNumericId, targetNumericId);

        try {
            if (!allowed) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Access denied: You do not follow user '" + targetUsername + "'."));
                output.flush();
                return;
            }

            // Ensure server-side per-client directory exists
            String dirPath = "ServerFiles/" + Constants.GROUP_ID + "client" + targetNumericId;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = dirPath + "/Profile_" + Constants.GROUP_ID
                    + "client" + targetNumericId + ".txt";
            File profileFile = new File(fileName);

            if (!profileFile.exists() || profileFile.length() == 0) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Access granted. Profile is empty."));
                output.flush();
                return;
            }

            // Read all lines
            List<String> lines = Files.readAllLines(profileFile.toPath(), StandardCharsets.UTF_8);

            // First pass: collect comments per postId
            Map<Integer, List<String>> commentMap = new HashMap<>();
            for (String line : lines) {
                if (line.contains("Comment on post ")) {
                    int idx = line.indexOf("Comment on post ") + "Comment on post ".length();
                    int end = line.indexOf(' ', idx);
                    try {
                        int postId = Integer.parseInt(line.substring(idx, end));
                        commentMap.computeIfAbsent(postId, k -> new ArrayList<>()).add(line);
                    } catch (NumberFormatException ignored) { }
                }
            }

            // Second pass: output posts with their comments
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access granted."));
            for (String line : lines) {
                if (line.startsWith("PostID:")) {
                    int spaceIdx = line.indexOf(' ');
                    int postId = Integer.parseInt(line.substring("PostID:".length(), spaceIdx));
                    output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                            "Uploaded post " + postId + ": " + line));

                    List<String> comms = commentMap.get(postId);
                    if (comms == null || comms.isEmpty()) {
                        output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                "  (no comments)"));
                    } else {
                        for (String c : comms) {
                            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                    "  " + c));
                        }
                    }
                }
            }
            output.flush();

        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error handling access_profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles repost requests by copying the original post into the requester’s
     * Others file, instructing the client to sync locally, and notifying the original author.
     *
     * @param msg                    the repost Message containing target_username:postId
     * @param requesterNumericId     the numeric ID of the reposting client
     * @param output                 the ObjectOutputStream to send responses
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
        String targetUsername   = parts[0];
        String postId           = parts[1];
        String targetNumericId  = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Repost failed: User '" + targetUsername + "' not found."));
                output.flush();
            } catch (IOException e) { e.printStackTrace(); }
            return;
        }

        // Read original post from the server-side per-client directory
        String profilePath = "ServerFiles/" + Constants.GROUP_ID
                + "client" + targetNumericId
                + "/Profile_" + Constants.GROUP_ID + "client" + targetNumericId + ".txt";
        String originalLine = "";
        try (BufferedReader br = new BufferedReader(new FileReader(profilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("PostID:" + postId + " ")) {
                    originalLine = line;
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Error reading profile file " + profilePath);
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

        // Ensure server-side per-client directory exists for requester
        String dirPath = "ServerFiles/" + Constants.GROUP_ID + "client" + requesterNumericId;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Append to server-side Others file
        String othersFileName = dirPath + "/Others_" + Constants.GROUP_ID
                + "client" + requesterNumericId + ".txt";
        String entry = "[" + Util.getTimestamp() + "] Repost of post "
                + postId + " from " + targetUsername + ": " + originalLine;
        try (FileWriter fw = new FileWriter(new File(othersFileName), true)) {
            fw.write(entry + "\n");
            System.out.println(Util.getTimestamp()
                    + " ProfileManager: Client " + requesterNumericId
                    + " updated Others file with repost.");
            // mark event so DirectoryWatcher skips this change
            SyncRegistry.markEvent(new File(othersFileName).toPath());
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
        String notif = "Your post " + postId + " was reposted by " +
                AuthenticationManager.getUsernameByNumericId(requesterNumericId);

        // Queue & live push to original author, then purge
        NotificationManager.getInstance().addNotification(targetNumericId, notif);
        ClientHandler oh = ClientHandler.activeClients.get(targetNumericId);
        if (oh != null) {
            oh.sendExternalMessage(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    notif
            ));
            NotificationManager.getInstance()
                    .removeNotification(targetNumericId, notif);
        }
    }
}