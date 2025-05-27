package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.io.IOException;

import common.Constants;

/**
 * Manages the persistent connection between the client and server,
 * handling socket creation, message sending, and client identification.
 *
 * Also tracks any pending ASK request for explicit owner approval.
 */
public class ServerConnection {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    // Use a default placeholder; this will be updated silently when auth succeeds.
    private String clientId = "clientID_placeholder";

    // NEW: store the user's language preference (default to English)
    private String languagePref = "en";

    // NEW: pending ASK payload awaiting owner decision
    private String pendingAskPayload = null;

    /**
     * Establishes a socket connection to the server and starts
     * a listener thread for incoming messages.
     *
     * @return true if connection succeeds; false on I/O error
     */
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

    /**
     * Sends a Message object to the server over the established connection.
     * Suppresses detailed client-side debug logging.
     *
     * @param msg the Message to send
     */
    public void sendMessage(common.Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
            // Debug log suppressed from client-side output.
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }

    /**
     * Retrieves the current client identifier.
     *
     * @return the client ID string, or placeholder if not set
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Updates the client identifier used for subsequent communications.
     * Internal session details are not logged to the console.
     *
     * @param clientId the new client ID to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
        // Suppressed: Do not print internal session update details.
    }

    /**
     * Retrieves the client’s preferred language for captions ("en" or "gr").
     *
     * @return the current language preference
     */
    public String getLanguagePref() {
        return languagePref;
    }

    /**
     * Updates the client’s language preference.
     *
     * @param languagePref must be "en" or "gr"
     */
    public void setLanguagePref(String languagePref) {
        this.languagePref = languagePref;
    }

    // ────────────────────────── Pending ASK logic ──────────────────────────

    /**
     * Queues an ASK payload for explicit owner approval.
     * The next console input will be interpreted as the decision.
     *
     * @param askPayload the raw payload string of the ASK message
     */
    public synchronized void queuePendingAsk(String askPayload) {
        this.pendingAskPayload = askPayload;
    }

    /**
     * Returns and clears the pending ASK payload.
     *
     * @return the queued ASK payload, or null if none was pending
     */
    public synchronized String consumePendingAsk() {
        String tmp = pendingAskPayload;
        pendingAskPayload = null;
        return tmp;
    }

    /**
     * Indicates whether there is an ASK awaiting the owner's decision.
     *
     * @return true if an ASK is pending, false otherwise
     */
    public synchronized boolean hasPendingAsk() {
        return pendingAskPayload != null;
    }
}
