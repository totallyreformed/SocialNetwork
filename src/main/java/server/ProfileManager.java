package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.util.Queue;
import common.Constants;
import common.Message;
import common.Message.MessageType;
import java.io.ObjectOutputStream;

public class ProfileManager {
    // Track locked profiles: ownerId -> locked flag.
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    // Waiting queues for each profile.
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    // Timers for locks.
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private static ProfileManager instance = null;

    private ProfileManager() { }

    public static ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    // Attempt to lock profile of ownerId for requesterId.
    public synchronized boolean lockProfile(String ownerId, String requesterId) {
        if (locks.containsKey(ownerId)) {
            waitingQueues.putIfAbsent(ownerId, new LinkedList<>());
            waitingQueues.get(ownerId).offer(requesterId);
            System.out.println("ProfileManager: Client " + requesterId + " is queued for profile " + ownerId + ".");
            return false;
        } else {
            locks.put(ownerId, true);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("ProfileManager: Lock timeout reached for profile " + ownerId + ". Releasing lock.");
                    unlockProfile(ownerId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(ownerId, timer);
            System.out.println("ProfileManager: Profile " + ownerId + " locked for editing.");
            return true;
        }
    }

    public synchronized void unlockProfile(String ownerId) {
        locks.remove(ownerId);
        Timer timer = timers.remove(ownerId);
        if (timer != null) timer.cancel();
        System.out.println("ProfileManager: Profile " + ownerId + " unlocked.");
        if (waitingQueues.containsKey(ownerId)) {
            Queue<String> queue = waitingQueues.get(ownerId);
            if (!queue.isEmpty()) {
                String nextClient = queue.poll();
                System.out.println("ProfileManager: Notifying waiting client " + nextClient + " that profile " + ownerId + " is now available.");
                ClientHandler handler = ClientHandler.activeClients.get(nextClient);
                if (handler != null) {
                    try {
                        ObjectOutputStream output = handler.getOutputStream();
                        output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Profile " + ownerId + " is now available."));
                        output.flush();
                    } catch (IOException e) {
                        System.out.println("ProfileManager: Error notifying client " + nextClient);
                    }
                }
            }
        }
    }

    // Update the profile file for clientId by appending a new post.
    public synchronized void updateProfile(String clientId, String content) {
        String fileName = "Profile_" + clientId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(content + "\n");
            System.out.println("ProfileManager: Updated " + fileName + " with: " + content);
        } catch (IOException e) {
            System.out.println("ProfileManager: Error updating profile " + fileName);
            e.printStackTrace();
        }
    }

    // Simulate retrieval of profile content.
    public synchronized String getProfile(String clientId) {
        // In a full implementation, read file contents and merge with synchronized updates.
        String profileData = "Profile content for client " + clientId;
        System.out.println("ProfileManager: Returning profile for " + clientId);
        return profileData;
    }

    // Handle access_profile request.
    public static void handleAccessProfile(Message msg, String requesterId, ObjectOutputStream output) {
        String targetClientId = msg.getPayload(); // The requested profile.
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterId, targetClientId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetClientId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access granted. " + profileContent));
                System.out.println("ProfileManager: Access granted to client " + requesterId + " for profile " + targetClientId);
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access denied to profile " + targetClientId));
                System.out.println("ProfileManager: Access denied for client " + requesterId + " to profile " + targetClientId);
            }
            output.flush();
        } catch (IOException e) {
            System.out.println("ProfileManager: Error handling access_profile.");
            e.printStackTrace();
        }
    }

    // New method to handle reposting a publication to an "Others" page.
    public static void handleRepost(Message msg, String requesterId, ObjectOutputStream output) {
        // Payload is assumed to be the post content from a followed client.
        String postContent = msg.getPayload();
        // In a full implementation, we would update a file named Others_<clientId>.txt
        String othersFileName = Constants.OTHERS_PREFIX + "Profile_" + requesterId + ".txt";
        try (FileWriter fw = new FileWriter(new File(othersFileName), true)) {
            fw.write("Repost from follower: " + postContent + "\n");
            System.out.println("ProfileManager: Client " + requesterId + " reposted: " + postContent + " to " + othersFileName);
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Repost successful to " + othersFileName));
            output.flush();
        } catch (IOException e) {
            System.out.println("ProfileManager: Error in reposting for client " + requesterId);
            e.printStackTrace();
        }
    }
}
