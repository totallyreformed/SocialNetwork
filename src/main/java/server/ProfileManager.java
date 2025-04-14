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
    // Map for profile locks (key: numeric client id, value: lock flag).
    private ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();
    // Waiting queues for profiles (key: numeric client id, value: queue of requester numeric ids).
    private ConcurrentHashMap<String, Queue<String>> waitingQueues = new ConcurrentHashMap<>();
    // Timers for locks.
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    private static ProfileManager instance = null;

    private ProfileManager() { }

    public static ProfileManager getInstance() {
        if(instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    // Attempt to lock a profile for editing.
    public synchronized boolean lockProfile(String clientId, String requesterId) {
        if(locks.containsKey(clientId)) {
            waitingQueues.putIfAbsent(clientId, new LinkedList<>());
            waitingQueues.get(clientId).offer(requesterId);
            System.out.println("ProfileManager: Client " + requesterId + " queued for profile " + clientId);
            return false;
        } else {
            locks.put(clientId, true);
            Timer timer = new Timer();
            timer.schedule(new TimerTask(){
                @Override
                public void run() {
                    System.out.println("ProfileManager: Lock timeout for profile " + clientId + " reached. Releasing lock.");
                    unlockProfile(clientId);
                }
            }, Constants.TIMEOUT_MILLISECONDS);
            timers.put(clientId, timer);
            System.out.println("ProfileManager: Profile " + clientId + " locked.");
            return true;
        }
    }

    public synchronized void unlockProfile(String clientId) {
        locks.remove(clientId);
        Timer timer = timers.remove(clientId);
        if(timer != null) timer.cancel();
        System.out.println("ProfileManager: Profile " + clientId + " unlocked.");
        if(waitingQueues.containsKey(clientId)) {
            Queue<String> queue = waitingQueues.get(clientId);
            if(!queue.isEmpty()) {
                String nextClient = queue.poll();
                ClientHandler handler = ClientHandler.activeClients.get(nextClient);
                if(handler != null) {
                    try {
                        handler.getOutputStream().writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Profile " + clientId + " is now available."));
                        handler.getOutputStream().flush();
                    } catch(IOException e) {
                        System.out.println("ProfileManager: Error notifying client " + nextClient);
                    }
                }
            }
        }
    }

    // Update the profile by appending a new post.
    public synchronized void updateProfile(String clientId, String content) {
        String fileName = "Profile_" + clientId + ".txt";
        try(FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(content + "\n");
            System.out.println("ProfileManager: Updated " + fileName + " with: " + content);
        } catch(IOException e) {
            System.out.println("ProfileManager: Error updating profile " + fileName);
            e.printStackTrace();
        }
    }

    // Retrieve profile content (for simulation, return a simple message).
    public synchronized String getProfile(String clientId) {
        String fileName = "Profile_" + clientId + ".txt";
        System.out.println("ProfileManager: Retrieving profile for client " + clientId);
        return "Profile content for client " + clientId;
    }

    // Handle access_profile requests.
    public static void handleAccessProfile(Message msg, String requesterNumericId, ObjectOutputStream output) {
        String targetUsername = msg.getPayload();
        String targetNumericId = AuthenticationManager.getClientIdByUsername(targetUsername);
        if(targetNumericId == null) {
            try {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access_profile failed: User '" + targetUsername + "' not found."));
                output.flush();
            } catch(IOException e) {
                e.printStackTrace();
            }
            return;
        }
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterNumericId, targetNumericId);
        try {
            if(allowed) {
                String profileContent = getInstance().getProfile(targetNumericId);
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access granted. " + profileContent));
                System.out.println("ProfileManager: Access granted for requester " + requesterNumericId + " to profile " + targetNumericId);
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Access denied: You do not follow user '" + targetUsername + "'."));
                System.out.println("ProfileManager: Access denied for requester " + requesterNumericId + " to profile " + targetNumericId);
            }
            output.flush();
        } catch(IOException e) {
            System.out.println("ProfileManager: Error handling access_profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Handle repost requests.
    public static void handleRepost(Message msg, String requesterNumericId, ObjectOutputStream output) {
        String postContent = msg.getPayload();
        String othersFileName = "Others_Profile_" + requesterNumericId + ".txt";
        try (FileWriter fw = new FileWriter(new File(othersFileName), true)) {
            fw.write("Repost: " + postContent + "\n");
            System.out.println("ProfileManager: Client " + requesterNumericId + " reposted: " + postContent + " to " + othersFileName);
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Repost successful to " + othersFileName));
            output.flush();
        } catch(IOException e) {
            System.out.println("ProfileManager: Error in repost for client " + requesterNumericId);
            e.printStackTrace();
        }
    }
}
