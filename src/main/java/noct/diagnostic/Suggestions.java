package noct.diagnostic;

import java.util.Collection;
import java.util.Optional;

/** Finds "did you mean ...?" candidates for misspelled names. */
public final class Suggestions {

    private Suggestions() {}

    /**
     * Returns the candidate closest to {@code name} by edit distance, if it is
     * close enough to plausibly be a typo.
     */
    public static Optional<String> closest(String name, Collection<String> candidates) {
        int maxDistance = name.length() <= 3 ? 1 : 2;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            int d = distance(name, candidate);
            if (d <= maxDistance && d < bestDistance) {
                best = candidate;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Levenshtein distance: the number of single-character edits between two strings. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
