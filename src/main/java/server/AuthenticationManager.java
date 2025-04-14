package server;

import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationManager {
    private static ConcurrentHashMap<String, String> registeredClients = new ConcurrentHashMap<>();

    public static boolean signup(String clientId, String password) {
        if (registeredClients.containsKey(clientId)) {
            return false; // Already registered.
        }
        registeredClients.put(clientId, password);
        System.out.println("Client " + clientId + " signed up successfully.");
        return true;
    }

    public static boolean login(String clientId, String password) {
        if (registeredClients.containsKey(clientId) && registeredClients.get(clientId).equals(password)) {
            System.out.println("Client " + clientId + " logged in successfully.");
            return true;
        }
        return false;
    }
}
