package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import common.Constants;

public class ProfileManager {
    // Map to maintain locked profiles (clientID -> lock object)
    private ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    // Map to associate timers with locked profiles
    private ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private static ProfileManager instance = null;

    private ProfileManager() {}

    public static ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    public synchronized boolean lockProfile(String clientId) {
        if (locks.containsKey(clientId)) {
            return false; // The profile file is already locked.
        } else {
            locks.put(clientId, new Object());
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Lock timeout for client " + clientId + ". Releasing lock.");
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
    }

    public synchronized void updateProfile(String clientId, String content) {
        // Append the new post to the profile file
        String fileName = "Profile_" + clientId + ".txt";
        try (FileWriter fw = new FileWriter(new File(fileName), true)) {
            fw.write(content + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getProfile(String clientId) {
        // Here you would read and return the contents of the profile file.
        return "Profile content for client " + clientId;
    }

    // Handle profile access request: verifies if the requester follows the target.
    public static void handleAccessProfile(common.Message msg, String requesterId, java.io.ObjectOutputStream output) {
        String targetClientId = msg.getPayload();  // Assume payload holds the target client ID.
        boolean allowed = SocialGraphManager.getInstance().isFollowing(requesterId, targetClientId);
        try {
            if (allowed) {
                String profileContent = getInstance().getProfile(targetClientId);
                output.writeObject(new common.Message(common.Message.MessageType.DIAGNOSTIC, "Server", profileContent));
            } else {
                output.writeObject(new common.Message(common.Message.MessageType.DIAGNOSTIC, "Server", "Access denied for profile " + targetClientId));
            }
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
