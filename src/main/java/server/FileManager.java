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

    // Handles file upload. Expected payload format: "photoName:<name>|caption:<text>|data:<base64Data>"
    public static void handleUpload(Message msg, String clientId, ObjectOutputStream output) {
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
            // Ensure server file directory exists.
            File dir = new File("ServerFiles");
            if (!dir.exists()) dir.mkdirs();
            File photoFile = new File(dir, photoName);

            // In a full implementation, decode base64Data into bytes.
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(base64Data.getBytes());
            }

            // Save caption in a corresponding .txt file.
            File captionFile = new File(dir, photoName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(caption.getBytes());
            }

            // Update client's profile.
            ProfileManager.getInstance().updateProfile(clientId, clientId + " posted " + photoName);

            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + photoName));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handles file search requests. This simulated implementation checks if the file exists.
    public static void handleSearch(Message msg, String clientId, ObjectOutputStream output) {
        String photoName = msg.getPayload();
        File file = new File("ServerFiles/" + photoName);
        String result;
        if (file.exists()) {
            // In a complete implementation, return the client id(s) that own the file.
            result = "Found photo " + photoName + " at clientID dummyOwner";
        } else {
            result = "Photo " + photoName + " not found.";
        }
        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handles file download using a simulated 3-way handshake and stop-and-wait protocol.
    public static void handleDownload(Message msg, String clientId, ObjectInputStream input, ObjectOutputStream output) {
        String photoName = msg.getPayload();
        File dir = new File("ServerFiles");
        File photoFile = new File(dir, photoName);

        try {
            if (!photoFile.exists()) {
                output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "File " + photoName + " not found."));
                output.flush();
                return;
            }

            // Step 1: Send handshake initiation.
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server", "Initiate handshake for " + photoName));
            output.flush();
            System.out.println("Sent handshake for " + photoName);

            // Step 2: Wait for handshake ACK.
            Message ackMsg = (Message) input.readObject();
            if (ackMsg.getType() != MessageType.ACK || !ackMsg.getPayload().contains("handshake")) {
                output.writeObject(new Message(MessageType.NACK, "Server", "Handshake failed."));
                output.flush();
                return;
            }
            System.out.println("Received handshake ACK from client.");

            // Step 3: Read file into memory.
            byte[] fileData = new byte[(int) photoFile.length()];
            try (FileInputStream fis = new FileInputStream(photoFile)) {
                fis.read(fileData);
            }
            int totalLength = fileData.length;
            // For simplicity, calculate chunk size to simulate NUM_CHUNKS.
            int chunkSize = totalLength / Constants.NUM_CHUNKS;
            if (chunkSize == 0) chunkSize = totalLength;

            // For each simulated chunk.
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : i * chunkSize;
                String chunkContent = new String(fileData, start, end - start);
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                boolean ackReceived = false;
                int attempts = 0;
                int maxAttempts = 3;

                // For chunks 3 and 6, simulate retransmission.
                if (i == 3 || i == 6) {
                    while (!ackReceived && attempts < maxAttempts) {
                        output.writeObject(chunkMsg);
                        output.flush();
                        System.out.println("Sent chunk " + i + " (attempt " + (attempts+1) + "). Waiting for ACK...");
                        // Simulate delay exceeding timeout.
                        Thread.sleep(Constants.TIMEOUT_MILLISECONDS + 500);
                        // Try to read an ACK for this chunk.
                        Message ackChunk = (Message) input.readObject();
                        if (ackChunk.getType() == MessageType.ACK &&
                                ackChunk.getPayload().contains("Chunk " + i)) {
                            ackReceived = true;
                            System.out.println("Received ACK for chunk " + i);
                        } else {
                            System.out.println("Did not receive proper ACK for chunk " + i);
                            attempts++;
                        }
                    }
                    if (!ackReceived) {
                        System.out.println("Max retransmission reached for chunk " + i + ". Aborting download.");
                        output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                        output.flush();
                        return;
                    }
                } else {
                    // For other chunks, send once and wait for immediate ACK.
                    output.writeObject(chunkMsg);
                    output.flush();
                    Message ackChunk = (Message) input.readObject();
                    if (ackChunk.getType() == MessageType.ACK &&
                            ackChunk.getPayload().contains("Chunk " + i)) {
                        System.out.println("Received ACK for chunk " + i);
                    } else {
                        System.out.println("Did not receive proper ACK for chunk " + i + ". Aborting download.");
                        output.writeObject(new Message(MessageType.NACK, "Server", "Download aborted at chunk " + i));
                        output.flush();
                        return;
                    }
                }
            }

            // Send final message.
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
            System.out.println("File download completed for " + photoName);
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
