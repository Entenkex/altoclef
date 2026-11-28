package adris.altoclef.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened during one run (from user task start to finish / death / {@code @summary}):
 * time per chain, number of switches, the most frequent switch pairs, deaths with cause, dumps written.
 * Time is counted in ticks (20 per second) of the chain that was running.
 */
public final class RunSummary {

    private final Map<String, Long> ticksPerChain = new LinkedHashMap<>();
    private final Map<String, Integer> switchPairs = new LinkedHashMap<>();
    private final List<String> deaths = new ArrayList<>();
    private String label = "";
    private long totalTicks;
    private int switches;
    private int dumps;
    private int suppressedLines;

    public synchronized void start(String label) {
        this.label = label == null ? "" : label;
        ticksPerChain.clear();
        switchPairs.clear();
        deaths.clear();
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

    public synchronized void switched(String from, String to) {
        switches++;
        if (from != null && to != null) {
            switchPairs.merge(ThrashDetector.pairKey(from, to), 1, Integer::sum);
        }
    }

    public synchronized void death(String cause) {
        deaths.add(cause == null ? "unknown" : cause);
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

    static String formatDuration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
