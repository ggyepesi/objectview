package objectview;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * FORCING RULE — objectview must never infer a field/class ROLE from its literal NAME
 * ("qid", "name", "id", "source", "record", …). Special roles (identity, title, provenance,
 * link, entity) belong on declared flags/annotations read through a generic public mechanism,
 * not string-matched. Name inference is fragile and has bitten us repeatedly (Oscars fields,
 * the provenance {@code source} whose {@code @Link} field was named {@code qid}).
 *
 * <p>This scans objectview's own source for that anti-pattern and compares it to a frozen
 * allowlist ({@code name-based-role-allowlist.txt}). A NEW occurrence fails the build — you
 * must add a declared role instead. Each occurrence removed during the migration also fails
 * until you delete its line from the allowlist, so the list only ever shrinks to zero.
 */
class NameBasedRoleGuardTest {

    // A role-name string literal used in a role-inference context on one line of code.
    private static final Pattern ANTIPATTERN = Pattern.compile(
            "\"(?:qid|name|id|identifier|source|record)\"\\s*\\.equals"
            + "|\\.equals\\(\\s*\"(?:qid|name|id|identifier|source|record)\""
            + "|rawDeclaredField\\([^)]*\"(?:qid|name|id|identifier|source|record)\""
            + "|(?:containsKey|hasRootPath|new FieldPath)\\([^)]*"
            + "\"(?:qid|name|id|identifier|source|record)\"");

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    @Test
    void objectviewNeverInfersARoleFromAFieldName() throws Exception {
        Set<String> current = scan();
        Set<String> allowed = loadAllowlist();

        Set<String> added = new TreeSet<>(current);
        added.removeAll(allowed);
        Set<String> resolved = new TreeSet<>(allowed);
        resolved.removeAll(current);

        StringBuilder msg = new StringBuilder();
        if (!added.isEmpty()) {
            msg.append("\nNEW name-based special-casing (forbidden — declare the role via an "
                    + "annotation/flag/contract, do NOT match a field name):\n");
            added.forEach(v -> msg.append("  + ").append(v).append('\n'));
        }
        if (!resolved.isEmpty()) {
            msg.append("\nAllowlisted occurrences that are gone (progress!) — delete these "
                    + "lines from name-based-role-allowlist.txt:\n");
            resolved.forEach(v -> msg.append("  - ").append(v).append('\n'));
        }
        assertTrue(added.isEmpty() && resolved.isEmpty(), msg.toString());
    }

    private static Set<String> scan() throws Exception {
        Path base = SOURCE_ROOT.resolve("objectview");
        Set<String> hits = new TreeSet<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = SOURCE_ROOT.relativize(f).toString().replace('\\', '/');
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String code = line.split("//", 2)[0];
                    String trimmed = code.strip();
                    if (trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
                    if (ANTIPATTERN.matcher(code).find()) {
                        hits.add(rel + "::" + trimmed);
                    }
                }
            }
        }
        return hits;
    }

    private static Set<String> loadAllowlist() throws Exception {
        Set<String> allow = new TreeSet<>();
        try (InputStream in = NameBasedRoleGuardTest.class
                .getResourceAsStream("/name-based-role-allowlist.txt")) {
            if (in == null) fail("name-based-role-allowlist.txt is missing from test resources");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = r.lines().map(String::strip)
                    .filter(s -> !s.isEmpty()).toList();
            allow.addAll(lines);
        }
        return allow;
    }
}
