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
    // Map from clientId (of the profile owner) to a flag indicating if the profile is locked.
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    // Waiting queues for each profile keyed by the owner clientId.
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

    // Lock the profile for editing.
    public synchronized boolean lockProfile(String ownerId, String requesterId) {
        if (locks.containsKey(ownerId)) {
            // Add requester to waiting queue.
            waitingQueues.putIfAbsent(ownerId, new LinkedList<>());
            waitingQueues.get(ownerId).offer(requesterId);
            System.out.println("Client " + requesterId + " is waiting to access profile " + ownerId);
            return false;
        } else {
            locks.put(ownerId, true);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Lock timeout for profile " + ownerId + ". Releasing lock.");
                    unlockProfile(ownerId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(ownerId, timer);
            return true;
        }
    }

    public synchronized void unlockProfile(String ownerId) {
        locks.remove(ownerId);
        Timer timer = timers.remove(ownerId);
        if (timer != null) {
            timer.cancel();
        }
        // Notify the next client waiting for this profile, if any.
        if (waitingQueues.containsKey(ownerId)) {
            Queue<String> queue = waitingQueues.get(ownerId);
            if (!queue.isEmpty()) {
                String nextClient = queue.poll();
                System.out.println("Notifying client " + nextClient + " that profile " + ownerId + " is now available.");
                // Look up the active connection for the waiting client.
                ClientHandler handler = ClientHandler.activeClients.get(nextClient);
                if (handler != null) {
                    try {
                        ObjectOutputStream output = handler.getOutputStream();
                        output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Profile " + ownerId + " is now available."));
                        output.flush();
                    } catch (IOException e) {
                        System.out.println("Error notifying waiting client " + nextClient);
                    }
                }
            }
        }
    }

    public synchronized void updateProfile(String clientId, String content) {
        // Append the new post to the profile file (named as Profile_<clientId>.txt).
        String fileName = "Profile_" + clientId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(content + "\n");
            System.out.println("Updated profile " + fileName + " with: " + content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // For now, simply return a simulated profile string.
    public synchronized String getProfile(String clientId) {
        return "Profile content for client " + clientId;
    }

    // Handles access requests for profiles.
    public static void handleAccessProfile(common.Message msg, String requesterId, ObjectOutputStream output) {
        String targetClientId = msg.getPayload();  // Payload holds target clientId.
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterId, targetClientId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetClientId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", profileContent));
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access denied to profile " + targetClientId));
            }
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
