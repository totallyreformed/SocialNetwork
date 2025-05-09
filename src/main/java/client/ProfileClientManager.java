package client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import common.Constants;

/**
 * Manages the local storage of user profile and repost entries by
 * writing posts and reposts to per-client text files.
 */
public class ProfileClientManager {

    private String clientId;
    private String profileFileName;
    private String othersProfileFileName;

    /**
     * Constructs a ProfileClientManager for the specified client,
     * ensuring the client directory exists and initializing file paths.
     *
     * @param clientId the identifier of the client
     */
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

    /**
     * Appends a new post entry to the client's profile file.
     *
     * @param post the post content to append
     */
    public void appendPost(String post) {
        try (FileWriter fw = new FileWriter(new File(profileFileName), true)) {
            fw.write(post + "\n");
            System.out.println("ProfileClientManager: Local profile updated with: " + post);
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error updating local profile.");
            e.printStackTrace();
        }
    }

    /**
     * Appends a new repost entry to the client's Others file.
     *
     * @param repost the repost information to append
     */
    public void appendRepost(String repost) {
        try (FileWriter fw = new FileWriter(new File(othersProfileFileName), true)) {
            fw.write(repost + "\n");
            System.out.println("ProfileClientManager: Local Others file updated with: " + repost);
        } catch (IOException e) {
            System.out.println("ProfileClientManager: Error updating local Others file.");
            e.printStackTrace();
        }
    }
}
