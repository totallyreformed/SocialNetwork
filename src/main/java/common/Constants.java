// common/Constants.java
package common;

public class Constants {
    public static final int SERVER_PORT = 5000;
    public static final int MAX_CLIENT_THREADS = 8;
    public static final int TIMEOUT_MILLISECONDS = 3000; // Timeout for file lock and ACK waiting
    public static final int CHUNK_SIZE = 1024; // Size in bytes for file segmentation (if needed)
    public static final int NUM_CHUNKS = 10;   // Simulated number of chunks per file

    // New constant for repost (Others page) file naming convention.
    public static final String OTHERS_PREFIX = "Others_";

    // --------------------------------------------------------------------
    // Replace "<YOUR_GROUP_ID>" with your actual group number.
    public static final String GROUP_ID = "40";
}
