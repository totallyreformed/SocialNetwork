package server;

import common.Message;
import common.Message.MessageType;
import common.Constants;
import common.Util;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages photo file operations including upload, search, and download.
 * Supports Base64 encoding/decoding, storage in server directories,
 * ownership tracking, and client notifications.
 */
public class FileManager {

    /**
     * Maps a filename to the set of client IDs owning that photo.
     */
    private static ConcurrentHashMap<String, Set<String>> photoOwners = new ConcurrentHashMap<>();
    /**
     * Maps a lowercase photo title to the set of client IDs owning that title.
     */
    private static ConcurrentHashMap<String, Set<String>> titleOwners = new ConcurrentHashMap<>();
    /**
     * Maps a lowercase photo title to a representative filename for lookup.
     */
    private static ConcurrentHashMap<String, String> titleToFileName = new ConcurrentHashMap<>();

    /**
     * Handles a client upload request by saving the photo and caption,
     * updating search indices, notifying followers, and sending a response.
     *<p>
     * Parses the payload for title, filename, caption, and data; decodes or reads
     * file bytes; writes to ServerFiles; updates photoOwners and titleOwners;
     * notifies followers and the uploading client via diagnostic messages.
     *
     * @param msg      the upload Message containing metadata and optional Base64 data
     * @param clientId the numeric ID of the uploading client
     * @param output   the ObjectOutputStream to send response Messages
     */
    public static void handleUpload(Message msg, String clientId, ObjectOutputStream output) {
        System.out.println(Util.getTimestamp() + " FileManager: Processing UPLOAD from client " + clientId);

        // Parse payload parts
        String payload = msg.getPayload();
        String[] parts = payload.split("\\|");
        String photoTitle = "", fileName = "", caption = "", base64Data = "";
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
            // Ensure server sub-directory exists
            File dir = new File("ServerFiles/" + Constants.GROUP_ID + "client" + clientId);
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println(Util.getTimestamp() + " FileManager: Created directory " + dir.getPath());
            }

            // Determine file bytes: from Base64 or client folder
            byte[] fileBytes;
            if (!base64Data.isEmpty()) {
                fileBytes = Base64.getDecoder().decode(base64Data);
            } else {
                Path clientPath = Paths.get("ClientFiles",
                        Constants.GROUP_ID + "client" + clientId,
                        fileName);
                fileBytes = Files.readAllBytes(clientPath);
            }

            // Save photo file on server
            File photoFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(fileBytes);
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved photo file " + fileName);
            // Mark this file so DirectoryWatcher will skip re-syncing it
            SyncRegistry.markEvent(photoFile.toPath());

            // Record ownership for filename-based search
            photoOwners
                    .computeIfAbsent(fileName, k -> ConcurrentHashMap.newKeySet())
                    .add(clientId);

            // Record ownership for title-based search (case-insensitive)
            String key = photoTitle.trim().toLowerCase();
            titleOwners
                    .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(clientId);
            titleToFileName.putIfAbsent(key, fileName);

            // Determine caption text and save on server
            String captionText = caption;
            if (captionText.isEmpty()) {
                Path clientCaption = Paths.get("ClientFiles",
                        Constants.GROUP_ID + "client" + clientId,
                        fileName + ".txt");
                if (Files.exists(clientCaption)) {
                    captionText = new String(Files.readAllBytes(clientCaption));
                }
            }
            File captionFile = new File(dir, fileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(captionText.getBytes());
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved caption for " + fileName);
            // Mark this file so DirectoryWatcher will skip re-syncing it
            SyncRegistry.markEvent(captionFile.toPath());

            // Notify followers of new upload
            ProfileManager.getInstance().updateProfile(clientId, photoTitle);
            String uploaderUsername = AuthenticationManager.getUsernameByNumericId(clientId);
            String notification = "User " + uploaderUsername + " uploaded " + photoTitle;
            Set<String> followers = SocialGraphManager.getInstance().getFollowers(clientId);
            if (followers != null) {
                for (String f : followers) {
                    // queue for offline
                    NotificationManager.getInstance().addNotification(f, notification);
                    // live push & purge
                    ClientHandler h = ClientHandler.activeClients.get(f);
                    if (h != null) {
                        h.sendExternalMessage(new Message(
                                MessageType.DIAGNOSTIC,
                                "Server",
                                notification
                        ));
                        NotificationManager.getInstance()
                                .removeNotification(f, notification);
                    }
                }
            }

            // Acknowledge successful upload
            output.writeObject(new Message(
                    MessageType.DIAGNOSTIC,
                    "Server",
                    "Upload successful for " + fileName
            ));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: UPLOAD completed for client " + clientId);

        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " FileManager: Error during file upload.");
            e.printStackTrace();
        }
    }

    /**
     * Processes a search request for photos by title or filename, filters results
     * to the client's followees, and returns a diagnostic message listing matches.
     *
     * @param msg      the search Message containing the query
     * @param clientId the numeric ID of the searching client
     * @param output   the ObjectOutputStream to send the search result
     */
    public static void handleSearch(Message msg,
                                    String clientId,
                                    ObjectOutputStream output) {
        String query    = msg.getPayload().trim();
        String key      = query.toLowerCase();
        String fileName = titleToFileName.get(key);

        Set<String> owners;
        if (fileName != null) {
            // title match
            owners = titleOwners.getOrDefault(key, Set.of());
        } else {
            // fallback: interpret query as a filename
            fileName = query;
            owners   = photoOwners.getOrDefault(fileName, Set.of());
        }

        // Filter to only followees
        Set<String> followees = SocialGraphManager.getInstance().getFollowees(clientId);
        Set<String> available = owners.stream()
                .filter(followees::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String result;
        if (available.isEmpty()) {
            result = "Search: no followees have photo " + query;
        } else {
            String listing = available.stream()
                    .map(id -> id + "(" + AuthenticationManager.getUsernameByNumericId(id) + ")")
                    .collect(Collectors.joining(","));
            result = "Search: found photo " + fileName + " at: " + listing;
        }

        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles a download request by performing a handshake and streaming
     * file chunks with stop-and-wait reliability, then sends captions and EOF.
     *
     * @param msg           the download Message specifying owner and filename
     * @param downloaderId  the numeric ID of the downloading client
     * @param clientSocket  the Socket for the client connection
     * @param input         the ObjectInputStream to receive ACKs
     * @param output        the ObjectOutputStream to send chunks and messages
     * @throws IOException if an I/O error occurs during transfer
     */
    public static void handleDownload(Message msg,
                                      String downloaderId,
                                      Socket clientSocket,
                                      ObjectInputStream input,
                                      ObjectOutputStream output) throws IOException {
        System.out.println(Util.getTimestamp() + " FileManager: Processing DOWNLOAD for client " + downloaderId);

        // 1) Parse ownerName:photoName
        String[] parts = msg.getPayload().split(":", 2);
        String ownerName = parts.length == 2 ? parts[0] : AuthenticationManager.getUsernameByNumericId(downloaderId);
        String photoName = parts.length == 2 ? parts[1] : msg.getPayload();

        // 2) Resolve ownerName → ownerId
        String ownerId = AuthenticationManager.getClientIdByUsername(ownerName);
        if (ownerId == null) {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "Download failed: User '" + ownerName + "' not found."));
            output.flush();
            return;
        }

        // 3) Locate files
        File ownerDir    = new File("ServerFiles/" + Constants.GROUP_ID + "client" + ownerId);
        File photoFile   = new File(ownerDir, photoName);
        File captionFile = new File(ownerDir, photoName + ".txt");

        if (!photoFile.exists()) {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "File " + photoName + " not found."));
            output.flush();
            System.out.println(Util.getTimestamp() + " FileManager: DOWNLOAD aborted – file not found.");
            return;
        }

        // prepare raw input stream for available()
        InputStream rawIn = clientSocket.getInputStream();

        try {
            // 4) Three-way handshake
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server",
                    "Initiate handshake for " + photoName));
            output.flush();
            // wait up to 5s for handshake ACK
            long hsStart = System.currentTimeMillis();
            Message hsAck = null;
            while (System.currentTimeMillis() - hsStart < 5000) {
                if (rawIn.available() > 0) {
                    hsAck = (Message) input.readObject();
                    if (hsAck.getType() == MessageType.ACK && hsAck.getPayload().contains("handshake")) {
                        break;
                    }
                }
            }
            if (hsAck == null || hsAck.getType() != MessageType.ACK) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Handshake failed."));
                output.flush();
                return;
            }
            System.out.println(Util.getTimestamp() + " FileManager: Handshake complete");

            // 5) Read and encode file
            byte[] bytes  = Files.readAllBytes(photoFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            int totalLen  = base64.length();
            int chunkSize = totalLen / Constants.NUM_CHUNKS;
            if (chunkSize == 0) chunkSize = totalLen;

            // 6) Stop-and-wait chunk transfer with retransmission
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end   = (i == Constants.NUM_CHUNKS) ? totalLen : i * chunkSize;
                String content = base64.substring(start, end);
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server",
                        "Chunk " + i + ": " + content);

                int attempts = 0;
                boolean ackReceived = false;
                while (attempts < 2 && !ackReceived) {
                    // send chunk
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println(Util.getTimestamp()
                            + " FileManager: Sent chunk " + i + " (attempt " + (attempts+1) + ")");

                    // wait for ACK or timeout
                    long sendTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - sendTime < Constants.TIMEOUT_MILLISECONDS) {
                        if (rawIn.available() > 0) {
                            Message resp = (Message) input.readObject();
                            if (resp.getType() == MessageType.ACK
                                    && resp.getPayload().contains("Chunk " + i)) {
                                long rtt = System.currentTimeMillis() - sendTime;
                                System.out.println(Util.getTimestamp()
                                        + " FileManager: ACK received for chunk " + i + " RTT=" + rtt + " ms");

                                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                        "ACK received for chunk " + i));
                                output.flush();
                                ackReceived = true;
                            }
                            // ignore any other messages
                        }
                        if (ackReceived) break;
                    }

                    if (!ackReceived) {
                        // timeout expired without ACK → retransmit
                        attempts++;
                        System.out.println(Util.getTimestamp()
                                + " FileManager: Server did not receive ACK for chunk " + i
                                + " — retransmitting");
                        output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                                "Server did not receive ACK for chunk " + i
                                        + " — retransmitting"));
                        output.flush();
                    }
                }

                if (!ackReceived) {
                    output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                            "Download aborted at chunk " + i));
                    output.flush();
                    return;
                }
                // if there’s a delayed second ACK for the same chunk, it will be read by the next iteration’s rawIn.available() but ignored
            }

            // 7) Send caption or notify none
            if (captionFile.exists()) {
                String cap = new String(Files.readAllBytes(captionFile.toPath()));
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Caption: " + cap));
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "No caption available"));
            }
            output.flush();

            // 8) Signal end of transmission
            output.writeObject(new Message(MessageType.FILE_END, "Server",
                    "The transmission is completed"));
            output.flush();
            System.out.println(Util.getTimestamp()
                    + " FileManager: DOWNLOAD completed successfully for " + photoName);

            // 9) Record the downloader as an owner for search purposes
            photoOwners
                    .computeIfAbsent(photoName, k -> ConcurrentHashMap.newKeySet())
                    .add(downloaderId);

        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }
}