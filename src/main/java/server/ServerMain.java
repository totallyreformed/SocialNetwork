// File: server/ServerMain.java
package server;

import common.Constants;
import common.Util;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ServerMain {
    private static DirectoryWatcher directoryWatcher;
    private ExecutorService threadPool;

    public ServerMain() {
        threadPool = Executors.newFixedThreadPool(Constants.MAX_CLIENT_THREADS);
    }

    public void startServer() throws IOException {
        // Initialize and start the directory watcher
        directoryWatcher = new DirectoryWatcher("ServerFiles");
        Thread watcherThread = new Thread(directoryWatcher, "DirectoryWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
        System.out.println(Util.getTimestamp()
                + " ServerMain: DirectoryWatcher started for 'ServerFiles' directory.");

        try (ServerSocket serverSocket = new ServerSocket(Constants.SERVER_PORT)) {
            System.out.println(Util.getTimestamp()
                    + " ServerMain: Server started on port " + Constants.SERVER_PORT);

            // Load the initial social graph from file.
            SocialGraphManager.getInstance().loadSocialGraph("src/SocialGraph.txt");

            // Continuously accept incoming client connections.
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(Util.getTimestamp()
                        + " ServerMain: New client connected from " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            System.out.println(Util.getTimestamp()
                    + " ServerMain: Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Allows other classes (e.g. ClientHandler) to trigger an initial scan. */
    public static DirectoryWatcher getDirectoryWatcher() {
        return directoryWatcher;
    }

    public static void main(String[] args) throws IOException {
        ServerMain server = new ServerMain();
        server.startServer();
    }
}
