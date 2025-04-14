package server;

import java.net.ServerSocket;
import java.net.Socket;
import common.Constants;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ServerMain {
    private ExecutorService threadPool;

    public ServerMain() {
        threadPool = Executors.newFixedThreadPool(Constants.MAX_CLIENT_THREADS);
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(Constants.SERVER_PORT)) {
            System.out.println("Server started on port " + Constants.SERVER_PORT);

            // Load the social graph from file
            SocialGraphManager.getInstance().loadSocialGraph("SocialGraph.txt");

            // Accept clients continuously
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from " + clientSocket.getInetAddress());

                // Create a new ClientHandler and assign it to the thread pool
                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ServerMain server = new ServerMain();
        server.startServer();
    }
}
