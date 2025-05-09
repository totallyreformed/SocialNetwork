package server;

import common.Constants;
import common.Util;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Entry point for the server application that initializes the thread pool,
 * starts the directory watcher, loads the social graph, and accepts client connections.
 */
public class ServerMain {

    /**
     * ExecutorService to manage client handler threads.
     */
    private ExecutorService threadPool;

    /**
     * Constructs a ServerMain, initializing a fixed-size thread pool
     * according to the configured maximum client threads.
     */
    public ServerMain() {
        threadPool = Executors.newFixedThreadPool(Constants.MAX_CLIENT_THREADS);
    }

    /**
     * Starts the server by launching the DirectoryWatcher thread, binding to the server port,
     * loading the initial social graph, and continuously accepting incoming client connections.
     * Each new connection is handled by a ClientHandler submitted to the thread pool.
     *
     * @throws IOException if an I/O error occurs when opening the server socket
     */
    public void startServer() throws IOException {
        // Start the directory watcher in its own thread.
        Thread watcherThread = new Thread(new DirectoryWatcher("ServerFiles"));
        watcherThread.start();
        System.out.println(Util.getTimestamp() + " ServerMain: DirectoryWatcher started for 'ServerFiles' directory.");

        try (ServerSocket serverSocket = new ServerSocket(Constants.SERVER_PORT)) {
            System.out.println(Util.getTimestamp() + " ServerMain: Server started on port " + Constants.SERVER_PORT);

            // Load the initial social graph from file.
            SocialGraphManager.getInstance().loadSocialGraph("src/SocialGraph.txt");

            // Continuously accept incoming client connections.
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(Util.getTimestamp() + " ServerMain: New client connected from " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            System.out.println(Util.getTimestamp() + " ServerMain: Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main method to launch the server application.
     *
     * @param args command-line arguments (not used)
     * @throws IOException if the server socket cannot be opened
     */
    public static void main(String[] args) throws IOException {
        ServerMain server = new ServerMain();
        server.startServer();
    }
}
