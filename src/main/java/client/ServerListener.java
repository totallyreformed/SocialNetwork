package client;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import common.Message;
import common.Message.MessageType;
import common.Constants;
import common.Util;

/**
 * Listens for incoming messages from the server and dispatches
 * actions such as authentication handling, follow requests,
 * search responses, and file transfer events.
 *
 * Now queues ASK requests in ServerConnection instead of blocking
 * on console input.
 */
public class ServerListener implements Runnable {

    private final ObjectInputStream input;
    private final ServerConnection connection;
    private ProfileClientManager profileClientManager;
    private String lastDownloadFileName = null;
    private List<String> lastOwners = new ArrayList<>();
    private String lastLang;

    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input      = input;
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {

                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    connection.setClientId(msg.getSenderId());
                    profileClientManager = new ProfileClientManager(msg.getSenderId());
                    System.out.println(msg.getPayload());
                    continue;
                }
                if (msg.getType() == MessageType.AUTH_FAILURE) {
                    System.out.println(msg.getPayload());
                    continue;
                }
                if (msg.getType() == MessageType.LIST_FOLLOWERS_RESPONSE) {
                    String p = msg.getPayload();
                    System.out.println(p.isEmpty() ? "You have no followers." : "Followers: " + p);
                    continue;
                }
                if (msg.getType() == MessageType.LIST_FOLLOWING_RESPONSE) {
                    String p = msg.getPayload();
                    System.out.println(p.isEmpty() ? "You are not following anyone." : "Following: " + p);
                    continue;
                }

                /* ── Phase-B gated download ── */
                if (msg.getType() == MessageType.ASK) {
                    // Instead of prompting here, queue the payload for CommandHandler
                    connection.queuePendingAsk(msg.getPayload());
                    System.out.println("\n>>> You have a download request pending. " +
                            "Type 'yes' or 'no'.");
                    continue;
                }
                if (msg.getType() == MessageType.PERMIT) {
                    handlePermit(msg);
                    continue;
                }
                if (msg.getType() == MessageType.DENY) {
                    handleDeny(msg);
                    continue;
                }

                if (msg.getType() == MessageType.DIAGNOSTIC) {
                    String p = msg.getPayload();
                    if (p.startsWith("Search: found photo ")) {
                        System.out.println(p);
                        String[] parts = p.split(" at: ");
                        String raw = parts[0].substring("Search: found photo ".length());
                        int idx = raw.indexOf(" (");
                        String file = (idx == -1 ? raw : raw.substring(0, idx)).trim();

                        // record owners & context
                        lastOwners.clear();
                        for (String tok : parts[1].split(",")) {
                            String nm = tok.substring(tok.indexOf('(')+1, tok.indexOf(')'));
                            lastOwners.add(nm);
                        }
                        lastDownloadFileName = file;
                        lastLang = connection.getLanguagePref();

                        // send initial ASK
                        String owner = lastOwners.get((int)(Math.random()*lastOwners.size()));
                        System.out.println("Initiating ASK to " + owner);
                        sendAsk(owner, file, lastLang);
                        continue;
                    }
                    if (p.startsWith("Search: no followees")) {
                        System.out.println(p);
                        continue;
                    }
                    if (p.startsWith("Caption: ")) {
                        saveCaptionFile(p.substring("Caption: ".length()));
                        continue;
                    }
                    if (p.equals("No caption available")) {
                        saveCaptionFile("");
                        continue;
                    }
                    if (p.startsWith("SYNC_REPOST:")) {
                        profileClientManager.appendRepost(p.substring("SYNC_REPOST:".length()));
                        System.out.println("Local Others updated with repost: " + p);
                        continue;
                    }
                    if (p.startsWith("Notification:")) {
                        profileClientManager.appendPost(p);
                        System.out.println(p);
                        continue;
                    }
                    if (!p.contains("handshake") && !p.contains("Chunk")) {
                        System.out.println(p);
                    }
                }

                if (msg.getType() == MessageType.FOLLOW_REQUEST) {
                    String[] pr = msg.getPayload().split(":",2);
                    System.out.println("\n>>> User '" + pr[0] + "' wants to follow you.");
                    System.out.println("    Type: respondfollow " + pr[0] + ":<accept|reject|reciprocate>");
                    System.out.print("> ");
                    continue;
                }

                if (msg.getType() == MessageType.HANDSHAKE
                        || msg.getType() == MessageType.FILE_CHUNK
                        || msg.getType() == MessageType.FILE_END
                        || msg.getType() == MessageType.NACK) {

                    if (msg.getType() == MessageType.HANDSHAKE) {
                        String hs = msg.getPayload();
                        int idx = hs.indexOf("for ");
                        if (idx != -1) {
                            // ◀── Record filename again in case someone bypassed PERMIT path
                            lastDownloadFileName = hs.substring(idx + 4).trim();
                        }
                    }
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                }

            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private void sendAsk(String owner, String file, String lang) {
        String payload = "requesterId:"   + connection.getClientId()
                + "|ownerUsername:" + owner
                + "|file:"          + file
                + "|lang:"          + lang;
        connection.sendMessage(new Message(MessageType.ASK, connection.getClientId(), payload));
    }

    /**
     * Requester-side: on PERMIT, emit DOWNLOAD and invoke the
     * existing FileTransferHandler.  (Runs only on the requester client.)
     */
    private void handlePermit(Message permit) {
        Map<String,String> m = Util.parsePayload(permit.getPayload());
        String owner = m.get("ownerUsername");
        String file  = m.get("file");
        String lang  = m.get("lang");

        // ◀── Record filename here so caption saving knows where to write
        lastDownloadFileName = file;

        String dlPayload = "lang:" + lang
                + "|ownerFilename:" + owner + ":" + file;

        connection.sendMessage(new Message(
                MessageType.DOWNLOAD,
                connection.getClientId(),
                dlPayload));
        FileTransferHandler.downloadFile(dlPayload, connection);
    }


    private void handleDeny(Message deny) {
        System.out.println("Download denied – " + deny.getPayload());
        // No further action here—CommandHandler will now drive retries
    }

    private void saveCaptionFile(String captionText) {
        if (lastDownloadFileName == null) {
            System.out.println("Warning: no filename recorded for caption; skipping save.");
            return;
        }
        try {
            String base = lastDownloadFileName;
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);

            String clientId = connection.getClientId();
            File dir = new File("ClientFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) dir.mkdirs();

            File cf = new File(dir, base + ".txt");
            try (FileWriter fw = new FileWriter(cf)) {
                fw.write(captionText);
            }
            System.out.println("Caption saved to " + cf.getPath());
        } catch (IOException e) {
            System.out.println("Error saving caption: " + e.getMessage());
        }
    }
}