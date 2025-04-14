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
    private String clientId = "clientID_placeholder"; // Updated after successful auth.

    public boolean connect() {
        try {
            socket = new Socket("localhost", Constants.SERVER_PORT);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
            new Thread(new ServerListener(input, this)).start();
            return true;
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    public void sendMessage(common.Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
        System.out.println("Session updated: clientId is now " + clientId);
    }
}
