// File: client/FileTransferHandler.java
package client;

import common.Message;
import common.Message.MessageType;
import common.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

public class FileTransferHandler {

    private static boolean chunk3Delayed = false;
    private static boolean chunk6Delayed = false;
    private static StringBuilder downloadBuffer = new StringBuilder();
    private static String currentDownloadFile = null;
    private static ServerConnection currentConnection = null;

    // Called when a DOWNLOAD command is initiated.
    // Now accepts ownerName:filename or just filename
    public static void downloadFile(String payload, ServerConnection connection) {
        System.out.println("Download initiated for payload: " + payload);
        downloadBuffer.setLength(0);
        chunk3Delayed = false;
        chunk6Delayed = false;
        currentConnection = connection;

        // Extract just the filename
        if (payload.contains(":")) {
            currentDownloadFile = payload.split(":", 2)[1];
        } else {
            currentDownloadFile = payload;
        }
    }

    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE:
                    connection.sendMessage(new Message(
                            MessageType.ACK,
                            connection.getClientId(),
                            "handshake ACK"));
                    break;

                case FILE_CHUNK:
                    String[] parts = msg.getPayload().split(":", 2);
                    if (parts.length < 2) break;
                    String chunkLabel   = parts[0].trim();
                    String chunkContent = parts[1].trim();

                    int chunkNum = -1;
                    try {
                        chunkNum = Integer.parseInt(chunkLabel.split(" ")[1]);
                    } catch (Exception ignored) {}

                    if (chunkNum == 3 && !chunk3Delayed) {
                        Thread.sleep(3500);
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel));
                        chunk3Delayed = true;
                    }
                    else if (chunkNum == 6 && !chunk6Delayed) {
                        Thread.sleep(3500);
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel));
                        chunk6Delayed = true;
                    }
                    else {
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel));
                    }

                    downloadBuffer.append(chunkContent);
                    break;

                case FILE_END:
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

    private static void saveDownloadedFile() {
        if (currentDownloadFile == null || downloadBuffer.length() == 0) {
            System.out.println("FileTransferHandler: No file data to save.");
            return;
        }
        try {
            String base64Data = downloadBuffer.toString().replaceAll("\\s+", "");
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            String clientId = currentConnection.getClientId();
            File dir = new File("ClientFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) dir.mkdirs();

            File outputFile = new File(dir, currentDownloadFile);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileBytes);
            }
            System.out.println("FileTransferHandler: File '" + currentDownloadFile
                    + "' saved successfully to " + dir.getPath());
        } catch (IOException e) {
            System.out.println("FileTransferHandler: Error saving downloaded file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("FileTransferHandler: Failed to decode Base64 data: "
                    + e.getMessage());
        }
    }
}
