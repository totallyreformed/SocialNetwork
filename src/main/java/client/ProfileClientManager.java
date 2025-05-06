package client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import common.Constants;

public class ProfileClientManager {
    private String clientId;
    private String profileFileName;
    private String othersProfileFileName;

    public ProfileClientManager(String clientId) {
        this.clientId = clientId;
        // Ensure per-client directory exists under ClientFiles/
        String clientDir = "ClientFiles/" + Constants.GROUP_ID + "client" + clientId;
        File dir = new File(clientDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // Place the two text files inside that directory
        this.profileFileName =
                clientDir + "/Profile_" + Constants.GROUP_ID + "client" + clientId + ".txt";
        this.othersProfileFileName =
                clientDir + "/Others_" + Constants.GROUP_ID + "client" + clientId + ".txt";
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

    // Append a new repost entry to the local Others file.
    public void appendRepost(String repost) {
        try (FileWriter fw = new FileWriter(new File(othersProfileFileName), true)) {
            fw.write(repost + "\n");
            System.out.println("ProfileClientManager: Local Others file updated with: " + repost);
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error updating local Others file.");
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
