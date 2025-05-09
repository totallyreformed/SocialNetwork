package server;

import common.Util;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Centralized manager for queuing, retrieving, and removing notifications
 * for clients, ensuring thread-safe operations and timestamped messages.
 */
public class NotificationManager {
    /** Singleton instance of NotificationManager. */
    private static NotificationManager instance = null;

    /**
     * Mapping from recipient client ID to a list of timestamped notifications.
     */
    private ConcurrentHashMap<String, List<String>> notifications;

    /**
     * Private constructor to initialize the notifications map.
     */
    private NotificationManager() {
        notifications = new ConcurrentHashMap<>();
    }

    /**
     * Returns the singleton instance of NotificationManager, creating it if necessary.
     *
     * @return the NotificationManager instance
     */
    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /**
     * Queues a notification message for a recipient to be delivered upon their next login.
     * Each message is prefixed with a timestamp.
     *
     * @param recipientId the numeric client ID of the notification recipient
     * @param message     the notification text to queue
     */
    public void addNotification(String recipientId, String message) {
        notifications.putIfAbsent(recipientId, new ArrayList<>());
        List<String> list = notifications.get(recipientId);
        synchronized (list) {
            list.add("[" + Util.getTimestamp() + "] " + message);
        }
    }

    /**
     * Retrieves and clears all queued notifications for a given recipient.
     *
     * @param recipientId the numeric client ID whose notifications to retrieve
     * @return a list of timestamped notification messages; empty if none
     */
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
     * Removes a specific queued notification for a recipient so it will not be
     * re-sent on subsequent logins. Matches entries that end with the given text.
     *
     * @param recipientId the numeric client ID whose notification to remove
     * @param message     the exact message text to remove (without timestamp)
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
