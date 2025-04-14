package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import common.Constants;
import java.io.IOException;

public class ServerConnection {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    // Use a default placeholder; this will be updated silently when auth succeeds.
    private String clientId = "clientID_placeholder";

    public boolean connect() {
        try {
            socket = new Socket("localhost", Constants.SERVER_PORT);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
            // Start the listener thread.
            new Thread(new ServerListener(input, this)).start();
            return true;
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    // Updated: This method no longer prints the detailed debug information.
    public void sendMessage(common.Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
            // Debug log suppressed from client-side output.
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }

    public ObjectOutputStream getOutputStream() {
        return output;
    }

    public ObjectInputStream getInputStream() {
        return input;
    }

    // Updated: Set client ID without printing verbose logs.
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
        // Suppressed: Do not print internal session update details.
    }
}
