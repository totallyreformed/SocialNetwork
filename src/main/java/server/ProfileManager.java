package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.util.Queue;
import common.Constants;

public class ProfileManager {
    // Map from clientId to lock object.
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    // Waiting queue for clients trying to access a locked profile.
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    // Timers for file locks.
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private static ProfileManager instance = null;

    private ProfileManager() {}

    public static ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    public synchronized boolean lockProfile(String clientId, String requesterId) {
        if (locks.containsKey(clientId)) {
            // Add to waiting queue.
            waitingQueues.putIfAbsent(clientId, new LinkedList<>());
            waitingQueues.get(clientId).offer(requesterId);
            System.out.println("Client " + requesterId + " is waiting to access profile " + clientId);
            return false;
        } else {
            locks.put(clientId, true);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Lock timeout for profile " + clientId + ". Releasing lock.");
                    unlockProfile(clientId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(clientId, timer);
            return true;
        }
    }

    public synchronized void unlockProfile(String clientId) {
        locks.remove(clientId);
        Timer timer = timers.remove(clientId);
        if (timer != null) {
            timer.cancel();
        }
        // Notify next waiting client if any.
        if (waitingQueues.containsKey(clientId)) {
            Queue<String> queue = waitingQueues.get(clientId);
            if (!queue.isEmpty()) {
                String nextClient = queue.poll();
                System.out.println("Notifying client " + nextClient + " to access profile " + clientId);
                // In a complete implementation we would send a message to nextClient.
            }
        }
    }

    public synchronized void updateProfile(String clientId, String content) {
        // Append the new post to the profile file.
        String fileName = "Profile_" + clientId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(content + "\n");
            System.out.println("Updated profile " + fileName + " with: " + content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getProfile(String clientId) {
        // For brevity, return a simulated string.
        return "Profile content for client " + clientId;
    }

    public static void handleAccessProfile(common.Message msg, String requesterId, ObjectOutputStream output) {
        String targetClientId = msg.getPayload();  // Payload holds target clientId.
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterId, targetClientId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetClientId);
                output.writeObject(new common.Message(common.Message.MessageType.DIAGNOSTIC, "Server", profileContent));
            } else {
                output.writeObject(new common.Message(common.Message.MessageType.DIAGNOSTIC, "Server", "Access denied to profile " + targetClientId));
            }
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
