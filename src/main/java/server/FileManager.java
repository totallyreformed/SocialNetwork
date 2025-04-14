package server;

import common.Message;
import common.Message.MessageType;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class FileManager {

    // Process file upload requests.
    public static void handleUpload(Message msg, String clientId, ObjectOutputStream output) {
        // Example: msg.payload might be "photoName:acropolis.jpg|caption:Beautiful view"
        String fileDetails = msg.getPayload();
        // Store the photo and caption in the server’s directory structure.
        // Update the client's profile file with a new post.
        ProfileManager.getInstance().updateProfile(clientId, clientId + " posted " + fileDetails);
        try {
            output.writeObject(new Message(MessageType.DIAGNOSTIC, "Server", "Upload successful for " + fileDetails));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Process a search request for a photo in the client's social graph.
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

    // Process file download requests implementing the handshake and stop-and-wait protocol.
    public static void handleDownload(Message msg, String clientId, ObjectOutputStream output) {
        String photoName = msg.getPayload();
        try {
            // Step 1: Initiate the handshake
            output.writeObject(new Message(MessageType.HANDSHAKE, "Server", "Initiate handshake for " + photoName));
            output.flush();

            // For illustration, simulate file segmentation into ~10 APDUs and waiting for ACKs.
            for (int i = 1; i <= 10; i++) {
                // Simulate sending each file chunk
                output.writeObject(new Message(MessageType.FILE_CHUNK, "Server", "APDU " + i + " for " + photoName));
                output.flush();
                // In a real implementation, wait for an ACK, check for timeouts (especially for chunk 3 and 6),
                // and retransmit as needed.
            }
            // Notify the client that transmission is complete.
            output.writeObject(new Message(MessageType.FILE_END, "Server", "Transmission completed for " + photoName));
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
