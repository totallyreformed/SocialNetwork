// File: client/ServerListener.java
package client;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;

import common.Message;
import common.Message.MessageType;
import common.Constants;

public class ServerListener implements Runnable {
    private ObjectInputStream input;
    private ServerConnection connection;
    private ProfileClientManager profileClientManager;
    private String lastDownloadFileName = null;

    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input = input;
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

                if (msg.getType() == MessageType.DIAGNOSTIC) {
                    String p = msg.getPayload();

                    if (p.startsWith("Search: found photo ")) {
                        System.out.println(p);
                        String[] parts = p.split(" at: ");
                        if (parts.length == 2) {
                            String file = parts[0].substring("Search: found photo ".length());
                            String[] owners = parts[1].split(",");
                            int ri = new java.util.Random().nextInt(owners.length);
                            String chosenToken = owners[ri];        // "3(alice)"
                            // extract ownerName inside parentheses
                            String ownerName = chosenToken
                                    .substring(chosenToken.indexOf('(')+1, chosenToken.indexOf(')'));
                            System.out.println("Initiating download of " + file
                                    + " from user " + ownerName);
                            lastDownloadFileName = file;
                            String downloadPayload = ownerName + ":" + file;
                            connection.sendMessage(new Message(
                                    MessageType.DOWNLOAD,
                                    connection.getClientId(),
                                    downloadPayload));
                            FileTransferHandler.downloadFile(downloadPayload, connection);
                        }
                        continue;
                    }

                    if (p.startsWith("Search: no followees")) {
                        System.out.println(p);
                        continue;
                    }

                    if (p.startsWith("Caption: ")) {
                        String captionText = p.substring("Caption: ".length());
                        saveCaptionFile(captionText);
                        continue;
                    }
                    if (p.equals("No caption available")) {
                        saveCaptionFile("");
                        continue;
                    }

                    if (p.startsWith("SYNC_REPOST:")) {
                        String entry = p.substring("SYNC_REPOST:".length());
                        profileClientManager.appendRepost(entry);
                        System.out.println("Local Others file updated with repost: " + entry);
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
                    System.out.println("Follow request received: " + msg.getPayload());
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

            File captionFile = new File(dir, base + ".txt");
            try (FileWriter fw = new FileWriter(captionFile)) {
                fw.write(captionText);
            }
            System.out.println("FileTransferHandler: Caption saved to " + captionFile.getPath());
        } catch (IOException e) {
            System.out.println("Error saving caption file: " + e.getMessage());
        }
    }
}
