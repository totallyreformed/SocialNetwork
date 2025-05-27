package server;

import common.Constants;
import common.Util;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Scanner;

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

        // Register shutdown hook as a fallback (in case someone kills the JVM normally)
        Runtime.getRuntime().addShutdownHook(new Thread(DownloadStatisticsManager::printReport));

        // Start a console‐command listener to allow "shutdown" or "exit"
        Thread consoleThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Type 'shutdown' or 'exit' to stop the server and print statistics.");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim().toLowerCase();
                if (line.equals("shutdown") || line.equals("exit")) {
                    System.out.println("ServerMain: Shutdown command received.");
                    DownloadStatisticsManager.printReport();
                    System.exit(0);
                }
            }
        }, "ConsoleListener");
        consoleThread.setDaemon(true);
        consoleThread.start();

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
