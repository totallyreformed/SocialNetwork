package client;

import java.io.ObjectInputStream;
import common.Message;
import java.io.IOException;

public class ServerListener implements Runnable {
    private ObjectInputStream input;

    public ServerListener(ObjectInputStream input) {
        this.input = input;
    }

    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {
                System.out.println("Server: " + msg.getPayload());
                // Additional processing based on message type can be added here.
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        }
    }
}
