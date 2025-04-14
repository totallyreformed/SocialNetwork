package common;

public class Constants {
    public static final int SERVER_PORT = 5000;
    public static final int MAX_CLIENT_THREADS = 8;
    public static final int TIMEOUT_MILLISECONDS = 3000; // For file lock and ACK waiting
    public static final int CHUNK_SIZE = 1024; // Size in bytes for file segmentation (if needed)
    public static final int NUM_CHUNKS = 10;   // Number of chunks to split a file into (simulation)
}
