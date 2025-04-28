package client;

import common.Message;
import common.Message.MessageType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class FileTransferHandler {

    // Static flags and buffer for simulating delay behavior.
    private static boolean chunk3Delayed = false;
    private static boolean chunk6Delayed = false;
    private static StringBuilder downloadBuffer = new StringBuilder();
    private static String currentDownloadFile = null;

    // Called when a DOWNLOAD command is initiated.
    public static void downloadFile(String photoName, ServerConnection connection) {
        System.out.println("Download initiated for photo: " + photoName);
        downloadBuffer.setLength(0);
        currentDownloadFile = photoName;
        chunk3Delayed = false;
        chunk6Delayed = false;
    }

    // Process incoming file-transfer messages.
    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE: {
                    String handshakePayload = msg.getPayload();
                    int idx = handshakePayload.indexOf("for ");
                    if (idx != -1) {
                        currentDownloadFile = handshakePayload.substring(idx + 4).trim();
                    }
                    connection.sendMessage(new Message(
                            MessageType.ACK,
                            connection.getClientId(),
                            "handshake ACK"
                    ));
                    break;
                }
                case FILE_CHUNK: {
                    String payload = msg.getPayload();
                    String[] parts = payload.split(":", 2);
                    if (parts.length < 2) break;
                    String chunkLabel = parts[0].trim();   // e.g., "Chunk 3"
                    String chunkContent = parts[1].trim(); // base64 content

                    int chunkNum = -1;
                    try {
                        String[] labelParts = chunkLabel.split(" ");
                        if (labelParts.length >= 2) {
                            chunkNum = Integer.parseInt(labelParts[1]);
                        }
                    } catch (NumberFormatException ignored) { }

                    if (chunkNum == 3 && !chunk3Delayed) {
                        System.out.println("FileTransferHandler: Delaying ACK for Chunk 3...");
                        Thread.sleep(3500);
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel
                        ));
                        chunk3Delayed = true;
                    }
                    else if (chunkNum == 6 && !chunk6Delayed) {
                        System.out.println("FileTransferHandler: Delaying ACK for Chunk 6 (first time)...");
                        Thread.sleep(3500);
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel
                        ));
                        chunk6Delayed = true;
                    }
                    else {
                        connection.sendMessage(new Message(
                                MessageType.ACK,
                                connection.getClientId(),
                                "ACK for " + chunkLabel
                        ));
                    }

                    downloadBuffer.append(chunkContent);
                    break;
                }
                case FILE_END: {
                    System.out.println("Download complete. Saving file...");
                    saveDownloadedFile();

                    // 2️⃣ Print the server’s “transmission completed” message:
                    System.out.println(msg.getPayload());

                    // 1️⃣ Auto–resync the just‐downloaded file back to the server:
                    try {
                        byte[] fileBytes = Files.readAllBytes(
                                Paths.get("ClientFiles", currentDownloadFile)
                        );
                        String b64 = Base64.getEncoder().encodeToString(fileBytes);

                        String title = currentDownloadFile;
                        int dot = title.lastIndexOf('.');
                        if (dot != -1) title = title.substring(0, dot);

                        String uploadPayload =
                                "photoTitle:" + title +
                                        "|fileName:"  + currentDownloadFile +
                                        "|caption:"   + "" +
                                        "|data:"      + b64;

                        connection.sendMessage(new Message(
                                MessageType.UPLOAD,
                                connection.getClientId(),
                                uploadPayload
                        ));
                        System.out.println("FileTransferHandler: auto-resynced " + currentDownloadFile + " to server.");
                    } catch (IOException e) {
                        System.out.println("FileTransferHandler: failed to resync "
                                + currentDownloadFile + ": " + e.getMessage());
                    }

                    // Reset state
                    downloadBuffer.setLength(0);
                    currentDownloadFile = null;
                    break;
                }
                case NACK:
                    System.out.println("Download error: " + msg.getPayload());
                    break;
                default:
                    // ignore other types
                    break;
            }
        } catch (InterruptedException e) {
            System.out.println("FileTransferHandler: Interrupted: " + e.getMessage());
        }
    }

    // Saves the downloaded file into ClientFiles, decoding Base64.
    private static void saveDownloadedFile() {
        if (currentDownloadFile == null || downloadBuffer.length() == 0) {
            System.out.println("FileTransferHandler: No file data to save.");
            return;
        }
        try {
            // Decode and write the bytes
            byte[] fileBytes = Base64.getDecoder().decode(
                    downloadBuffer.toString().replaceAll("\\s+", "")
            );

            File dir = new File("ClientFiles");
            if (!dir.exists()) dir.mkdirs();

            File outputFile = new File(dir, currentDownloadFile);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileBytes);
            }
            System.out.println("FileTransferHandler: File '" + currentDownloadFile + "' saved successfully.");
        } catch (IOException e) {
            System.out.println("FileTransferHandler: Error saving downloaded file: " + e.getMessage());
        }
    }
}
