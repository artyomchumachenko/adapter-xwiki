package ru.cbgr.adapter.xwiki.chunker;

import java.util.ArrayList;
import java.util.List;

final class HardLimiter {

    private HardLimiter() {}

    static List<String> enforce(List<String> src, int maxChars, int overlapChars) {
        if (src == null || src.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();

        for (String s : src) {
            if (s == null) continue;
            String t = s.strip();
            if (t.isEmpty()) continue;

            if (t.length() <= maxChars) {
                out.add(t);
            } else {
                out.addAll(splitByCharsWithOverlap(t, maxChars, overlapChars));
            }
        }
        return out;
    }

    static List<String> splitByCharsWithOverlap(String s, int max, int overlap) {
        int safeMax = Math.max(1, max);
        int safeOverlap = Math.max(0, Math.min(overlap, safeMax - 1));

        List<String> parts = new ArrayList<>();
        int start = 0;

        while (start < s.length()) {
            int end = Math.min(start + safeMax, s.length());
            String part = s.substring(start, end).strip();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            if (end == s.length()) break;
            start = Math.max(0, end - safeOverlap);
        }
        return parts;
    }
}
