package com.sairam.pr_sense.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks a unified diff and returns, for every new-side file path,
 * the set of line numbers GitHub will accept a RIGHT-side comment on.
 */
public final class DiffParser {

    private static final Pattern HUNK =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    private DiffParser() {}

    public static Map<String, Set<Integer>> commentableLines(String diff) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        if (diff == null || diff.isBlank()) return result;

        String path = null;
        int newLine = 0;
        boolean inHunk = false;

        for (String raw : diff.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;

            // New file section resets everything
            if (line.startsWith("diff --git ")) {
                path = null;
                inHunk = false;
                continue;
            }

            if (!inHunk) {
                if (line.startsWith("+++ ")) {
                    String p = line.substring(4).trim();
                    int tab = p.indexOf('\t');           // some tools append a timestamp
                    if (tab >= 0) p = p.substring(0, tab);
                    path = ("/dev/null".equals(p) || p.isEmpty()) ? null : stripPrefix(p);
                    if (path != null) result.computeIfAbsent(path, k -> new LinkedHashSet<>());
                    continue;
                }
                Matcher m = HUNK.matcher(line);
                if (m.find()) {
                    newLine = Integer.parseInt(m.group(1));
                    inHunk = true;
                }
                continue;
            }

            // ---- inside a hunk ----
            Matcher m = HUNK.matcher(line);
            if (m.find()) {                              // next hunk, same file
                newLine = Integer.parseInt(m.group(1));
                continue;
            }
            if (line.startsWith("\\")) continue;         // "\ No newline at end of file"
            if (line.isEmpty()) continue;

            char c = line.charAt(0);
            switch (c) {
                case '+' -> {                            // added line -> new file
                    if (path != null) result.get(path).add(newLine);
                    newLine++;
                }
                case ' ' -> {                            // context line -> also commentable
                    if (path != null) result.get(path).add(newLine);
                    newLine++;
                }
                case '-' -> { /* removed: new-side counter does not advance */ }
                default -> { /* meta line, ignore */ }
            }
        }
        return result;
    }

    private static String stripPrefix(String p) {
        return (p.startsWith("b/") || p.startsWith("a/")) ? p.substring(2) : p;
    }
}