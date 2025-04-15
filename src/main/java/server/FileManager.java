package server;

import common.Message;
import common.Message.MessageType;
import common.Constants;
import common.Util;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.Set;

public class FileManager {

    // Handle file upload: saves the file and caption, and updates the profile.
    public static void handleUpload(Message msg, String clientId, ObjectOutputStream output) {
        System.out.println(Util.getTimestamp() + " FileManager: Processing UPLOAD from client " + clientId);
        String payload = msg.getPayload();
        String[] parts = payload.split("\\|");
        String photoTitle = "";
        String fileName = "";
        String caption = "";
        String base64Data = "";
        for (String part : parts) {
            if (part.startsWith("photoTitle:"))
                photoTitle = part.substring("photoTitle:".length());
            else if (part.startsWith("fileName:"))
                fileName = part.substring("fileName:".length());
            else if (part.startsWith("caption:"))
                caption = part.substring("caption:".length());
            else if (part.startsWith("data:"))
                base64Data = part.substring("data:".length());
        }

        try {
            // Ensure the ServerFiles directory exists.
            File dir = new File("ServerFiles");
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println(Util.getTimestamp() + " FileManager: Created ServerFiles directory.");
            }

            // Save the photo file using the provided file name.
            File photoFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(base64Data.getBytes());
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved photo file " + fileName + " (Title: " + photoTitle + ")");

            // Save the caption in a .txt file.
            File captionFile = new File(dir, fileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(caption.getBytes());
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved caption for " + fileName);

            // Update the uploader's profile with the new upload.
            ProfileManager.getInstance().updateProfile(clientId, photoTitle);

            // Notify each follower of this new upload.
            String uploaderUsername = AuthenticationManager.getUsernameByNumericId(clientId);
            String notificationMessage = "User " + uploaderUsername + " uploaded " + photoTitle;
            Set<String> followers = SocialGraphManager.getInstance().getFollowers(clientId); // Assumes getFollowers() is implemented.
            if (followers != null) {
                for (String followerId : followers) {
                    NotificationManager.getInstance().addNotification(followerId, notificationMessage);
                    String followerUsername = AuthenticationManager.getUsernameByNumericId(followerId);
                    System.out.println(Util.getTimestamp() + " FileManager: Notification sent to follower " + followerUsername + " (" + followerId + ")");
                }
            }

            // Send a diagnostic message back to the uploader.
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + fileName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: UPLOAD completed for client " + clientId);

        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Error during file upload.");
            e.printStackTrace();
        }
    }

    // Handle file search: returns a diagnostic message based on file existence.
    public static void handleSearch(Message msg, String clientId, ObjectOutputStream output) {
        System.out.println(Util.getTimestamp() + " FileManager: Processing SEARCH for client " + clientId);
        String photoName = msg.getPayload();
        File file = new File("ServerFiles/" + photoName);
        String result;
        if (file.exists()) {
            result = "Found photo " + photoName + ". Owner: " + AuthenticationManager.getUsernameByNumericId(clientId);
            System.out.println(Util.getTimestamp() + " FileManager: SEARCH found file " + photoName);
        } else {
            result = "Photo " + photoName + " not found.";
            System.out.println(Util.getTimestamp() + " FileManager: SEARCH did not find file " + photoName);
        }
        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Error sending SEARCH result.");
            e.printStackTrace();
        }
    }

    // Handle file download with a simulated handshake and retransmission mechanism.
    public static void handleDownload(Message msg, String clientId, ObjectInputStream input, ObjectOutputStream output) {
        System.out.println(Util.getTimestamp() + " FileManager: Processing DOWNLOAD for client " + clientId);
        String photoName = msg.getPayload();
        File dir = new File("ServerFiles");
        File photoFile = new File(dir, photoName);
        File captionFile = new File(dir, photoName + ".txt");

        try {
            if (!photoFile.exists()) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "File " + photoName + " not found."));
                output.flush();
                System.out.println(Util.getTimestamp() + " FileManager: DOWNLOAD aborted – file not found.");
                return;
            }

            // Step 1: Initiate handshake.
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server", "Initiate handshake for " + photoName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: Handshake initiated for " + photoName);

            // Step 2: Wait for a handshake ACK for up to 5 seconds.
            long startTime = System.currentTimeMillis();
            Message ackMsg = null;
            while (System.currentTimeMillis() - startTime < 5000) {
                try {
                    ackMsg = (Message) input.readObject();
                    if (ackMsg.getType() == MessageType.ACK && ackMsg.getPayload().contains("handshake")) {
                        break;
                    }
                    // If we receive a different message, we continue waiting.
                } catch (Exception ex) {
                    // Optionally log the exception and continue attempting to read.
                    System.out.println(Util.getTimestamp() + " FileManager: Exception while waiting for handshake ACK: " + ex.getMessage());
                }
            }

            if (ackMsg == null || ackMsg.getType() != MessageType.ACK || !ackMsg.getPayload().contains("handshake")) {
                output.writeObject(new Message(MessageType.NACK, "Server", "Handshake failed."));
                output.flush();
                System.out.println(Util.getTimestamp() + " FileManager: Handshake failed, aborting DOWNLOAD.");
                return;
            }
            System.out.println(Util.getTimestamp() + " FileManager: Handshake acknowledged by client.");

            // Step 3: Read the file data.
            byte[] fileData = new byte[(int) photoFile.length()];
            try (FileInputStream fis = new FileInputStream(photoFile)) {
                int bytesRead = fis.read(fileData);
                System.out.println(Util.getTimestamp() + " FileManager: Read " + bytesRead + " bytes from " + photoName);
            }
            int totalLength = fileData.length;
            int chunkSize = totalLength / Constants.NUM_CHUNKS;
            if (chunkSize == 0) {
                chunkSize = totalLength;
            }

            // For each simulated chunk, send the chunk and wait for an ACK.
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : i * chunkSize;
                String chunkContent = new String(fileData, start, end - start);
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                boolean ackReceived = false;
                int attempts = 0;
                int maxAttempts = 3;
                while (!ackReceived && attempts < maxAttempts) {
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println(Util.getTimestamp() + " FileManager: Sent chunk " + i + " (attempt " + (attempts + 1) + "). Waiting for ACK...");
                    Message ackChunk = (Message) input.readObject();
                    if (ackChunk.getType() == MessageType.ACK && ackChunk.getPayload().contains("Chunk " + i)) {
                        ackReceived = true;
                        System.out.println(Util.getTimestamp() + " FileManager: ACK received for chunk " + i);
                    } else {
                        attempts++;
                        System.out.println(Util.getTimestamp() + " FileManager: Incorrect or no ACK for chunk " + i + ". Attempt " + (attempts + 1));
                    }
                    if (!ackReceived && attempts >= maxAttempts) {
                        System.out.println(Util.getTimestamp() + " FileManager: Max retransmissions reached for chunk " + i + ". Aborting DOWNLOAD.");
                        output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                        output.flush();
                        return;
                    }
                }
            }

            // Step 4: Send the caption if available.
            String captionText;
            if (captionFile.exists()) {
                byte[] capData = new byte[(int) captionFile.length()];
                try (FileInputStream fis = new FileInputStream(captionFile)) {
                    fis.read(capData);
                }
                captionText = new String(capData);
                System.out.println(Util.getTimestamp() + " FileManager: Caption found for " + photoName);
            } else {
                captionText = "No caption available for " + photoName;
                System.out.println(Util.getTimestamp() + " FileManager: Caption missing for " + photoName);
            }
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Caption: " + captionText));
            output.flush();

            // Final completion message.
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: DOWNLOAD completed successfully for " + photoName);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Exception during DOWNLOAD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
