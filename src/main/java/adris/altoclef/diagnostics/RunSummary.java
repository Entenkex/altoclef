package adris.altoclef.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened during one run (from user task start to finish / death / {@code @summary}):
 * time per chain, number of switches, the most frequent switch pairs, how often each chain took over, deaths with
 * cause, dumps written, and movement: distance walked, net / max distance from the start, time in water.
 * Time is counted in ticks (20 per second) while the bot runs.
 */
public final class RunSummary {

    private final Map<String, Long> ticksPerChain = new LinkedHashMap<>();
    private final Map<String, Integer> switchPairs = new LinkedHashMap<>();
    private final Map<String, Integer> chainEntries = new LinkedHashMap<>();
    private final Map<String, Integer> itemsLost = new LinkedHashMap<>();
    private final List<String> deaths = new ArrayList<>();
    private String label = "";
    private long totalTicks;
    private int switches;
    private int dumps;
    private int suppressedLines;
    private boolean hasPosition;
    private double startX, startZ, lastX, lastZ;
    private double distance;
    private double maxDistance;
    private long waterTicks;

    public synchronized void start(String label) {
        this.label = label == null ? "" : label;
        ticksPerChain.clear();
        switchPairs.clear();
        chainEntries.clear();
        itemsLost.clear();
        deaths.clear();
        hasPosition = false;
        distance = 0;
        maxDistance = 0;
        waterTicks = 0;
        totalTicks = 0;
        switches = 0;
        dumps = 0;
        suppressedLines = 0;
    }

    /**
     * One tick in which {@code chain} ran ({@code null} = nothing ran).
     */
    public synchronized void tick(String chain) {
        totalTicks++;
        ticksPerChain.merge(chain == null ? "(none)" : chain, 1L, Long::sum);
    }

    /**
     * Where the player is this tick (horizontal), and whether it touches water.
     * Jumps over 10 blocks in one tick (teleport, respawn, portal) are not counted as walked distance.
     */
    public synchronized void position(double x, double z, boolean inWater) {
        if (inWater) waterTicks++;
        if (!hasPosition) {
            hasPosition = true;
            startX = lastX = x;
            startZ = lastZ = z;
            return;
        }
        double step = Math.hypot(x - lastX, z - lastZ);
        if (step <= 10) distance += step;
        lastX = x;
        lastZ = z;
        maxDistance = Math.max(maxDistance, netDistance());
    }

    private double netDistance() {
        return hasPosition ? Math.hypot(lastX - startX, lastZ - startZ) : 0;
    }

    public synchronized void switched(String from, String to) {
        if (to != null) chainEntries.merge(to, 1, Integer::sum);
        switches++;
        if (from != null && to != null) {
            switchPairs.merge(ThrashDetector.pairKey(from, to), 1, Integer::sum);
        }
    }

    public synchronized void death(String cause) {
        deaths.add(cause == null ? "unknown" : cause);
    }

    /**
     * A tool / weapon / armor piece disappeared ({@link InventoryWatch}).
     */
    public synchronized void itemLost(String item, String reason) {
        String name = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        itemsLost.merge(name + " (" + reason + ")", 1, Integer::sum);
    }

    public synchronized void dumped() {
        dumps++;
    }

    public synchronized void suppressed(int lines) {
        suppressedLines += lines;
    }

    public synchronized long totalTicks() {
        return totalTicks;
    }

    public synchronized int switches() {
        return switches;
    }

    /**
     * Full summary for the log.
     */
    public synchronized Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("run", label);
        map.put("seconds", totalTicks / 20.0);
        Map<String, Object> perChain = new LinkedHashMap<>();
        sortedChains().forEach(e -> perChain.put(e.getKey(), e.getValue() / 20.0));
        map.put("secondsPerChain", perChain);
        map.put("switches", switches);
        Map<String, Integer> pairs = new LinkedHashMap<>();
        switchPairs.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> pairs.put(e.getKey(), e.getValue()));
        map.put("switchPairs", pairs);
        map.put("chainEntries", new LinkedHashMap<>(chainEntries));
        map.put("distanceWalked", round(distance));
        map.put("netDistance", round(netDistance()));
        map.put("maxDistance", round(maxDistance));
        map.put("waterSeconds", waterTicks / 20.0);
        map.put("itemsLost", new LinkedHashMap<>(itemsLost));
        map.put("deaths", new ArrayList<>(deaths));
        map.put("dumps", dumps);
        map.put("suppressedLogLines", suppressedLines);
        return map;
    }

    /**
     * Short version for chat (a few lines).
     */
    public synchronized List<String> chatLines() {
        List<String> lines = new ArrayList<>();
        StringBuilder chains = new StringBuilder();
        for (Map.Entry<String, Long> e : sortedChains()) {
            if (chains.length() > 0) chains.append(", ");
            chains.append(e.getKey()).append(' ').append(percent(e.getValue()));
        }
        lines.add("Time " + formatDuration(totalTicks / 20) + ": " + (chains.length() == 0 ? "-" : chains));
        String topPair = switchPairs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> ", most: " + e.getKey() + " (" + e.getValue() + ")")
                .orElse("");
        lines.add("Switches: " + switches + topPair);
        lines.add("Moved " + Math.round(distance) + " blocks (net " + Math.round(netDistance()) + ", max "
                + Math.round(maxDistance) + "), in water " + RunSummary.formatDuration(waterTicks / 20)
                + ", unstuck " + chainEntries.getOrDefault("Unstuck Chain", 0) + "x");
        if (!itemsLost.isEmpty()) {
            List<String> lost = new ArrayList<>();
            itemsLost.forEach((k, v) -> lost.add(v > 1 ? v + "x " + k : k));
            lines.add("Lost: " + String.join(", ", lost));
        }
        lines.add("Deaths: " + deaths.size() + (deaths.isEmpty() ? "" : " (last: " + deaths.get(deaths.size() - 1) + ")")
                + (dumps > 0 ? ", dumps: " + dumps : ""));
        return lines;
    }

    private List<Map.Entry<String, Long>> sortedChains() {
        return ticksPerChain.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .toList();
    }

    private String percent(long ticks) {
        if (totalTicks == 0) return "0%";
        return Math.round(100.0 * ticks / totalTicks) + "%";
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static String formatDuration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
