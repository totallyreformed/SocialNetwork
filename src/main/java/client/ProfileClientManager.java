package client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class ProfileClientManager {
    private String clientId;
    private String profileFileName;
    private String othersProfileFileName; // For reposts.

    public ProfileClientManager(String clientId) {
        this.clientId = clientId;
        this.profileFileName = "Profile_Client_" + clientId + ".txt";
        this.othersProfileFileName = "Others_Profile_" + clientId + ".txt";
    }

    // Append a new post to the local profile.
    public void appendPost(String post) {
        try (FileWriter fw = new FileWriter(new File(profileFileName), true)) {
            fw.write(post + "\n");
            System.out.println("ProfileClientManager: Local profile updated with: " + post);
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error updating local profile.");
            e.printStackTrace();
        }
    }

    // Append a repost message to the local Others profile.
    public void appendRepost(String post) {
        try (FileWriter fw = new FileWriter(new File(othersProfileFileName), true)) {
            fw.write("Repost: " + post + "\n");
            System.out.println("ProfileClientManager: Others profile updated with repost: " + post);
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error updating Others profile.");
            e.printStackTrace();
        }
    }

    // Read and return the content of the local profile.
    public String readProfile() {
        try {
            String content = new String(Files.readAllBytes(new File(profileFileName).toPath()));
            System.out.println("ProfileClientManager: Read local profile for " + clientId);
            return content;
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error reading local profile.");
            e.printStackTrace();
            return "";
        }
    }
}
