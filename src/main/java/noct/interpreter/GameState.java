package noct.interpreter;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything that changes while a game is played. Noct has a single global
 * environment, so this one object is shared by every handler in every room.
 */
public final class GameState {

    final Map<String, Integer> vars = new LinkedHashMap<>();
    final Map<String, Boolean> flags = new LinkedHashMap<>();
    final Set<String> inventory = new LinkedHashSet<>();

    /** Lock state of every door, keyed by room and then door name. */
    private final Map<String, Map<String, Boolean>> locked = new HashMap<>();

    String currentRoom;

    boolean isLocked(String room, String door) {
        return locked.getOrDefault(room, Map.of()).getOrDefault(door, false);
    }

    void setLocked(String room, String door, boolean value) {
        locked.computeIfAbsent(room, r -> new HashMap<>()).put(door, value);
    }

    // Read-only views, used by tests and tools.

    public Map<String, Integer> vars() { return Collections.unmodifiableMap(vars); }

    public Map<String, Boolean> flags() { return Collections.unmodifiableMap(flags); }

    public Set<String> inventory() { return Collections.unmodifiableSet(inventory); }

    public String currentRoom() { return currentRoom; }

    public boolean doorLocked(String room, String door) { return isLocked(room, door); }
}
