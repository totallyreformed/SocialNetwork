// File: client/FileTransferHandler.java
package client;

import common.Message;
import common.Message.MessageType;
import common.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class FileTransferHandler {

    private static boolean chunk3Delayed = false;
    private static boolean chunk6Delayed = false;
    private static StringBuilder downloadBuffer = new StringBuilder();
    private static String currentDownloadFile = null;
    private static ServerConnection currentConnection = null;

    private static final Set<Integer> seenChunks = new HashSet<>();

    // Called when a DOWNLOAD command is initiated.
    public static void downloadFile(String payload, ServerConnection connection) {
        currentConnection = connection;
        downloadBuffer.setLength(0);
        seenChunks.clear();
        chunk3Delayed = false;
        chunk6Delayed = false;

        currentDownloadFile = payload.contains(":")
                ? payload.split(":",2)[1]
                : payload;
        System.out.println("Download initiated for payload: " + payload);
    }

    /** Process incoming file-transfer messages. */
    public static void handleIncomingMessage(Message msg, ServerConnection conn) {
        try {
            switch (msg.getType()) {

                case HANDSHAKE:
                    conn.sendMessage(new Message(MessageType.ACK,
                            conn.getClientId(),
                            "handshake ACK"));
                    break;

                case FILE_CHUNK:
                    String[] parts = msg.getPayload().split(":", 2);
                    if (parts.length < 2) break;
                    String chunkLabel   = parts[0].trim();   // "Chunk 3"
                    String chunkContent = parts[1].trim();

                    // Parse chunk number
                    int chunkNum = -1;
                    try { chunkNum = Integer.parseInt(chunkLabel.split(" ")[1]); }
                    catch (Exception ignored) {}

                    /* ======= deliberate ACK delays for chunk 3 & 6 ========= */
                    if (chunkNum == 3 && !chunk3Delayed) {
                        Thread.sleep(3500);
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk 3"));
                        chunk3Delayed = true;
                    }
                    else if (chunkNum == 6 && !chunk6Delayed) {
                        Thread.sleep(3500);
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk 6"));
                        chunk6Delayed = true;
                    }
                    else {
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk " + chunkNum));
                    }

                    /* ========= NEW: ignore duplicate chunks ========== */
                    if (!seenChunks.contains(chunkNum)) {
                        downloadBuffer.append(chunkContent);
                        seenChunks.add(chunkNum);
                    }
                    break;

                case FILE_END:
                    System.out.println(msg.getPayload());
                    System.out.println("Download complete. Saving file...");
                    saveDownloadedFile();
                    downloadBuffer.setLength(0);
                    currentDownloadFile = null;
                    currentConnection = null;
                    break;

                case NACK:
                    System.out.println("Download error: " + msg.getPayload());
                    break;

                default:
                    break;
            }
        } catch (InterruptedException e) {
            System.out.println("FileTransferHandler: Interrupted: " + e.getMessage());
        }
    }

    /** Decode Base64 and save into ClientFiles/<groupID>client<id>/ */
    private static void saveDownloadedFile() {
        if (currentDownloadFile == null || downloadBuffer.length() == 0) {
            System.out.println("FileTransferHandler: No file data to save.");
            return;
        }
        try {
            String base64Data = downloadBuffer.toString().replaceAll("\\s+", "");
            byte[] fileBytes  = Base64.getDecoder().decode(base64Data);

            String clientId = currentConnection.getClientId();
            File dir = new File("ClientFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) dir.mkdirs();

            File out = new File(dir, currentDownloadFile);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(fileBytes);
            }
            System.out.println("FileTransferHandler: File '" + currentDownloadFile
                    + "' saved successfully to " + dir.getPath());
        } catch (IOException e) {
            System.out.println("FileTransferHandler: Error saving downloaded file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("FileTransferHandler: Failed to decode Base64 data: " + e.getMessage());
        }
    }
}
