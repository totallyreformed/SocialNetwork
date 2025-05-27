package server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tracks which clients have downloaded which photos,
 * and prints a summary report on shutdown.
 */
public class DownloadStatisticsManager {
    private static final ConcurrentMap<String, Set<String>> downloadersMap =
            new ConcurrentHashMap<>();

    /**
     * Record that the given downloaderId has downloaded the given photoName.
     */
    public static void recordDownload(String photoName, String downloaderId) {
        downloadersMap
                .computeIfAbsent(photoName, k -> ConcurrentHashMap.newKeySet())
                .add(downloaderId);
    }

    /**
     * Prints a report of download counts per photo,
     * sorted by descending number of distinct downloaders.
     */
    public static void printReport() {
        System.out.println("=== Download Statistics Report ===");
        if (downloadersMap.isEmpty()) {
            System.out.println("No downloads were recorded.");
            return;
        }
        List<String> sortedPhotos = downloadersMap.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        for (String photo : sortedPhotos) {
            Set<String> dl = downloadersMap.get(photo);
            System.out.printf(
                    "Photo '%s' was downloaded %d time%s by clients: %s%n",
                    photo,
                    dl.size(),
                    dl.size() == 1 ? "" : "s",
                    String.join(", ", dl)
            );
        }
    }
}