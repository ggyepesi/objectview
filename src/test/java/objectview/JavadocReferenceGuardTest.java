package objectview;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code {@link}} and {@code {@see}} in objectview's sources names something that exists.
 *
 * <p>javac treats a doc comment as text, so the build stays green while a link names a type
 * that was moved, renamed or never belonged here — {@code FieldKind} once linked the app's
 * {@code DomainField}, a concept objectview must not know. IntelliJ's editor resolves the
 * links and shows such files as broken. This runs javac's own reference check (doclint
 * {@code reference}) over the source tree, analysis only, and fails with each unresolved
 * link.</p>
 */
class JavadocReferenceGuardTest {

    @Test void everyJavadocReferenceInObjectviewResolves() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the tests need a JDK, not a JRE");
        List<Path> sources;
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(sources.size() > 100, "the objectview sources were found: " + sources.size());

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (com.sun.source.util.JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-Xdoclint:reference", "-Xmaxerrs", "10000",
                            "-classpath", System.getProperty("java.class.path")),
                    null, files.getJavaFileObjectsFromPaths(sources));
            task.analyze();
        }

        List<String> unresolved = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> Path.of("src/main/java").toAbsolutePath()
                        .relativize(Path.of(diagnostic.getSource().toUri()))
                        + ":" + diagnostic.getLineNumber() + " "
                        + diagnostic.getMessage(Locale.ROOT))
                .toList();
        assertEquals(List.of(), unresolved,
                "Qualify the type, point the link at the current member, or make it plain "
                        + "text ({@code …}).");
    }
}
