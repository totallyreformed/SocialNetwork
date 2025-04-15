package server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Helper class to hold user data.
class ClientRecord {
    String username;
    String password;
    String numericId; // Auto-generated numeric id as a string.

    public ClientRecord(String username, String password, String numericId) {
        this.username = username;
        this.password = password;
        this.numericId = numericId;
    }
}

public class AuthenticationManager {
    // Map from username to ClientRecord.
    private static ConcurrentHashMap<String, ClientRecord> userRecords = new ConcurrentHashMap<>();
    private static AtomicInteger clientIdCounter = new AtomicInteger(1);

    /**
     * Registers a new user with the provided username and password.
     * Auto-generates a numeric client id.
     * @return the generated numeric client id (as a String) if successful; null otherwise.
     */
    public static String signup(String username, String password) {
        if (userRecords.containsKey(username)) {
            System.out.println("AuthenticationManager: Signup failed – username '" + username + "' already exists.");
            return null;
        }
        String newClientId = Integer.toString(clientIdCounter.getAndIncrement());
        ClientRecord record = new ClientRecord(username, password, newClientId);
        userRecords.put(username, record);
        System.out.println("AuthenticationManager: User '" + username + "' signed up successfully with client id " + newClientId);
        return newClientId;
    }

    /**
     * Logs in an existing user.
     * @param username the username provided.
     * @param password the password provided.
     * @return the numeric client id if login succeeds; null otherwise.
     */
    public static String login(String username, String password) {
        if (userRecords.containsKey(username)) {
            ClientRecord record = userRecords.get(username);
            if (record.password.equals(password)) {
                System.out.println("AuthenticationManager: User '" + username + "' logged in successfully with client id " + record.numericId);
                return record.numericId;
            }
        }
        System.out.println("AuthenticationManager: Login failed for username '" + username + "'.");
        return null;
    }

    public static Iterable<ClientRecord> getAllClientRecords() {
        return userRecords.values();
    }

    /**
     * Retrieves the numeric client id for the given username.
     * @param username the username to look up.
     * @return the numeric client id as a String, or null if not found.
     */
    public static String getClientIdByUsername(String username) {
        ClientRecord record = userRecords.get(username);
        return record == null ? null : record.numericId;
    }

    /**
     * Retrieves the username for the given numeric client id.
     * @param numericId the numeric client id to look up.
     * @return the username, or null if not found.
     */
    public static String getUsernameByNumericId(String numericId) {
        for (ClientRecord record : userRecords.values()) {
            if (record.numericId.equals(numericId)) {
                return record.username;
            }
        }
        return null;
    }

}
