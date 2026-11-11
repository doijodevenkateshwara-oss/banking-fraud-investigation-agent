package com.bfs.fraudcase.knowledge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates policy front matter and splits documents by numbered headings so no rule is split across unrelated
 * chunks. Oversized sections are split on paragraph boundaries under a word cap.
 *
 * @author Venkateshwara Doijode
 */
public final class PolicyDocumentParser {

    static final Set<String> REQUIRED = Set.of("documentId", "title", "version", "effectiveDate", "owner",
            "classification", "status", "chunkPrefix");
    private static final Pattern HEADING = Pattern.compile("^##\\s+(\\d+(?:\\.\\d+)*)\\s+(.+)$");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "WITHDRAWN", "SUPERSEDED", "DRAFT");
    private final int maxWords;

    public PolicyDocumentParser(int maxWords) {
        this.maxWords = maxWords;
    }

    public List<PolicyChunk> parse(String sourceName, String content) {
        String normalised = content.replace("\r\n", "\n");
        if (!normalised.startsWith("---\n")) {
            throw new IllegalArgumentException(sourceName + ": missing front matter");
        }
        int end = normalised.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException(sourceName + ": unterminated front matter");
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : normalised.substring(4, end).split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                meta.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }
        for (String key : REQUIRED) {
            if (meta.getOrDefault(key, "").isBlank()) {
                throw new IllegalArgumentException(sourceName + ": missing metadata '" + key + "'");
            }
        }
        if (!STATUSES.contains(meta.get("status"))) {
            throw new IllegalArgumentException(sourceName + ": unknown status " + meta.get("status"));
        }
        if (!"SYNTHETIC_TRAINING".equals(meta.get("classification"))) {
            throw new IllegalArgumentException(sourceName + ": only SYNTHETIC_TRAINING policies may be ingested");
        }
        LocalDate effective = LocalDate.parse(meta.get("effectiveDate"));
        boolean active = "ACTIVE".equals(meta.get("status"));

        List<PolicyChunk> chunks = new ArrayList<>();
        String section = null;
        String sectionTitle = null;
        StringBuilder body = new StringBuilder();
        for (String line : normalised.substring(end + 5).split("\n")) {
            Matcher m = HEADING.matcher(line.trim());
            if (m.matches()) {
                flush(chunks, meta, effective, active, section, sectionTitle, body);
                section = m.group(1);
                sectionTitle = m.group(2).trim();
                body.setLength(0);
            } else if (section != null) {
                body.append(line).append('\n');
            }
        }
        flush(chunks, meta, effective, active, section, sectionTitle, body);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(sourceName + ": no numbered sections found");
        }
        return chunks;
    }

    private void flush(List<PolicyChunk> out, Map<String, String> meta, LocalDate effective, boolean active,
                       String section, String sectionTitle, StringBuilder body) {
        if (section == null || body.toString().isBlank()) {
            return;
        }
        List<String> parts = splitByWords(body.toString().trim());
        String base = meta.get("chunkPrefix") + "-" + sectionCode(section) + (active ? "" : "-v" + meta.get("version"));
        for (int i = 0; i < parts.size(); i++) {
            out.add(new PolicyChunk(meta.get("documentId"), meta.get("title"), meta.get("version"), effective,
                    meta.get("owner"), meta.get("classification"), meta.get("status"), section, sectionTitle,
                    i == 0 ? base : base + "-" + (i + 1), parts.get(i)));
        }
    }

    private List<String> splitByWords(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int words = 0;
        for (String para : text.split("\n\\s*\n")) {
            int w = para.trim().split("\\s+").length;
            if (words > 0 && words + w > maxWords) {
                parts.add(current.toString().trim());
                current.setLength(0);
                words = 0;
            }
            current.append(para.trim().replaceAll("\\s*\n\\s*", " ")).append("\n\n");
            words += w;
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    /** "3.2" becomes "032", "10.2" becomes "102". */
    static String sectionCode(String section) {
        String[] p = section.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            sb.append(s);
        }
        String digits = sb.toString();
        return digits.length() >= 3 ? digits : "0".repeat(3 - digits.length()) + digits;
    }
}
