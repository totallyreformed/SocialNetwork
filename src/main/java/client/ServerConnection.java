// client/ServerConnection.java
package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import common.Constants;

/**
 * Manages the persistent connection between the client and server,
 * handling socket creation, message sending, and client identification.
 *
 * Also tracks any pending ASK request for explicit owner approval
 * and drives retry logic when downloads are denied.
 */
public class ServerConnection {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private String clientId = "clientID_placeholder";
    private String languagePref = "en";

    // awaiting owner PERMIT/DENY
    private String pendingAskPayload;

    // retry state for download-on-deny
    enum RetryState { NONE, AWAIT_RETRY_CONFIRM, AWAIT_SAME_CONFIRM, AWAIT_SELECTION }
    private RetryState retryState = RetryState.NONE;
    private List<String> retryOwners = new ArrayList<>();
    private String retryLang, retryFile;

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

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getLanguagePref() { return languagePref; }
    public void setLanguagePref(String languagePref) { this.languagePref = languagePref; }

    // ─── ASK state ───────────────────────────────────────
    public synchronized void queuePendingAsk(String askPayload) {
        this.pendingAskPayload = askPayload;
    }
    public synchronized String consumePendingAsk() {
        String tmp = pendingAskPayload;
        pendingAskPayload = null;
        return tmp;
    }
    public synchronized boolean hasPendingAsk() {
        return pendingAskPayload != null;
    }

    // ─── Retry-on-deny state ─────────────────────────────
    public synchronized void initRetry(List<String> owners, String lang, String file) {
        this.retryOwners.clear();
        this.retryOwners.addAll(owners);
        this.retryLang = lang;
        this.retryFile = file;
        this.retryState = RetryState.AWAIT_RETRY_CONFIRM;
    }
    public synchronized RetryState getRetryState() { return retryState; }
    public synchronized List<String> getRetryOwners() { return retryOwners; }
    public synchronized String getRetryLang() { return retryLang; }
    public synchronized String getRetryFile() { return retryFile; }
    public synchronized void setRetryState(RetryState s) { this.retryState = s; }
    public synchronized void clearRetry() {
        this.retryState = RetryState.NONE;
        this.retryOwners.clear();
        this.retryLang = this.retryFile = null;
    }
}
