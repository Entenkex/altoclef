package adris.altoclef.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects settings warnings that happen while no world is open (e.g. at game start), so they can be
 * shown once in chat when the player joins a world. Until then they are only in the log.
 * <p>
 * Each load of the profile starts over, so fixed problems don't show up later. Does not depend on Minecraft.
 */
public final class StartupWarnings {

    private final Set<String> pending = new LinkedHashSet<>();

    /**
     * Forget warnings of the previous load.
     */
    public synchronized void clear() {
        pending.clear();
    }

    public synchronized void add(String warning) {
        pending.add(warning);
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Takes all pending warnings (they are gone afterwards).
     */
    public synchronized List<String> drain() {
        List<String> result = new ArrayList<>(pending);
        pending.clear();
        return result;
    }

    /**
     * Header and lines for one chat message, or empty if there is nothing to show.
     */
    public record Message(String header, List<String> lines) {
    }

    public Optional<Message> drainAsMessage(String commandPrefix) {
        List<String> warnings = drain();
        if (warnings.isEmpty()) return Optional.empty();
        List<String> lines = new ArrayList<>();
        for (String warning : warnings) {
            String text = warning.startsWith("Settings: ") ? warning.substring("Settings: ".length()) : warning;
            lines.add("§c  - " + text);
        }
        lines.add("§7Fix the profile file, then " + commandPrefix + "reload_settings");
        String header = "§cSettings: " + warnings.size() + (warnings.size() == 1 ? " problem" : " problems")
                + " while loading at startup:";
        return Optional.of(new Message(header, lines));
    }
}
