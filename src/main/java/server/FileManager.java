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

            // --- Handshake ---
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server", "Initiate handshake for " + photoName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: Handshake initiated for " + photoName);

            long handshakeStart = System.currentTimeMillis();
            Message ackMsg = null;
            // Use a dynamic loop waiting up to 5000ms
            while (System.currentTimeMillis() - handshakeStart < 5000) {
                try {
                    ackMsg = (Message) input.readObject();
                    if (ackMsg.getType() == MessageType.ACK && ackMsg.getPayload().contains("handshake")) {
                        break;
                    }
                } catch (IOException ex) {
                    // Log and continue waiting
                    System.out.println(Util.getTimestamp() + " FileManager: Exception during handshake waiting: " + ex.getMessage());
                }
            }
            if (ackMsg == null || ackMsg.getType() != MessageType.ACK || !ackMsg.getPayload().contains("handshake")) {
                output.writeObject(new Message(MessageType.NACK, "Server", "Handshake failed."));
                output.flush();
                System.out.println(Util.getTimestamp() + " FileManager: Handshake failed, aborting DOWNLOAD.");
                return;
            }
            long handshakeRTT = System.currentTimeMillis() - handshakeStart;
            System.out.println(Util.getTimestamp() + " FileManager: Handshake acknowledged by client. RTT = " + handshakeRTT + " ms");

            // --- File Transfer ---
            // Read file data.
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

            // Set initial dynamic timeout based on handshake RTT (or use a fixed initial value)
            long dynamicTimeout = (handshakeRTT > 0) ? handshakeRTT * 2 : Constants.TIMEOUT_MILLISECONDS;

            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : i * chunkSize;
                String chunkContent = new String(fileData, start, end - start);
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                boolean ackReceived = false;
                int attempts = 0;
                int maxAttempts = 3;
                long chunkSendTime = 0;
                while (!ackReceived && attempts < maxAttempts) {
                    chunkSendTime = System.currentTimeMillis();
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println(Util.getTimestamp() + " FileManager: Sent chunk " + i + " (attempt " + (attempts + 1) + "). Waiting for ACK...");
                    boolean gotAck = false;
                    Message ackChunk = null;
                    long ackWaitStart = System.currentTimeMillis();
                    // Wait for ACK for this chunk for up to dynamicTimeout ms.
                    while (System.currentTimeMillis() - ackWaitStart < dynamicTimeout) {
                        try {
                            ackChunk = (Message) input.readObject();
                            if (ackChunk.getType() == MessageType.ACK && ackChunk.getPayload().contains("Chunk " + i)) {
                                gotAck = true;
                                break;
                            }
                        } catch (IOException ex) {
                            System.out.println(Util.getTimestamp() + " FileManager: Exception while waiting for ACK for chunk " + i + ": " + ex.getMessage());
                        }
                    }
                    if (gotAck) {
                        ackReceived = true;
                        long rtt = System.currentTimeMillis() - chunkSendTime;
                        System.out.println(Util.getTimestamp() + " FileManager: ACK received for chunk " + i + ". RTT = " + rtt + " ms");
                        // Adjust dynamic timeout: use a simple average between previous timeout and measured RTT, but not lower than a minimum.
                        dynamicTimeout = Math.max((dynamicTimeout + rtt) / 2, Constants.TIMEOUT_MILLISECONDS);
                    } else {
                        attempts++;
                        System.out.println(Util.getTimestamp() + " FileManager: Timeout waiting for ACK for chunk " + i + " (Attempt " + (attempts + 1) + ")");
                        // Increase dynamic timeout for the next attempt (exponential backoff).
                        dynamicTimeout *= 2;
                        if (attempts >= maxAttempts) {
                            System.out.println(Util.getTimestamp() + " FileManager: Max retransmissions reached for chunk " + i + ". Aborting DOWNLOAD.");
                            output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                            output.flush();
                            return;
                        }
                    }
                }
            }

            // --- Caption and Completion ---
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
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: DOWNLOAD completed successfully for " + photoName);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Exception during DOWNLOAD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
