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

    // Process file upload: payload format is "photoName:<name>|caption:<text>|data:<base64Data>"
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
            // For simulation, write photo data to file.
            File dir = new File("ServerFiles");
            if (!dir.exists()) dir.mkdirs();
            File photoFile = new File(dir, photoName);

            // In a real implementation, decode base64Data to bytes.
            // Here we simply simulate by writing the string.
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(base64Data.getBytes());
            }

            // Save caption to a .txt file with same name (or with a fixed convention)
            File captionFile = new File(dir, photoName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(captionFile)) {
                fos.write(caption.getBytes());
            }

            // Update the profile (append a new post)
            ProfileManager.getInstance().updateProfile(clientId, clientId + " posted " + photoName);

            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + photoName));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Process file search: For simplicity, return a dummy client ID.
    public static void handleSearch(Message msg, String clientId, ObjectOutputStream output) {
        String photoName = msg.getPayload();
        String result = SocialGraphManager.getInstance().searchPhoto(photoName, clientId);
        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", result));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Process file download with a simulated handshake and stop-and-wait protocol.
    // The input stream is used to wait for handshake ACK messages.
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

            // Step 2: Wait for client's ACK for handshake.
            Message ackMsg = (Message) input.readObject();
            if (ackMsg.getType() != MessageType.ACK || !ackMsg.getPayload().contains("handshake")) {
                output.writeObject(new Message(MessageType.NACK, "Server", "Handshake failed."));
                output.flush();
                return;
            }
            System.out.println("Received handshake ACK from client.");

            // Step 3: Read file bytes (simulate segmentation into NUM_CHUNKS chunks).
            byte[] fileData = new byte[(int) photoFile.length()];
            try (FileInputStream fis = new FileInputStream(photoFile)) {
                fis.read(fileData);
            }
            int totalLength = fileData.length;
            int chunkSize = totalLength / Constants.NUM_CHUNKS;
            if (chunkSize == 0) chunkSize = totalLength; // small file

            // For simulation, we send NUM_CHUNKS chunks.
            for (int i = 1; i <= Constants.NUM_CHUNKS; i++) {
                // Determine start and end index (simulate last chunk may be longer)
                int start = (i - 1) * chunkSize;
                int end = (i == Constants.NUM_CHUNKS) ? totalLength : i * chunkSize;
                String chunkContent = new String(fileData, start, end - start);

                // Create a file chunk message.
                Message chunkMsg = new Message(MessageType.FILE_CHUNK, "Server", "Chunk " + i + ": " + chunkContent);

                // For chunk 3 and 6, simulate delay and retransmission.
                if (i == 3 || i == 6) {
                    output.writeObject(chunkMsg);
                    output.flush();
                    System.out.println("Sent chunk " + i + " (deliberate delay simulated). Waiting for ACK...");

                    // Simulate waiting period (beyond timeout).
                    Thread.sleep(Constants.TIMEOUT_MILLISECONDS + 500);
                    // In a real scenario, no ACK would be received, so server retransmits.
                    System.out.println("Timeout reached for chunk " + i + ". Retransmitting chunk " + i + ".");
                    output.writeObject(chunkMsg);
                    output.flush();
                } else {
                    output.writeObject(chunkMsg);
                    output.flush();
                }

                // Simulate stop-and-wait: wait for an ACK for this chunk.
                Message ackChunk = (Message) input.readObject();
                if (ackChunk.getType() != MessageType.ACK) {
                    System.out.println("Did not receive proper ACK for chunk " + i);
                } else {
                    System.out.println("Received ACK for chunk " + i);
                }
            }

            // Send final message indicating completion.
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
            System.out.println("File download completed for " + photoName);
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
