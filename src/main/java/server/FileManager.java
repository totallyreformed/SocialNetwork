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
import java.util.*;
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

        // --- 1) Parse payload parts ---
        String payload     = msg.getPayload();
        String[] parts     = payload.split("\\|");
        String photoTitle  = "", fileName = "", captionEn = "", captionGr = "", base64Data = "";

        for (String part : parts) {
            if (part.startsWith("photoTitle:")) {
                photoTitle = part.substring("photoTitle:".length());
            } else if (part.startsWith("fileName:")) {
                fileName = part.substring("fileName:".length());
            } else if (part.startsWith("captionEn:")) {
                captionEn = part.substring("captionEn:".length());
            } else if (part.startsWith("captionGr:")) {
                captionGr = part.substring("captionGr:".length());
            } else if (part.startsWith("data:")) {
                base64Data = part.substring("data:".length());
            }
        }

        try {
            // Decode the image bytes
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            // Server directory for this client
            Path dir = Paths.get("ServerFiles", Constants.GROUP_ID + "client" + clientId);
            Files.createDirectories(dir);

            // --- 2) Save the photo file ---
            File photoFile = new File(dir.toFile(), fileName);
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(fileBytes);
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved photo file " + fileName);
            SyncRegistry.markEvent(photoFile.toPath());

            // --- 3) Update search indices ---
            // by filename
            photoOwners
                    .computeIfAbsent(fileName, k -> ConcurrentHashMap.newKeySet())
                    .add(clientId);
            // by title
            String key = photoTitle.trim().toLowerCase();
            titleOwners
                    .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(clientId);
            titleToFileName.putIfAbsent(key, fileName);

            // --- 4) Save bilingual captions ---
            // Always write the English caption (even if empty, to clear old data)
            File capEnFile = new File(dir.toFile(), fileName + "_en.txt");
            try (FileOutputStream fos = new FileOutputStream(capEnFile)) {
                fos.write(captionEn.getBytes());
            }
            System.out.println(Util.getTimestamp() + " FileManager: Saved English caption for " + fileName);

            // Only write a Greek caption file if one was provided
            if (!captionGr.isEmpty()) {
                File capGrFile = new File(dir.toFile(), fileName + "_gr.txt");
                try (FileOutputStream fos = new FileOutputStream(capGrFile)) {
                    fos.write(captionGr.getBytes());
                }
                System.out.println(Util.getTimestamp() + " FileManager: Saved Greek caption for " + fileName);
            }

            // 5) Notify followers of new upload
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

            // 6) Propagate new post into each follower's Others file
            if (followers != null && !followers.isEmpty()) {
                String postEntry = "[" + Util.getTimestamp() + "] New post from "
                        + uploaderUsername + ": " + photoTitle;
                for (String f : followers) {
                    Path followerDir = Paths.get("ServerFiles", Constants.GROUP_ID + "client" + f);
                    Files.createDirectories(followerDir);
                    File othersFile = new File(
                            followerDir.toFile(),
                            Constants.OTHERS_PREFIX + Constants.GROUP_ID + "client" + f + ".txt"
                    );
                    try (FileWriter fw = new FileWriter(othersFile, true)) {
                        fw.write(postEntry + "\n");
                    }
                    SyncRegistry.markEvent(othersFile.toPath());
                }
            }

            // 7) Acknowledge upload
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
     * Processes a search request carrying both preferred language and query,
     * filters results to the client’s followees, and returns a diagnostic message listing matches.
     * Now also filters owners by existence of the requested-language caption file.
     *
     * @param msg      the search Message containing "lang:<en|gr>|query:<photoTitle>"
     * @param clientId the numeric ID of the searching client
     * @param output   the ObjectOutputStream to send the search result
     */
    public static void handleSearch(Message msg,
                                    String clientId,
                                    ObjectOutputStream output) {
        // 1) Parse payload
        Map<String, String> map = Util.parsePayload(msg.getPayload());
        String lang  = map.getOrDefault("lang", "en");
        String query = map.getOrDefault("query", "").trim();

        // 2) Determine file name and candidate owners
        String key      = query.toLowerCase();
        String fileName = titleToFileName.get(key);
        Set<String> owners;
        if (fileName != null) {
            owners = titleOwners.getOrDefault(key, Set.of());
        } else {
            fileName = query;
            owners   = photoOwners.getOrDefault(fileName, Set.of());
        }

        // 3) Filter by followees
        Set<String> followees = SocialGraphManager.getInstance().getFollowees(clientId);
        Set<String> available = owners.stream()
                .filter(followees::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Further filter by existence of caption in requested language
        String finalFileName = fileName;
        available = available.stream()
                .filter(ownerId -> {
                    // caption file: ServerFiles/<group>client<ownerId>/<fileName>_<lang>.txt
                    Path cap = Paths.get("ServerFiles",
                            Constants.GROUP_ID + "client" + ownerId,
                            finalFileName + "_" + lang + ".txt");
                    return Files.exists(cap);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 5) Build result
        String result;
        if (available.isEmpty()) {
            result = "Search: no followees have photo " + query + " (" + lang + ")";
        } else {
            String listing = available.stream()
                    .map(id -> id + "(" + AuthenticationManager.getUsernameByNumericId(id) + ")")
                    .collect(Collectors.joining(","));
            result = "Search: found photo " + fileName + " (" + lang + ") at: " + listing;
        }

        // 6) Send back
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

        // 1) Parse payload
        Map<String,String> map = Util.parsePayload(msg.getPayload());
        String lang       = map.get("lang");
        String of         = map.get("ownerFilename");
        String[] parts    = of.split(":", 2);
        String ownerName  = parts[0];
        String photoName  = parts[1];

        // 2) Resolve ownerName → ownerId
        String ownerId = AuthenticationManager.getClientIdByUsername(ownerName);
        if (ownerId == null) {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "Download failed: User '" + ownerName + "' not found."));
            output.flush();
            return;
        }

        // 3) Locate photo file
        File ownerDir  = new File("ServerFiles/" + Constants.GROUP_ID + "client" + ownerId);
        File photoFile = new File(ownerDir, photoName);
        if (!photoFile.exists()) {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                    "File " + photoName + " not found."));
            output.flush();
            return;
        }

        InputStream rawIn = clientSocket.getInputStream();

        try {
            // 4) Handshake
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server",
                    "Initiate handshake for " + photoName));
            output.flush();
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
            int numChunks = Constants.NUM_CHUNKS;
            int chunkSize = totalLen / numChunks;
            if (chunkSize == 0) chunkSize = totalLen;

            // 6) Prepare chunk messages
            List<Message> chunks = new ArrayList<>(numChunks + 1);
            for (int i = 1; i <= numChunks; i++) {
                int start = (i - 1) * chunkSize;
                int end   = (i == numChunks) ? totalLen : i * chunkSize;
                String content = base64.substring(start, end);
                chunks.add(new Message(MessageType.FILE_CHUNK, "Server",
                        "Chunk " + i + ": " + content));
            }

            // 7) Go-Back-N window = 3, cumulative ACKs
            int base = 1, nextSeq = 1;
            final int N = numChunks, WINDOW = 3;
            Map<Integer, Long> sendTimes = new HashMap<>();

            while (base <= N) {
                // send up to window
                while (nextSeq < base + WINDOW && nextSeq <= N) {
                    output.writeObject(chunks.get(nextSeq - 1));
                    output.flush();
                    sendTimes.put(nextSeq, System.currentTimeMillis());
                    System.out.println(Util.getTimestamp()
                            + " FileManager: Sent chunk " + nextSeq);
                    nextSeq++;
                }

                // wait for ACK or timeout on base
                boolean gotAck = false;
                long timeoutStart = System.currentTimeMillis();
                while (System.currentTimeMillis() - timeoutStart < Constants.TIMEOUT_MILLISECONDS) {
                    if (rawIn.available() > 0) {
                        Message resp = (Message) input.readObject();
                        if (resp.getType() == MessageType.ACK && resp.getPayload().contains("Chunk ")) {
                            // parse cumulative ack number
                            String payload = resp.getPayload();
                            int ackNum = Integer.parseInt(
                                    payload.split("Chunk ")[1].trim()
                            );
                            if (ackNum >= base) {
                                base = ackNum + 1;
                                gotAck = true;
                                System.out.println(Util.getTimestamp()
                                        + " FileManager: Cumulative ACK received for chunk " + ackNum);
                                break;
                            }
                        }
                        // ignore others
                    }
                }

                if (!gotAck) {
                    // timeout → go back and retransmit [base, nextSeq)
                    System.out.println(Util.getTimestamp()
                            + " FileManager: Timeout on chunk " + base + ", retransmitting window");
                    for (int seq = base; seq < nextSeq; seq++) {
                        output.writeObject(chunks.get(seq - 1));
                        output.flush();
                        System.out.println(Util.getTimestamp()
                                + " FileManager: Retransmitted chunk " + seq);
                    }
                }
            }

            // 8) Send caption
            File capFile = new File(ownerDir, photoName + "_" + lang + ".txt");
            if (capFile.exists()) {
                String cap = new String(Files.readAllBytes(capFile.toPath()));
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "Caption: " + cap));
            } else {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server",
                        "No caption available in " + (lang.equals("en") ? "English" : "Greek")));
            }
            output.flush();

            // 9) FILE_END
            output.writeObject(new Message(MessageType.FILE_END, "Server",
                    "The transmission is completed"));
            output.flush();
            System.out.println(Util.getTimestamp()
                    + " FileManager: DOWNLOAD completed successfully for " + photoName);

            // 10) Update search & stats
            photoOwners
                    .computeIfAbsent(photoName, k -> ConcurrentHashMap.newKeySet())
                    .add(downloaderId);
            DownloadStatisticsManager.recordDownload(photoName, downloaderId);

        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }
}