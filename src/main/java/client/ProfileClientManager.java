package client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class ProfileClientManager {
    private String clientId;
    private String profileFileName;

    public ProfileClientManager(String clientId) {
        this.clientId = clientId;
        this.profileFileName = "Profile_Client_" + clientId + ".txt";
    }

    // Appends a new post to the local profile file.
    public void appendPost(String post) {
        try (FileWriter fw = new FileWriter(new File(profileFileName), true)) {
            fw.write(post + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Reads the current content of the local profile.
    public String readProfile() {
        try {
            return new String(Files.readAllBytes(new File(profileFileName).toPath()));
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}
