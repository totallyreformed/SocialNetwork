package server;

import common.Util;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

public class NotificationManager {
    private static NotificationManager instance = null;

    // Mapping from recipient (numeric ID) to a list of notifications.
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

    // Add a notification for a recipient.
    public void addNotification(String recipientId, String message) {
        notifications.putIfAbsent(recipientId, new ArrayList<>());
        synchronized (notifications.get(recipientId)) {
            notifications.get(recipientId).add("[" + Util.getTimestamp() + "] " + message);
        }
    }

    // Retrieve and clear notifications for a recipient.
    public List<String> getNotifications(String recipientId) {
        List<String> msgs = notifications.get(recipientId);
        if (msgs == null) {
            return new ArrayList<>();
        }
        synchronized(msgs) {
            List<String> copy = new ArrayList<>(msgs);
            msgs.clear();
            return copy;
        }
    }
}
