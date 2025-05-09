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

/**
 * Handles file download operations by processing incoming file-transfer messages,
 * reconstructing file chunks, acknowledging receipt, and saving completed files
 * to the local client directory.
 */
public class FileTransferHandler {

    // Flags to simulate delayed acknowledgements for specific chunks
    private static boolean chunk3Delayed = false;
    private static boolean chunk6Delayed = false;

    // Buffer for collecting Base64-encoded file data as it arrives
    private static StringBuilder downloadBuffer = new StringBuilder();

    // Name of the file currently being downloaded
    private static String currentDownloadFile = null;

    // Connection used for the current download session
    private static ServerConnection currentConnection = null;

    // Tracks which chunk numbers have already been processed to avoid duplicates
    private static final Set<Integer> seenChunks = new HashSet<>();

    /**
     * Initiates a file download by resetting state and setting the target file name.
     *
     * @param payload    the download command payload in the format ownerName:filename
     * @param connection the ServerConnection to use for receiving chunks
     */
    public static void downloadFile(String payload, ServerConnection connection) {
        currentConnection = connection;
        downloadBuffer.setLength(0);
        seenChunks.clear();
        chunk3Delayed = false;
        chunk6Delayed = false;

        currentDownloadFile = payload.contains(":")
                ? payload.split(":", 2)[1]
                : payload;
        System.out.println("Download initiated for payload: " + payload);
    }

    /**
     * Processes an incoming Message related to file transfer, handling handshake,
     * chunk reception (with simulated delays), duplicate detection, and completion.
     *
     * @param msg  the Message received from the server
     * @param conn the ServerConnection over which to send acknowledgements
     */
    public static void handleIncomingMessage(Message msg, ServerConnection conn) {
        try {
            switch (msg.getType()) {

                case HANDSHAKE:
                    // Respond to initial handshake to begin transfer
                    conn.sendMessage(new Message(MessageType.ACK,
                            conn.getClientId(),
                            "handshake ACK"));
                    break;

                case FILE_CHUNK:
                    // Split payload into chunk label and content
                    String[] parts = msg.getPayload().split(":", 2);
                    if (parts.length < 2) break;
                    String chunkLabel   = parts[0].trim();   // e.g., "Chunk 3"
                    String chunkContent = parts[1].trim();

                    // Parse chunk number from label
                    int chunkNum = -1;
                    try {
                        chunkNum = Integer.parseInt(chunkLabel.split(" ")[1]);
                    } catch (Exception ignored) {}

                    // Simulate delayed ACKs for testing network conditions
                    if (chunkNum == 3 && !chunk3Delayed) {
                        Thread.sleep(3500);
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk 3"));
                        chunk3Delayed = true;
                    } else if (chunkNum == 6 && !chunk6Delayed) {
                        Thread.sleep(3500);
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk 6"));
                        chunk6Delayed = true;
                    } else {
                        // Acknowledge other chunks immediately
                        conn.sendMessage(new Message(MessageType.ACK,
                                conn.getClientId(),
                                "ACK for Chunk " + chunkNum));
                    }

                    // Append content only if this chunk has not been seen before
                    if (!seenChunks.contains(chunkNum)) {
                        downloadBuffer.append(chunkContent);
                        seenChunks.add(chunkNum);
                    }
                    break;

                case FILE_END:
                    // Complete download: output message and save file
                    System.out.println(msg.getPayload());
                    System.out.println("Download complete. Saving file...");
                    saveDownloadedFile();
                    downloadBuffer.setLength(0);
                    currentDownloadFile = null;
                    currentConnection = null;
                    break;

                case NACK:
                    // Handle negative acknowledgement by logging error
                    System.out.println("Download error: " + msg.getPayload());
                    break;

                default:
                    // Ignore unrelated messages
                    break;
            }
        } catch (InterruptedException e) {
            System.out.println("FileTransferHandler: Interrupted: " + e.getMessage());
        }
    }

    /**
     * Decodes the accumulated Base64 data and writes the resulting bytes
     * to the client-specific download directory, then marks the event to
     * prevent re-upload by FileSyncManager.
     */
    private static void saveDownloadedFile() {
        // Validate that data is present
        if (currentDownloadFile == null || downloadBuffer.length() == 0) {
            System.out.println("FileTransferHandler: No file data to save.");
            return;
        }
        try {
            // Clean and decode Base64 payload
            String base64Data = downloadBuffer.toString().replaceAll("\\s+", "");
            byte[] fileBytes  = Base64.getDecoder().decode(base64Data);

            // Prepare output directory: ClientFiles/<groupID>client<id>/
            String clientId = currentConnection.getClientId();
            File dir = new File("ClientFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Write file to disk
            File out = new File(dir, currentDownloadFile);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(fileBytes);
            }
            System.out.println("FileTransferHandler: File '" + currentDownloadFile
                    + "' saved successfully to " + dir.getPath());

            // Notify registry to skip re-upload of this newly downloaded file
            ClientSyncRegistry.markEvent(out.toPath());
        } catch (IOException e) {
            System.out.println("FileTransferHandler: Error saving downloaded file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("FileTransferHandler: Failed to decode Base64 data: " + e.getMessage());
        }
    }
}
