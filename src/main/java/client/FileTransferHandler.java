package client;

import common.Message;
import common.Message.MessageType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileTransferHandler {

    // Static flag and buffer for simulating delay behavior.
    private static boolean chunk3Delayed = false;
    private static boolean chunk6Delayed = false;
    private static StringBuilder downloadBuffer = new StringBuilder();
    private static String currentDownloadFile = null;

    // Called when a DOWNLOAD command is initiated.
    public static void downloadFile(String photoName, ServerConnection connection) {
        // The handshake message from the server should contain the photo name.
        // Here we simply log the initiation.
        System.out.println("Download initiated for photo: " + photoName);
        // Reset buffer and flags
        downloadBuffer.setLength(0);
        currentDownloadFile = photoName;
        chunk3Delayed = false;
        chunk6Delayed = false;
    }

    // Process incoming file-transfer messages.
    public static void handleIncomingMessage(Message msg, ServerConnection connection) {
        try {
            switch (msg.getType()) {
                case HANDSHAKE:
                    // The handshake message is assumed to be like "Initiate handshake for <photoName>"
                    // Extract photoName if possible.
                    String handshakePayload = msg.getPayload();
                    // We expect the payload contains "for " followed by the file name.
                    int idx = handshakePayload.indexOf("for ");
                    if (idx != -1) {
                        currentDownloadFile = handshakePayload.substring(idx + 4).trim();
                    }
                    // Immediately send ACK for handshake.
                    connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "handshake ACK"));
                    break;
                case FILE_CHUNK:
                    // Expected payload: "Chunk N: <chunk content>"
                    String payload = msg.getPayload();
                    // Extract chunk number and content.
                    String[] parts = payload.split(":", 2);
                    if (parts.length < 2) break;
                    String chunkLabel = parts[0].trim();  // e.g., "Chunk 3"
                    String chunkContent = parts[1];       // The rest is the chunk content.

                    // Determine chunk number.
                    int chunkNum = -1;
                    try {
                        String[] labelParts = chunkLabel.split(" ");
                        if (labelParts.length >= 2) {
                            chunkNum = Integer.parseInt(labelParts[1]);
                        }
                    } catch (NumberFormatException e) {
                        // If parsing fails, treat as normal chunk.
                        chunkNum = -1;
                    }

                    // For chunk 3: Simulate intentional delay on the first reception.
                    if (chunkNum == 3 && !chunk3Delayed) {
                        System.out.println("FileTransferHandler: Delaying ACK for Chunk 3...");
                        // Delay for longer than the server timeout.
                        Thread.sleep(3500);
                        // Now send ACK for Chunk 3.
                        connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "ACK for " + chunkLabel));
                        chunk3Delayed = true;
                    }
                    // For chunk 6: Simulate a delay to cause duplicate ACK situation.
                    else if (chunkNum == 6 && !chunk6Delayed) {
                        System.out.println("FileTransferHandler: Delaying ACK for Chunk 6 (first time)...");
                        Thread.sleep(3500);
                        connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "ACK for " + chunkLabel));
                        chunk6Delayed = true;
                    }
                    // For subsequent receptions or normal chunks, send ACK immediately.
                    else {
                        connection.sendMessage(new Message(MessageType.ACK, connection.getClientId(), "ACK for " + chunkLabel));
                    }

                    // Append the chunk content to the download buffer.
                    downloadBuffer.append(chunkContent);
                    break;
                case FILE_END:
                    // File transfer is complete.
                    System.out.println("Download complete. Saving file...");
                    saveDownloadedFile();
                    // Clear the buffer.
                    downloadBuffer.setLength(0);
                    currentDownloadFile = null;
                    break;
                case NACK:
                    // Show error message.
                    System.out.println("Download error: " + msg.getPayload());
                    break;
                default:
                    // For other types, do nothing.
                    break;
            }
        } catch (InterruptedException e) {
            System.out.println("FileTransferHandler: Interrupted while processing file chunk: " + e.getMessage());
        }
    }

    // Saves the downloaded file to the local ClientFiles directory.
    private static void saveDownloadedFile() {
        if (currentDownloadFile == null || downloadBuffer.isEmpty()) {
            System.out.println("FileTransferHandler: No file data to save.");
            return;
        }
        try {
            File dir = new File("ClientFiles");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File outputFile = new File(dir, currentDownloadFile);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                // Here, we assume the downloaded content is the actual file data (as a string).
                // In a real scenario, we would handle binary data.
                fos.write(downloadBuffer.toString().getBytes());
            }
            System.out.println("FileTransferHandler: File '" + currentDownloadFile + "' saved successfully.");
        } catch (IOException e) {
            System.out.println("FileTransferHandler: Error saving downloaded file: " + e.getMessage());
        }
    }
}
