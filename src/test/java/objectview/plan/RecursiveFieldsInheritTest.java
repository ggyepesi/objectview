package objectview.plan;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ViewConfigEditor;
import objectview.viewconfig.ViewConfigJsonIO;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field whose type is already on the config path has no config of its own: it
 * inherits the nearest ancestor's of that type, and starts folded so each expand opens
 * one level (#368). Reported: History's offices → position → replaces (a Position under
 * a Position) showed {@code replaces (1)} and opened to nothing, because its members
 * followed their own empty ticks while the editor marked the field ↩ and offered none.
 */
class RecursiveFieldsInheritTest {

    static final class Office extends ViewableAdapter {
        @DisplayField private final String name;
        private final String country;
        @Reference Office predecessor;
        @Reference final List<Office> replaces = new ArrayList<>();

        Office(String name, String country) {
            this.name = name;
            this.country = country;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private final Office president = new Office("President", "Germany");
    private final Office emperor = new Office("Emperor", "German Empire");
    private final Office king = new Office("King of Prussia", "Prussia");

    RecursiveFieldsInheritTest() {
        president.predecessor = emperor;
        emperor.predecessor = king;
        president.replaces.add(emperor);
        emperor.replaces.add(king);
    }

    private static TypeShape shape() {
        return TypeShape.ofClass(Office.class);
    }

    private static ViewConfig nameCountryAndBoth() {
        ViewConfig config = ViewConfig.leaf();
        config.addField("name", ViewConfig.leaf());
        config.addField("country", ViewConfig.leaf());
        config.addField("predecessor", ViewConfig.leaf());
        config.addField("replaces", ViewConfig.leaf());
        return config;
    }

    @Test void theViewDefaultMakesARecursiveFieldInheritItsAncestor() {
        ViewConfig defaults = ViewDefaults.newView(shape());

        assertSame(defaults, defaults.getFieldConfig("predecessor").inheritedFrom());
        assertSame(defaults, defaults.getFieldConfig("replaces").inheritedFrom());
        assertNull(defaults.getFieldConfig("name").inheritedFrom());
    }

    @Test void ticksStoredUnderARecursiveFieldAreNotItsConfig() {
        ViewConfig config = nameCountryAndBoth();
        ViewConfig own = ViewConfig.leaf();
        own.addField("country", ViewConfig.leaf());
        config.addField("predecessor", own);

        ViewConfig literal = ViewConfigDesugar.literal(config, shape());

        assertSame(literal, literal.getFieldConfig("predecessor").inheritedFrom());
        assertTrue(literal.getFieldConfig("predecessor").getFields().isEmpty());
    }

    @Test void aFieldSelectionNeverAddsAnInheritance() {
        ViewConfig selection = ViewConfigDesugar.selection(nameCountryAndBoth(), shape());

        assertNull(selection.getFieldConfig("predecessor").inheritedFrom(),
                "a quiz key selects only the paths ticked one by one");
    }

    @Test void anInheritedObjectStartsFoldedAndEachExpandOpensOneLevel() {
        MockDisclosure disclosure = new MockDisclosure();

        assertEquals("""
                OBJECT <root> caption="President" open
                  TEXT country "Germany"
                  OBJECT predecessor caption="Emperor" folded
                    DEFER predecessor COLLAPSED
                  COLLECTION replaces (1) folded
                    DEFER replaces COLLAPSED""", render(disclosure));

        disclosure.expand(emperor);
        assertEquals("""
                OBJECT <root> caption="President" open
                  TEXT country "Germany"
                  OBJECT predecessor caption="Emperor" open
                    TEXT predecessor.country "German Empire"
                    OBJECT predecessor.predecessor caption="King of Prussia" folded
                      DEFER predecessor.predecessor COLLAPSED
                    COLLECTION predecessor.replaces (1) folded
                      DEFER predecessor.replaces COLLAPSED
                  COLLECTION replaces (1) folded
                    DEFER replaces COLLAPSED""", render(disclosure));
    }

    @Test void anOpenedInheritedCollectionShowsItsMembersUnderTheAncestorsConfig() {
        MockDisclosure disclosure = new MockDisclosure().expand(president.replaces);

        String trace = render(disclosure);

        assertTrue(trace.contains(String.join("\n",
                "  COLLECTION replaces (1) open",
                "    OBJECT replaces[0] caption=\"Emperor\" open",
                "      TEXT replaces[0].country \"German Empire\"",
                "      OBJECT replaces[0].predecessor caption=\"King of Prussia\" folded")),
                trace);
    }

    @Test void anObjectAlreadyOnThePathEndsTheRecursion() {
        king.predecessor = president;
        MockDisclosure disclosure = new MockDisclosure().expand(emperor).expand(king);

        String trace = render(disclosure);

        assertTrue(trace.contains("BACKREF predecessor.predecessor.predecessor \"President\""),
                trace);
    }

    @Test void copyAndSaveKeepWhichAncestorAFieldInherits() {
        ViewConfig literal = ViewConfigDesugar.literal(nameCountryAndBoth(), shape());

        ViewConfig copy = literal.copy();
        assertSame(copy, copy.getFieldConfig("predecessor").inheritedFrom());

        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "recursive-fields-inherit-" + System.nanoTime() + ".json");
        try {
            ViewConfigJsonIO.save(file, literal);
            ViewConfig loaded = ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file));
            assertSame(loaded, loaded.getFieldConfig("replaces").inheritedFrom());
        } finally {
            file.delete();
        }
    }

    @Test void theViewEditorEmitsTheInheritanceItShowsAsArrow() throws Exception {
        ViewConfig[] emitted = new ViewConfig[1];
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(nameCountryAndBoth(), president);
            editor.setInheritsRecursion(true);
            emitted[0] = editor.getConfig();
        });

        assertNotNull(emitted[0].getFieldConfig("predecessor"));
        assertSame(emitted[0], emitted[0].getFieldConfig("predecessor").inheritedFrom());
        assertSame(emitted[0], emitted[0].getFieldConfig("replaces").inheritedFrom());
    }

    @Test void anEditorThatDoesNotInheritKeepsItsOwnTicksUnderARecursiveField()
            throws Exception {
        ViewConfig config = nameCountryAndBoth();
        ViewConfig own = ViewConfig.leaf();
        own.addField("country", ViewConfig.leaf());
        config.addField("predecessor", own);
        ViewConfig[] emitted = new ViewConfig[2];
        SwingUtilities.invokeAndWait(() -> {
            emitted[0] = new ViewConfigEditor(config, true, president).getConfig();
            emitted[1] = new ViewConfigEditor(config, president).getConfig();
        });

        for (ViewConfig editor : emitted) {
            ViewConfig predecessor = editor.getFieldConfig("predecessor");
            assertNull(predecessor.inheritedFrom());
            assertTrue(predecessor.hasField("country"),
                    "a quiz key and a field picker select finite paths, as before");
        }
    }

    @Test void theCardFoldsAnInheritedObjectAndOpensOneLevelPerExpand() throws Exception {
        objectview.render.RenderContext context =
                new objectview.render.RenderContext(List.of(president));
        ViewConfig literal = ViewConfigDesugar.literal(nameCountryAndBoth(), shape());

        javax.swing.JComponent folded = card(literal, context);
        assertTrue(shows(folded, "emperor"), "the inherited level shows as its caption chip");
        assertTrue(!shows(folded, EMPEROR_COUNTRY, "empire") && !shows(folded, "king"),
                "and nothing under it until it is opened");

        context.setExpanded(emperor, true);
        javax.swing.JComponent opened = card(literal, context);
        assertTrue(shows(opened, EMPEROR_COUNTRY, "empire"), "opening it shows its level");
        assertTrue(shows(opened, "king"), "with its own inherited object as a chip");
        assertTrue(!shows(opened, objectview.field.FieldPath.of(
                        "predecessor", "predecessor", "country"), "prussia"),
                "folded again: one level per expand");
    }

    @Test void theCardPreparesALiteralConfigWhoseRecursiveFieldIsStillPlain()
            throws Exception {
        objectview.render.RenderContext context =
                new objectview.render.RenderContext(List.of(president));
        // This is literal in the old, syntactic sense, but predecessor still has its
        // own empty child. Card is the rendering boundary and must apply the recursion
        // rule itself rather than require every caller to remember another operation.
        ViewConfig entered = nameCountryAndBoth();

        javax.swing.JComponent folded = card(entered, context);
        assertTrue(shows(folded, "emperor"));
        assertTrue(!shows(folded, EMPEROR_COUNTRY, "empire"));

        context.setExpanded(emperor, true);
        javax.swing.JComponent opened = card(entered, context);
        assertTrue(shows(opened, EMPEROR_COUNTRY, "empire"),
                "a raw literal config must not reproduce expand-to-nothing");
    }

    @Test void preparationReusesAnAlreadyPreparedConfig() {
        ViewConfig prepared = ViewConfigDesugar.literal(nameCountryAndBoth(), shape());

        assertSame(prepared, ViewConfigDesugar.preparedView(prepared, shape()),
                "rendering many instances of one type must not copy its config per card");
        ViewConfig repaired = ViewConfigDesugar.preparedView(nameCountryAndBoth(), shape());
        assertSame(repaired, repaired.getFieldConfig("predecessor").inheritedFrom(),
                "a raw or old literal config is repaired at the shared rendering boundary");
    }

    @Test void aFiniteSelectionIsNotReinterpretedAsARecursiveView() {
        ViewConfig finite = ViewConfigDesugar.preparedSelection(
                nameCountryAndBoth(), shape());

        assertSame(finite, ViewConfigDesugar.preparedView(finite, shape()));
        assertNull(finite.getFieldConfig("predecessor").inheritedFrom());
        assertTrue(finite.getFieldConfig("predecessor").isFinitePaths(),
                "nested cards retain the same finite-path meaning");
    }

    @Test void aSavedFiniteSelectionKeepsItsMeaning() {
        ViewConfig finite = ViewConfigDesugar.preparedSelection(
                nameCountryAndBoth(), shape());
        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "finite-path-selection-" + System.nanoTime() + ".json");
        try {
            ViewConfigJsonIO.save(file, finite);
            ViewConfig loaded = ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file));

            assertTrue(loaded.isFinitePaths());
            assertTrue(loaded.getFieldConfig("predecessor").isFinitePaths());
            assertNull(loaded.getFieldConfig("predecessor").inheritedFrom());
        } finally {
            file.delete();
        }
    }

    @Test void anInheritedObjectWithoutACaptionFoldsUnderItsFieldName() throws Exception {
        ViewConfig config = ViewConfig.leaf();
        config.addField("country", ViewConfig.leaf());
        config.addField("predecessor", ViewConfig.leaf());
        ViewConfig literal = ViewConfigDesugar.literal(config, shape());
        objectview.render.RenderContext context =
                new objectview.render.RenderContext(List.of(president));

        javax.swing.JComponent folded = card(literal, context);

        assertTrue(shows(folded, "predecessor"), "its field name is the chip that opens it");
        assertTrue(!shows(folded, EMPEROR_COUNTRY, "empire"),
                "a body shown at once would open the next inherited level with it");
    }

    private javax.swing.JComponent card(ViewConfig literal,
                                        objectview.render.RenderContext context)
            throws Exception {
        javax.swing.JComponent[] card = new javax.swing.JComponent[1];
        SwingUtilities.invokeAndWait(() -> card[0] =
                new objectview.render.Card(president, literal, context, false));
        return card[0];
    }

    private static final objectview.field.FieldPath EMPEROR_COUNTRY =
            objectview.field.FieldPath.of("predecessor", "country");

    /** Whether a text block paints {@code token} at {@code path}. */
    private static boolean shows(java.awt.Component root, objectview.field.FieldPath path,
                                 String token) {
        if (root instanceof objectview.render.TextBlock block
                && block.hasMatchingRow(path, List.of(token))) return true;
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                if (shows(child, path, token)) return true;
            }
        }
        return false;
    }

    private static boolean shows(java.awt.Component root, String token) {
        return countOf(root, token) > 0;
    }

    private static int countOf(java.awt.Component root, String token) {
        int count = root instanceof objectview.render.TextRow row
                && row.matchesRenderedText(List.of(token), false) ? 1 : 0;
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                count += countOf(child, token);
            }
        }
        return count;
    }

    private String render(Disclosure disclosure) {
        ViewConfig literal = ViewConfigDesugar.literal(nameCountryAndBoth(), shape());
        MockSink sink = new MockSink();
        new RenderExecutor(new PlanResolver(), value -> null, value -> false, disclosure)
                .render(president, literal, sink);
        return sink.traceText();
    }
}
