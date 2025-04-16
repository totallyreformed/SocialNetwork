package server;

import common.Constants;
import common.Message;
import common.Message.MessageType;
import common.Util;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.util.Queue;
import java.io.ObjectOutputStream;

public class ProfileManager {
    // Map to track locked profiles (key: clientId, value: true if locked)
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    // Map to track waiting clients for each profile.
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    // Map to track timers for each locked profile.
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    private static ProfileManager instance = null;

    private ProfileManager() { }

    public static ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    /**
     * Attempts to lock the profile file corresponding to clientId for requesterId.
     * If the profile is already locked, request is queued.
     * @return true if lock acquired; false otherwise.
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
            timer.schedule(new TimerTask(){
                @Override
                public void run() {
                    System.out.println(Util.getTimestamp() + " ProfileManager: Lock timeout for profile "
                            + clientId + " reached. Releasing lock.");
                    unlockProfile(clientId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(clientId, timer);
            System.out.println(Util.getTimestamp() + " ProfileManager: Profile " + clientId + " locked.");
            return true;
        }
    }

    /**
     * Unlocks the profile file for clientId and notifies any waiting clients.
     */
    public synchronized void unlockProfile(String clientId) {
        locks.remove(clientId);
        Timer timer = timers.remove(clientId);
        if (timer != null) timer.cancel();
        System.out.println(Util.getTimestamp() + " ProfileManager: Profile " + clientId + " unlocked.");
        if (waitingQueues.containsKey(clientId)) {
            Queue<String> queue = waitingQueues.get(clientId);
            if (!queue.isEmpty()) {
                String nextClient = queue.poll();
                ClientHandler handler = ClientHandler.activeClients.get(nextClient);
                if (handler != null) {
                    try {
                        handler.getOutputStream().writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Profile " + clientId + " is now available."));
                        handler.getOutputStream().flush();
                    } catch (IOException e) {
                        System.out.println(Util.getTimestamp() + " ProfileManager: Error notifying client " + nextClient);
                    }
                }
            }
        }
    }

    /**
     * Updates a profile file by appending new content (e.g. a photo post).
     * The profile file is first locked, updated, then unlocked.
     */
    public synchronized void updateProfile(String clientId, String content) {
        // Attempt to lock the profile for exclusive update.
        if (!lockProfile(clientId, clientId)) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Unable to acquire lock on profile " + clientId);
            return;
        }
        // Retrieve the username from internal records.
        String username = AuthenticationManager.getUsernameByNumericId(clientId);
        String fileName = "Profile_" + clientId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write("[" + Util.getTimestamp() + "] " + username + " posted " + content + "\n");
            System.out.println(Util.getTimestamp() + " ProfileManager: Updated " + fileName
                    + " with: " + username + " posted " + content);
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error updating profile " + fileName);
            e.printStackTrace();
        }
        // Release the lock after update.
        unlockProfile(clientId);
    }

    /**
     * Adds a comment to the target user's profile.
     * The profile is locked during the update and then unlocked.
     */
    public synchronized void addComment(String targetId, String commenterId, String comment) {
        if (!lockProfile(targetId, commenterId)) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Unable to lock profile " + targetId
                    + " for commenting.");
            return;
        }
        // Retrieve commenter's username.
        String commenterName = AuthenticationManager.getUsernameByNumericId(commenterId);
        // Retrieve target's username using targetId.
        String targetName = AuthenticationManager.getUsernameByNumericId(targetId);
        // Here we assume the profile file is named using the target's numeric id.
        String fileName = "Profile_" + targetId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            String logEntry = "[" + Util.getTimestamp() + "] Comment from " + commenterName + ": " + comment;
            fw.write(logEntry + "\n");
            System.out.println(Util.getTimestamp() + " ProfileManager: Appended comment to " + fileName
                    + ": " + logEntry);
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error appending comment to " + fileName);
            e.printStackTrace();
        }
        unlockProfile(targetId);
    }

    /**
     * Retrieves profile content. For simulation, returns a simple string.
     */
    public synchronized String getProfile(String clientId) {
        System.out.println(Util.getTimestamp() + " ProfileManager: Retrieving profile for client " + clientId);
        return "Profile content for client " + clientId;
    }

    /**
     * Handles access_profile requests.
     */
    public static void handleAccessProfile(Message msg, String requesterNumericId, ObjectOutputStream output) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if (targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access_profile failed: User '"
                        + targetUsername + "' not found."));
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterNumericId, targetNumericId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetNumericId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access granted. " + profileContent));
                System.out.println(Util.getTimestamp() + " ProfileManager: Access granted for requester "
                        + requesterNumericId + " to profile " + targetNumericId);
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access denied: You do not follow user '"
                        + targetUsername + "'."));
                System.out.println(Util.getTimestamp() + " ProfileManager: Access denied for requester "
                        + requesterNumericId + " to profile " + targetNumericId);
            }
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error handling access_profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles repost requests.
     */
    public static void handleRepost(Message msg, String requesterNumericId, ObjectOutputStream output) {
        String postContent = msg.getPayload();
        String othersFileName = "Others_Profile_" + requesterNumericId + ".txt";
        try (FileWriter fw = new FileWriter(new File(othersFileName), true)) {
            fw.write("[" + Util.getTimestamp() + "] Repost: " + postContent + "\n");
            System.out.println(Util.getTimestamp() + " ProfileManager: Client " + requesterNumericId
                    + " reposted: " + postContent + " to " + othersFileName);
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Repost successful to " + othersFileName));
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ProfileManager: Error in repost for client " + requesterNumericId);
            e.printStackTrace();
        }
    }
}
