package server;

import common.Constants;
import common.Util;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ServerMain {
    private ExecutorService threadPool;

    public ServerMain() {
        threadPool = Executors.newFixedThreadPool(Constants.MAX_CLIENT_THREADS);
    }

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

    public static void main(String[] args) throws IOException {
        ServerMain server = new ServerMain();
        server.startServer();
    }
}
