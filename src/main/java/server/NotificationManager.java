// File: server/NotificationManager.java
package server;

import common.Util;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Centralized manager for queuing and delivering notifications.
 */
public class NotificationManager {
    private static NotificationManager instance = null;

    // Mapping from recipient (numeric ID) to a list of timestamped notifications.
    private ConcurrentHashMap<String, List<String>> notifications;

    private NotificationManager() {
        notifications = new ConcurrentHashMap<>();
    }

    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /** Queue a notification for delivery on next login. */
    public void addNotification(String recipientId, String message) {
        notifications.putIfAbsent(recipientId, new ArrayList<>());
        List<String> list = notifications.get(recipientId);
        synchronized (list) {
            list.add("[" + Util.getTimestamp() + "] " + message);
        }
    }

    /** Retrieve and clear all queued notifications for a user. */
    public List<String> getNotifications(String recipientId) {
        List<String> msgs = notifications.get(recipientId);
        if (msgs == null) {
            return new ArrayList<>();
        }
        synchronized (msgs) {
            List<String> copy = new ArrayList<>(msgs);
            msgs.clear();
            return copy;
        }
    }

    /**
     * Remove a specific queued notification so it won't be re‑sent at login.
     * Matches any entry that ends with the given message text.
     */
    public void removeNotification(String recipientId, String message) {
        List<String> msgs = notifications.get(recipientId);
        if (msgs == null) return;
        synchronized (msgs) {
            msgs.removeIf(m -> m.endsWith(message));
            if (msgs.isEmpty()) {
                notifications.remove(recipientId);
            }
        }
    }
}
