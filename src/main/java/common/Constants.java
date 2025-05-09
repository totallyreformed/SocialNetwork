package common;

/**
 * Defines application-wide constants for server configuration,
 * file transfer settings, and naming conventions.
 */
public class Constants {

    /**
     * Port number on which the server listens for client connections.
     */
    public static final int SERVER_PORT = 5000;

    /**
     * Maximum number of concurrent client handler threads allowed by the server.
     */
    public static final int MAX_CLIENT_THREADS = 8;

    /**
     * Timeout duration in milliseconds for file lock operations and ACK waiting.
     */
    public static final int TIMEOUT_MILLISECONDS = 3000;

    /**
     * Number of bytes per chunk when segmenting files for transfer (optional).
     */
    public static final int CHUNK_SIZE = 1024;

    /**
     * Simulated number of chunks to divide a file into during transfer.
     */
    public static final int NUM_CHUNKS = 10;

    /**
     * Prefix used for naming the "Others" file that tracks repost entries.
     */
    public static final String OTHERS_PREFIX = "Others_";

    /**
     * Identifier for this project group; used in directory and file naming.
     */
    public static final String GROUP_ID = "34";
}
