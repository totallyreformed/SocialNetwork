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
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FileManager {

    private static ConcurrentHashMap<String, Set<String>> photoOwners = new ConcurrentHashMap<>();

    /**
     * Handle file upload: saves the file and caption, updates the profile,
     * notifies followers, and records the owner for search.
     */
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
            File dir = new File("ServerFiles");
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println(Util.getTimestamp() + " FileManager: Created ServerFiles directory.");
            }

            // Decode and save the photo binary
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);
            File photoFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(fileBytes);
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved photo file " + fileName + " (Title: " + photoTitle + ")");

            // Record ownership for search
            photoOwners
                    .computeIfAbsent(fileName, k -> ConcurrentHashMap.newKeySet())
                    .add(clientId);

            // Save the caption
            File captionFile = new File(dir, fileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(caption.getBytes());
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved caption for " + fileName);

            // Update profile and notify followers
            ProfileManager.getInstance().updateProfile(clientId, photoTitle);
            String uploaderUsername = AuthenticationManager.getUsernameByNumericId(clientId);
            String notificationMessage = "User " + uploaderUsername + " uploaded " + photoTitle;
            Set<String> followers = SocialGraphManager.getInstance().getFollowers(clientId);
            if (followers != null) {
                for (String followerId : followers) {
                    NotificationManager.getInstance().addNotification(followerId, notificationMessage);
                }
            }

            // Send success diagnostic
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + fileName));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: UPLOAD completed for client " + clientId);

        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Error during file upload.");
            e.printStackTrace();
        }
    }

    /**
     * Handle file search: returns only those followees who have the photo.
     */
    public static void handleSearch(Message msg,
                                    String clientId,
                                    ObjectOutputStream output) {
        String photoName = msg.getPayload();

        // 1) All known owners of this photo
        Set<String> owners = photoOwners.getOrDefault(photoName, Set.of());
        // 2) Which of those the requester actually follows
        Set<String> followees = SocialGraphManager.getInstance().getFollowees(clientId);

        // 3) Filter to only followees
        Set<String> available = owners.stream()
                .filter(followees::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String result;
        if (available.isEmpty()) {
            result = "Search: no followees have photo " + photoName;
        } else {
            // map each clientId → "id(username)"
            String listing = available.stream()
                    .map(id -> id + "("
                            + AuthenticationManager.getUsernameByNumericId(id)
                            + ")")
                    .collect(Collectors.joining(","));
            result = "Search: found photo " + photoName + " at: " + listing;
        }

        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle file download with Base64‐split into text chunks.
    public static void handleDownload(Message msg, String clientId,
                                      ObjectInputStream input, ObjectOutputStream output) {
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
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
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

            // --- Read the file bytes and Base64-encode them ---
            byte[] fileBytes = Files.readAllBytes(photoFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            int totalLength = base64.length();
            int chunkSize = totalLength / Constants.NUM_CHUNKS;
            if (chunkSize == 0) chunkSize = totalLength;

            // Send in NUM_CHUNKS chunks
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : (i * chunkSize);
                String chunkContent = base64.substring(start, end);

                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                // Stop‐and‐wait with retransmissions and diagnostics
                boolean ackReceived = false;
                int attempts = 0;
                long dynamicTimeout = Constants.TIMEOUT_MILLISECONDS;
                while (!ackReceived && attempts < 3) {
                    long sendTime = System.currentTimeMillis();
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println(Util.getTimestamp() + " FileManager: Sent chunk " + i + " (attempt " + (attempts+1) + ")");
                    // Wait for ACK
                    Message ack = null;
                    long waitStart = System.currentTimeMillis();
                    while (System.currentTimeMillis() - waitStart < dynamicTimeout) {
                        try {
                            ack = (Message) input.readObject();
                            if (ack.getType() == MessageType.ACK && ack.getPayload().contains("Chunk " + i)) {
                                ackReceived = true;
                                long rtt = System.currentTimeMillis() - sendTime;
                                System.out.println(Util.getTimestamp() + " FileManager: ACK received for chunk " + i + " RTT=" + rtt);
                                // send diagnostic to client
                                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                        "ACK received for chunk " + i));
                                output.flush();
                                break;
                            }
                        } catch (IOException | ClassNotFoundException ex) {
                            // ignore, continue waiting
                        }
                    }
                    if (!ackReceived) {
                        attempts++;
                        System.out.println(Util.getTimestamp() + " FileManager: Timeout waiting for ACK for chunk " + i);
                        output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Server did not receive ACK for chunk " + i + "—retransmitting"));
                        output.flush();
                        dynamicTimeout *= 2;
                    }
                    if (attempts >= 3 && !ackReceived) {
                        output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                        output.flush();
                        return;
                    }
                }
            }

            // --- Caption and final diagnostics ---
            if (captionFile.exists()) {
                String captionText = new String(Files.readAllBytes(captionFile.toPath()));
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Caption: " + captionText));
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "No caption available"));
            }
            output.flush();

            // Final complete message
            output.writeObject(new Message(MessageType.FILE_END, "Server", "The transmission is completed"));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: DOWNLOAD completed successfully for " + photoName);

        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Exception during DOWNLOAD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
