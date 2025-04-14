package server;

import common.Message;
import common.Message.MessageType;
import common.Constants;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class FileManager {

    // Handle file upload. Payload format: "photoName:<name>|caption:<text>|data:<base64Data>"
    public static void handleUpload(Message msg, String clientId, ObjectOutputStream output) {
        System.out.println("FileManager: Processing UPLOAD from client " + clientId);
        String payload = msg.getPayload();
        String[] parts = payload.split("\\|");
        String photoName = "";
        String caption = "";
        String base64Data = "";
        for (String part : parts) {
            if (part.startsWith("photoName:"))
                photoName = part.substring("photoName:".length());
            else if (part.startsWith("caption:"))
                caption = part.substring("caption:".length());
            else if (part.startsWith("data:"))
                base64Data = part.substring("data:".length());
        }
        try {
            // Ensure server directory exists.
            File dir = new File("ServerFiles");
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println("FileManager: Created ServerFiles directory.");
            }
            // Write photo data (simulated by writing the base64 string).
            File photoFile = new File(dir, photoName);
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(base64Data.getBytes());
            }
            System.out.println("FileManager: Saved photo file " + photoName);

            // Write caption file.
            File captionFile = new File(dir, photoName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(caption.getBytes());
            }
            System.out.println("FileManager: Saved caption for " + photoName);

            // Update client's profile.
            ProfileManager.getInstance().updateProfile(clientId, clientId + " posted " + photoName);
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + photoName));
            output.flush();
            System.out.println("FileManager: UPLOAD completed for client " + clientId);
        } catch (IOException e) {
            System.out.println("FileManager: Error during file upload.");
            e.printStackTrace();
        }
    }

    // Handle file search by checking if the file exists.
    public static void handleSearch(Message msg, String clientId, ObjectOutputStream output) {
        System.out.println("FileManager: Processing SEARCH for client " + clientId);
        String photoName = msg.getPayload();
        File file = new File("ServerFiles/" + photoName);
        String result;
        if (file.exists()) {
            result = "Found photo " + photoName + " (owner: dummyOwner)";
            System.out.println("FileManager: SEARCH found file " + photoName);
        } else {
            result = "Photo " + photoName + " not found.";
            System.out.println("FileManager: SEARCH did not find file " + photoName);
        }
        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            System.out.println("FileManager: Error sending SEARCH result.");
            e.printStackTrace();
        }
    }

    // Handle file download with a simulated 3-way handshake and stop-and-wait protocol.
    public static void handleDownload(Message msg, String clientId, ObjectInputStream input, ObjectOutputStream output) {
        System.out.println("FileManager: Processing DOWNLOAD for client " + clientId);
        String photoName = msg.getPayload();
        File dir = new File("ServerFiles");
        File photoFile = new File(dir, photoName);
        File captionFile = new File(dir, photoName + ".txt");

        try {
            if (!photoFile.exists()) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "File " + photoName + " not found."));
                output.flush();
                System.out.println("FileManager: DOWNLOAD aborted – file not found.");
                return;
            }

            // Step 1: Handshake initiation.
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server", "Initiate handshake for " + photoName));
            output.flush();
            System.out.println("FileManager: Handshake initiated for " + photoName);

            // Step 2: Wait for handshake ACK.
            Message ackMsg = (Message) input.readObject();
            if (ackMsg.getType() != MessageType.ACK || !ackMsg.getPayload().contains("handshake")) {
                output.writeObject(new Message(MessageType.NACK, "Server", "Handshake failed."));
                output.flush();
                System.out.println("FileManager: Handshake failed, aborting DOWNLOAD.");
                return;
            }
            System.out.println("FileManager: Handshake acknowledged by client.");

            // Step 3: Read file data.
            byte[] fileData = new byte[(int) photoFile.length()];
            try (FileInputStream fis = new FileInputStream(photoFile)) {
                fis.read(fileData);
            }
            int totalLength = fileData.length;
            int chunkSize = totalLength / Constants.NUM_CHUNKS;
            if (chunkSize == 0) chunkSize = totalLength;

            // Simulate sending NUM_CHUNKS chunks.
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : i * chunkSize;
                String chunkContent = new String(fileData, start, end - start);
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                boolean ackReceived = false;
                int attempts = 0;
                int maxAttempts = 3;

                // For chunks 3 and 6, simulate retransmission.
                while (!ackReceived && attempts < maxAttempts) {
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println("FileManager: Sent chunk " + i + " (attempt " + (attempts+1) + "). Waiting for ACK...");
                    try {
                        // Wait for ACK within timeout.
                        Message ackChunk = (Message) input.readObject();
                        if (ackChunk.getType() == MessageType.ACK && ackChunk.getPayload().contains("Chunk " + i)) {
                            ackReceived = true;
                            System.out.println("FileManager: ACK received for chunk " + i);
                        } else {
                            attempts++;
                            System.out.println("FileManager: Incorrect ACK for chunk " + i + ". Attempt " + (attempts+1));
                        }
                    } catch (Exception ex) {
                        attempts++;
                        System.out.println("FileManager: Timeout waiting for ACK for chunk " + i + ". Attempt " + (attempts+1));
                    }
                    if (!ackReceived && attempts >= maxAttempts) {
                        System.out.println("FileManager: Max retransmissions reached for chunk " + i + ". Aborting DOWNLOAD.");
                        output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                        output.flush();
                        return;
                    }
                }
            }

            // After sending all chunks, send caption if available.
            String captionText;
            if (captionFile.exists()) {
                byte[] capData = new byte[(int) captionFile.length()];
                try (FileInputStream fis = new FileInputStream(captionFile)) {
                    fis.read(capData);
                }
                captionText = new String(capData);
                System.out.println("FileManager: Caption found for " + photoName);
            } else {
                captionText = "No caption available for " + photoName;
                System.out.println("FileManager: Caption missing for " + photoName);
            }
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Caption: " + captionText));
            output.flush();

            // Final completion message.
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
            System.out.println("FileManager: DOWNLOAD completed successfully for " + photoName);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("FileManager: Exception during DOWNLOAD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
