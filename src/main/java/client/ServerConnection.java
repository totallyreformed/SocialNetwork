package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import common.Constants;

/**
 * Manages the persistent connection to the server and
 * tracks handshake state for downloads and comment approvals.
 */
public class ServerConnection {
    /** Socket for communicating with the server. */
    private Socket socket;

    /** Stream for sending Message objects to the server. */
    private ObjectOutputStream output;

    /** Stream for receiving Message objects from the server. */
    private ObjectInputStream input;

    /** The numeric client identifier assigned by the server. */
    private String clientId = "clientID_placeholder";

    /** The client’s preferred caption language ("en" or "gr"). */
    private String languagePref = "en";

    // ── Download handshake state ─────────────────────────

    /** Payload of an incoming download ASK request, if pending. */
    private String pendingAskPayload;

    /**
     * Queues an incoming download ASK payload for user response.
     *
     * @param p the raw ASK payload from the server
     */
    public synchronized void queuePendingAsk(String p) {
        pendingAskPayload = p;
    }

    /**
     * Retrieves and clears the pending download ASK payload.
     *
     * @return the pending ASK payload, or null if none
     */
    public synchronized String consumePendingAsk() {
        String t = pendingAskPayload;
        pendingAskPayload = null;
        return t;
    }

    /**
     * Checks if there is a pending download ASK request.
     *
     * @return true if an ASK is pending, false otherwise
     */
    public synchronized boolean hasPendingAsk() {
        return pendingAskPayload != null;
    }

    /**
     * States for retry-on-denial download flow.
     */
    enum RetryState {
        NONE,
        AWAIT_RETRY_CONFIRM,
        AWAIT_SAME_CONFIRM,
        AWAIT_SELECTION
    }

    /** Current retry state for a denied download. */
    private RetryState retryState = RetryState.NONE;

    /** Candidate owners for retry download attempts. */
    private List<String> retryOwners = new ArrayList<>();

    /** Language code for retry download ("en" or "gr"). */
    private String retryLang;

    /** Filename for retry download. */
    private String retryFile;

    /**
     * Initializes retry context after a denial.
     *
     * @param owners list of possible owners to retry against
     * @param lang   preferred language code
     * @param file   name of the file being downloaded
     */
    public synchronized void initRetry(List<String> owners, String lang, String file) {
        retryOwners.clear();
        retryOwners.addAll(owners);
        retryLang = lang;
        retryFile = file;
        retryState = RetryState.AWAIT_RETRY_CONFIRM;
    }

    /**
     * Retrieves the current retry state.
     *
     * @return the retry state
     */
    public synchronized RetryState getRetryState() {
        return retryState;
    }

    /**
     * Retrieves the list of retry candidate owners.
     *
     * @return list of owner usernames
     */
    public synchronized List<String> getRetryOwners() {
        return retryOwners;
    }

    /**
     * Retrieves the retry language preference.
     *
     * @return "en" or "gr"
     */
    public synchronized String getRetryLang() {
        return retryLang;
    }

    /**
     * Retrieves the filename for retry download.
     *
     * @return filename string
     */
    public synchronized String getRetryFile() {
        return retryFile;
    }

    /**
     * Updates the retry state.
     *
     * @param s the new retry state
     */
    public synchronized void setRetryState(RetryState s) {
        retryState = s;
    }

    /**
     * Clears any retry context.
     */
    public synchronized void clearRetry() {
        retryState = RetryState.NONE;
        retryOwners.clear();
        retryLang = null;
        retryFile = null;
    }

    // ── Comment‐approval handshake state ─────────────────

    /** Payload of an incoming comment approval ASK, if pending. */
    private String pendingCommentAskPayload;

    /**
     * Queues an incoming comment approval ASK payload for user response.
     *
     * @param p the raw ASK_COMMENT payload from the server
     */
    public synchronized void queuePendingCommentAsk(String p) {
        pendingCommentAskPayload = p;
    }

    /**
     * Retrieves and clears the pending comment ASK payload.
     *
     * @return the pending ASK_COMMENT payload, or null if none
     */
    public synchronized String consumePendingCommentAsk() {
        String t = pendingCommentAskPayload;
        pendingCommentAskPayload = null;
        return t;
    }

    /**
     * Checks if there is a pending comment approval ASK request.
     *
     * @return true if an ASK_COMMENT is pending, false otherwise
     */
    public synchronized boolean hasPendingCommentAsk() {
        return pendingCommentAskPayload != null;
    }

    /**
     * Establishes a connection to the server and starts listening.
     *
     * @return true if connection succeeds, false on error
     */
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

    /**
     * Sends a Message object to the server.
     *
     * @param msg the Message to send
     */
    public void sendMessage(common.Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
        } catch (IOException e) {
            System.out.println("Error sending: " + e.getMessage());
        }
    }

    /**
     * Retrieves the current client identifier.
     *
     * @return numeric clientId or placeholder
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Updates the client identifier after authentication.
     *
     * @param id the new clientId assigned by the server
     */
    public void setClientId(String id) {
        clientId = id;
    }

    /**
     * Retrieves the client's caption language preference.
     *
     * @return "en" or "gr"
     */
    public String getLanguagePref() {
        return languagePref;
    }

    /**
     * Updates the client's caption language preference.
     *
     * @param lang the new language code ("en" or "gr")
     */
    public void setLanguagePref(String lang) {
        languagePref = lang;
    }
}
