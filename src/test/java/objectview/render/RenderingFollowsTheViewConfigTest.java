package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;
import objectview.field.ViewableContractFieldSet;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ViewConfigEditor;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field's value is shown if and only if the field is ticked in the ViewConfig.
 * Ticking an object field shows the object (a collection keeps its "field (N)"
 * header); its own fields are shown exactly when they are ticked. DISPLAY is one
 * of those fields: ticking it paints the object's caption once and changes nothing else.
 *
 * <p>The fixture is History's shape — a ruler's plain list of office holdings,
 * each naming a position — and the list is deliberately not an annotated
 * reference: that is the shape that used to reach ValueRenderer's own member
 * chip, which painted every member's name with DISPLAY unticked.
 */
class RenderingFollowsTheViewConfigTest {

    private static final Ruler WIGMUND = new Ruler("Wigmund of Mercia", List.of(
            new Office("Wigmund's kingship",
                    new Seat("King of Mercia", "Mercia",
                            List.of(new Seat("monarch", "", List.of()))), "840"),
            new Office("Wigmund's crown",
                    new Seat("monarch", "England", List.of()), "839"),
            new Office("Wigmund's council seat",
                    new Seat("Councillor of Mercia", "Mercia", List.of()), "838")));

    /**
     * The complete object-collection selection matrix.  This deliberately uses the
     * History shape that exposed the bug: offices may also be top-level objects, but
     * expanding the selected offices collection must still project exactly its
     * selected member fields.  Each boolean has an independent visible consequence;
     * DISPLAY is never allowed to gate source or position.
     */
    @TestFactory Stream<DynamicTest> objectCollectionSelectionMatrix() {
        List<DynamicTest> cases = new ArrayList<>();
        for (boolean collectionSelected : List.of(false, true)) {
            for (boolean expanded : List.of(false, true)) {
                for (boolean memberDisplay : List.of(false, true)) {
                    for (boolean sourceSelected : List.of(false, true)) {
                        for (boolean positionSelected : List.of(false, true)) {
                            for (boolean membersTopLevel : List.of(false, true)) {
                                String name = "collection=" + collectionSelected
                                        + ", expanded=" + expanded
                                        + ", display=" + memberDisplay
                                        + ", source=" + sourceSelected
                                        + ", position=" + positionSelected
                                        + ", membersTopLevel=" + membersTopLevel;
                                cases.add(DynamicTest.dynamicTest(name, () -> {
                                    ViewConfig member = offices();
                                    if (memberDisplay) {
                                        member.addField(display(Office.class), ViewConfig.leaf());
                                    }
                                    if (sourceSelected) {
                                        member.addField("source", config(Holder.class));
                                    }
                                    if (positionSelected) {
                                        member.addField("position", config(Seat.class));
                                    }

                                    ViewConfig root = config(Ruler.class);
                                    root.addField(display(Ruler.class), ViewConfig.leaf());
                                    if (collectionSelected) root.addField("offices", member);

                                    RenderContext context = new RenderContext();
                                    context.addTopLevel(WIGMUND);
                                    if (membersTopLevel) {
                                        WIGMUND.offices.forEach(context::addTopLevel);
                                    }
                                    if (expanded) {
                                        context.setCollectionExpanded(WIGMUND.offices, true);
                                    }

                                    Card card = render(root, context);
                                    boolean memberBodyVisible = collectionSelected && expanded;

                                    assertEquals(collectionSelected,
                                            hasFieldRow(card, "offices"), name);
                                    assertEquals(memberBodyVisible && memberDisplay,
                                            paints(card, "Wigmund's kingship"), name);
                                    assertEquals(memberBodyVisible && sourceSelected,
                                            hasFieldRow(card, "source"), name);
                                    assertEquals(memberBodyVisible && positionSelected,
                                            hasFieldRow(card, "position"), name);
                                    assertFalse(paints(card, "King of Mercia"),
                                            "position DISPLAY is unticked: " + name);
                                }));
                            }
                        }
                    }
                }
            }
        }
        return cases.stream();
    }

    /** Scalar references deliberately differ from collection members: once their
     * target has its own top-level card, the occurrence is only a navigation link. */
    @TestFactory Stream<DynamicTest> scalarReferenceSelectionMatrix() {
        List<DynamicTest> cases = new ArrayList<>();
        for (boolean targetTopLevel : List.of(false, true)) {
            for (boolean targetDisplay : List.of(false, true)) {
                for (boolean childSelected : List.of(false, true)) {
                    String name = "targetTopLevel=" + targetTopLevel
                            + ", display=" + targetDisplay
                            + ", child=" + childSelected;
                    cases.add(DynamicTest.dynamicTest(name, () -> {
                        Crowned owner = new Crowned(WIGMUND);
                        ViewConfig nestedRuler = config(Ruler.class);
                        if (targetDisplay) {
                            nestedRuler.addField(
                                    display(Ruler.class), ViewConfig.leaf());
                        }
                        if (childSelected) {
                            nestedRuler.addField("offices", offices());
                        }
                        ViewConfig ownerConfig = config(Crowned.class);
                        ownerConfig.addField("ruler", nestedRuler);
                        RenderContext context = new RenderContext();
                        context.addTopLevel(owner);
                        if (targetTopLevel) context.addTopLevel(WIGMUND);

                        Card card = new Card(owner, ownerConfig, context, false);

                        assertEquals(childSelected && !targetTopLevel,
                                hasFieldRow(card, "offices"), name);
                        ReferenceRow occurrence = findReference(card, WIGMUND);
                        assertEquals(targetTopLevel,
                                occurrence != null && occurrence.navigatesToTopLevel(), name);
                        assertEquals(targetDisplay,
                                paints(card, "Wigmund of Mercia"), name);
                    }));
                }
            }
        }
        return cases.stream();
    }

    @Test void anObjectListWithNothingTickedUnderItShowsItsCountAndNoMember() throws Exception {
        Card card = render(ruler(offices()));

        CollectionHeader header = find(card, CollectionHeader.class);
        assertNotNull(header, "the ticked list is shown as a list, not a bare caption");
        assertTrue(header.getToolTipText().contains("(3 items)"), header.getToolTipText());
        RenderContext opened = new RenderContext(List.of(WIGMUND));
        opened.setCollectionExpanded(WIGMUND.offices, true);
        Card open = render(ruler(offices()), opened);
        assertFalse(paints(open, "Wigmund's kingship"), "office DISPLAY is not ticked");
        assertFalse(paints(open, "King of Mercia"), "position is not ticked");
    }

    @Test void tickingTheMembersDisplayPaintsOneConfiguredCaption()
            throws Exception {
        ViewConfig offices = offices();
        offices.addField(display(Office.class), ViewConfig.leaf());

        Card card = renderOpen(ruler(offices));

        assertTrue(paints(card, "Wigmund's kingship"));
        assertTrue(hasTextField(card, FieldPath.of("offices", "label"),
                        "Wigmund's kingship"),
                "the caption carries the DISPLAY path and value");
        assertEquals(1, paintCount(card, "Wigmund's kingship"),
                "DISPLAY has one painted occurrence, not a caption plus a body row");
        assertFalse(paints(card, "All offices/Wigmund's kingship"),
                "a reference label is not the selected DISPLAY field value");
        assertFalse(paints(card, "King of Mercia"), "position is not ticked");
        assertFalse(paints(card, "840"), "startDate is not ticked");
    }

    @Test void tickedNestedFieldsShowWithoutAClickWhetherOrNotDisplayIsTicked() throws Exception {
        for (boolean officeDisplay : new boolean[] {false, true}) {
            ViewConfig seat = config(Seat.class);
            seat.addField(display(Seat.class), ViewConfig.leaf());
            seat.addField("country", ViewConfig.leaf());
            ViewConfig offices = offices();
            if (officeDisplay) offices.addField(display(Office.class), ViewConfig.leaf());
            offices.addField("position", seat);

            Card card = renderOpen(ruler(offices));

            assertTrue(paints(card, "King of Mercia"),
                    "position's ticked DISPLAY, office DISPLAY ticked=" + officeDisplay);
            assertTrue(paints(card, "Mercia"),
                    "position's ticked country, office DISPLAY ticked=" + officeDisplay);
            assertEquals(officeDisplay, paints(card, "Wigmund's kingship"),
                    "the office caption follows exactly its own DISPLAY tick");
        }
    }

    @Test void selectedCollectionMembersAreBuiltOnlyAfterTheCollectionIsExpanded()
            throws Exception {
        ViewConfig seat = config(Seat.class);
        seat.addField(display(Seat.class), ViewConfig.leaf());
        ViewConfig offices = offices();
        offices.addField("position", seat);

        Card closed = render(ruler(offices));

        assertNotNull(find(closed, CollectionHeader.class));
        assertFalse(paints(closed, "King of Mercia"),
                "member fields are lazy behind the collection disclosure");
        assertTrue(paints(renderOpen(ruler(offices)), "King of Mercia"));
    }

    @Test void aTickedObjectWithNothingTickedUnderItShowsOnlyItsFieldName() throws Exception {
        ViewConfig offices = offices();
        offices.addField("position", config(Seat.class));

        Card card = renderOpen(ruler(offices));

        assertTrue(hasFieldRow(card, "position"));
        assertFalse(paints(card, "King of Mercia"), "position DISPLAY is not ticked");
        assertFalse(paints(card, "Mercia"), "country is not ticked");
    }

    @Test void anUntickedMemberDisplayDoesNotDisableItsSelectedObjectFields()
            throws Exception {
        ViewConfig offices = offices();
        offices.addField("source", config(Holder.class));
        offices.addField("position", config(Seat.class));

        RenderContext context = new RenderContext();
        context.addTopLevel(WIGMUND);
        WIGMUND.offices.forEach(context::addTopLevel);
        context.setCollectionExpanded(WIGMUND.offices, true);
        Card card = render(ruler(offices), context);

        assertFalse(paints(card, "Wigmund's kingship"),
                "unticked office DISPLAY must not leak as a caption");
        assertTrue(hasFieldRow(card, "source"),
                "the office remains present and renders its selected source field");
        assertTrue(hasFieldRow(card, "position"),
                "the office remains present and renders its selected position field");
        assertFalse(paints(card, "King of Mercia"),
                "unticked position DISPLAY must not leak as a caption");
    }

    @Test void theSameNestedConfigRendersDisplayInPositionAndSuperclasses()
            throws Exception {
        Seat king = WIGMUND.offices.get(0).position;
        ViewConfig superclass = config(Seat.class);
        superclass.addField(display(Seat.class), ViewConfig.leaf());
        ViewConfig seat = config(Seat.class);
        seat.addField(display(Seat.class), ViewConfig.leaf());
        seat.addField("superClasses", superclass);
        ViewConfig offices = offices();
        offices.addField("position", seat);

        RenderContext context = new RenderContext(List.of(WIGMUND));
        context.setCollectionExpanded(WIGMUND.offices, true);
        context.setCollectionExpanded(king.superClasses, true);
        Card card = render(ruler(offices), context);

        assertTrue(hasTextField(card,
                FieldPath.of("offices", "position", "name"), "King of Mercia"));
        assertTrue(hasTextField(card,
                FieldPath.of("offices", "position", "superClasses", "name"),
                "monarch"));
    }

    @Test void aCaptionlessTopLevelTargetRemainsANavigableReference()
            throws Exception {
        Office office = WIGMUND.offices.get(0);
        ViewConfig offices = offices();
        offices.addField("source", config(Holder.class));
        RenderContext context = new RenderContext(List.of(WIGMUND));
        context.addTopLevel(office.source);
        context.setCollectionExpanded(WIGMUND.offices, true);

        Card card = render(ruler(offices), context);
        ReferenceRow source = findReference(card, office.source);

        assertNotNull(source);
        assertTrue(source.navigatesToTopLevel(),
                "caption selection must not control top-level navigation");
        assertTrue(source.matchesRenderedText(
                        List.of(ReferenceRow.NAVIGATION_LABEL), true),
                "the link names the action, not the unticked display");
        assertEquals(1, paintCount(card, "Wigmund of Mercia"),
                "the ruler's own title only; the source's unticked name is not painted");
    }

    /** An embedded cycle back to a top-level object ends in a link to it. With its
     * DISPLAY unticked the link still reads as one, never as a blank row. */
    @Test void aCaptionlessBackReferenceToATopLevelObjectIsALabelledLink()
            throws Exception {
        Twin first = new Twin("first");
        Twin second = new Twin("second");
        first.other = second;
        second.other = first;
        ViewConfig back = config(Twin.class);
        back.addField("note", ViewConfig.leaf());
        ViewConfig middle = config(Twin.class);
        middle.addField("other", back);
        ViewConfig root = config(Twin.class);
        root.addField("other", middle);
        RenderContext context = new RenderContext(List.of(first));

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(
                () -> card[0] = new Card(first, root, context, false));

        ReferenceRow link = findReference(card[0], first);
        assertNotNull(link, "the cycle back to the top-level card is a link");
        assertTrue(link.matchesRenderedText(
                List.of(ReferenceRow.NAVIGATION_LABEL), true));
        assertFalse(paints(card[0], "first"), "DISPLAY is unticked on every level");
    }

    @Test void aScalarTopLevelTargetIsOnlyANavigationLinkNotASecondInlineCard()
            throws Exception {
        Crowned owner = new Crowned(WIGMUND);
        ViewConfig nestedRuler = config(Ruler.class);
        nestedRuler.addField(display(Ruler.class), ViewConfig.leaf());
        nestedRuler.addField("offices", offices());
        ViewConfig ownerConfig = config(Crowned.class);
        ownerConfig.addField("ruler", nestedRuler);
        RenderContext context = new RenderContext(List.of(owner, WIGMUND));

        Card card = new Card(owner, ownerConfig, context, false);

        ReferenceRow link = findReference(card, WIGMUND);
        assertNotNull(link);
        assertTrue(link.navigatesToTopLevel());
        assertTrue(paints(card, "Wigmund of Mercia"));
        assertFalse(hasFieldRow(card, "offices"),
                "the scalar target's selected fields belong to its own top-level card");
    }

    @Test void untickingNestedDisplaysDoesNotRemoveSelectedSiblingObjectsFromTheEditor()
            throws Exception {
        ViewConfig office = ViewConfig.of(Office.class);
        ViewConfig root = ruler(office);
        ViewConfig[] edited = new ViewConfig[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(root, WIGMUND);
            assertTrue(editor.uncheckFieldPath(FieldPath.of("offices", "label")));
            assertTrue(editor.uncheckFieldPath(
                    FieldPath.of("offices", "position", "name")));
            edited[0] = editor.getConfig();
        });

        ViewConfig result = edited[0].getFieldConfig("offices");
        assertNotNull(result);
        assertTrue(result.hasField("source"));
        assertTrue(result.hasField("position"));
        assertFalse(result.hasField("label"));
        assertFalse(result.getFieldConfig("position").hasField("name"));
    }

    @Test void anEmptyTickedCollectionStillShowsItsZeroSize() throws Exception {
        Ruler nobody = new Ruler("Nobody", List.of());
        ViewConfig config = ruler(offices());
        config.addField("epithet", ViewConfig.leaf());

        Card card = new Card(nobody, config, new RenderContext(List.of(nobody)), false);

        CollectionHeader header = find(card, CollectionHeader.class);
        assertNotNull(header);
        assertEquals("0 items", header.getToolTipText());
        assertTrue(hasFieldRow(card, "offices"));
        assertFalse(hasFieldRow(card, "epithet"));
    }

    @Test void anEmptyTickedCollectionRemainsContentBehindAReferenceCaption()
            throws Exception {
        Ruler nobody = new Ruler("Nobody", List.of());
        Crowned owner = new Crowned(nobody);
        ViewConfig ruler = config(Ruler.class);
        ruler.addField(display(Ruler.class), ViewConfig.leaf());
        ruler.addField("offices", offices());
        ViewConfig ownerConfig = config(Crowned.class);
        ownerConfig.addField("ruler", ruler);

        Card card = new Card(
                owner, ownerConfig, new RenderContext(List.of(owner)), false);

        CollectionHeader header = find(card, CollectionHeader.class);
        assertNotNull(header, "the reference body contains the selected empty list");
        assertEquals("0 items", header.getToolTipText());
    }

    @Test void displayComesFromTheAuthoritativeSchemaFieldNotFromANameFallback()
            throws Exception {
        Unannotated child = new Unannotated("Schema title", "Fallback title");
        Owner owner = new Owner(child);
        FieldSchema childSchema = () -> List.of(FieldRef.described(
                "title", "Title", FieldRole.DISPLAY,
                FieldKind.TEXT, FieldKind.TEXT, "String",
                false, false, null, false, false,
                false, false, "", false));
        RenderContext context = new RenderContext(List.of(owner));
        context.setFieldSchemaResolver(value -> value == child ? childSchema : null);
        ViewConfig childConfig = config(Unannotated.class);
        childConfig.addField("title", ViewConfig.leaf());
        ViewConfig ownerConfig = config(Owner.class);
        ownerConfig.addField("child", childConfig);

        Card card = new Card(owner, ownerConfig, context, false);

        assertTrue(paints(card, "Schema title"));
        assertFalse(paints(card, "Fallback title"));
    }

    // --- configs ---

    private static ViewConfig config(Class<? extends objectview.Viewable> cls) {
        ViewConfig config = ViewConfig.leaf();
        config.setCls(cls);
        return config;
    }

    private static ViewConfig offices() {
        return config(Office.class);
    }

    private static ViewConfig ruler(ViewConfig offices) {
        ViewConfig config = config(Ruler.class);
        config.addField(display(Ruler.class), ViewConfig.leaf());
        config.addField("offices", offices);
        return config;
    }

    private static String display(Class<? extends objectview.Viewable> cls) {
        return ViewableContractFieldSet.displayKey(cls);
    }

    // --- rendering ---

    private static Card render(ViewConfig config) throws Exception {
        return render(config, new RenderContext(List.of(WIGMUND)));
    }

    private static Card renderOpen(ViewConfig config) throws Exception {
        RenderContext context = new RenderContext(List.of(WIGMUND));
        context.setCollectionExpanded(WIGMUND.offices, true);
        return render(config, context);
    }

    private static Card render(ViewConfig config, RenderContext context) throws Exception {
        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(
                () -> card[0] = new Card(WIGMUND, config, context, false));
        return card[0];
    }

    private static boolean paints(Component root, String text) {
        if (root instanceof TextRow row && row.matchesRenderedText(List.of(text), true)) {
            return true;
        }
        if (root instanceof TextBlock block && blockPaints(block, text)) {
            return true;
        }
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (paints(child, text)) return true;
            }
        }
        return false;
    }

    private static int paintCount(Component root, String text) {
        int count = 0;
        if (root instanceof TextRow row && row.matchesRenderedText(List.of(text), true)) count++;
        if (root instanceof TextBlock block && blockPaints(block, text)) count++;
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) count++;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                count += paintCount(child, text);
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static boolean blockPaints(TextBlock block, String text) {
        try {
            java.lang.reflect.Field rows = TextBlock.class.getDeclaredField("rows");
            rows.setAccessible(true);
            for (TextBlock.Row row : (List<TextBlock.Row>) rows.get(block)) {
                if (row.lines().contains(text)) return true;
            }
            return false;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean hasFieldRow(Component root, String fieldName) {
        if (root instanceof javax.swing.JComponent component
                && fieldName.equals(component.getClientProperty(
                        objectview.field.FieldProperties.FIELD_NAME_PROPERTY))) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (hasFieldRow(child, fieldName)) return true;
            }
        }
        return false;
    }

    private static boolean hasTextField(
            Component root, FieldPath path, String value) {
        if (root instanceof TextBlock block
                && block.hasMatchingRow(path, List.of(value))) return true;
        if (root instanceof TextRow row
                && row.represents(path)
                && row.matchesRenderedText(List.of(value), true)) return true;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (hasTextField(child, path, value)) return true;
            }
        }
        return false;
    }

    private static ReferenceRow findReference(Component root, objectview.Viewable target) {
        if (root instanceof ReferenceRow row && row.target() == target) return row;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                ReferenceRow found = findReference(child, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    // --- fixture ---

    private static final class Ruler extends ViewableAdapter {
        @DisplayField private final String name;
        @Reference
        private final List<Office> offices;
        private String epithet;

        private Ruler(String name, List<Office> offices) {
            this.name = name;
            this.offices = offices;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Office extends ViewableAdapter {
        @DisplayField private final String label;
        @Reference private final Holder source;
        @Reference
        private final Seat position;
        private final String startDate;

        private Office(String label, Seat position, String startDate) {
            this.label = label;
            this.source = new Holder("Wigmund of Mercia");
            this.position = position;
            this.startDate = startDate;
        }

        @Override public String getIdentifier() { return label; }
        @Override public String getDisplayName() { return label; }
        @Override public String getReferenceLabel() { return "All offices/" + label; }
    }

    private static final class Seat extends ViewableAdapter {
        @DisplayField private final String name;
        private final String country;
        @Reference private final List<Seat> superClasses;

        private Seat(String name, String country, List<Seat> superClasses) {
            this.name = name;
            this.country = country;
            this.superClasses = superClasses;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Holder extends ViewableAdapter {
        @DisplayField private final String name;

        private Holder(String name) { this.name = name; }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Owner extends ViewableAdapter {
        private final Unannotated child;
        private Owner(Unannotated child) { this.child = child; }
        @Override public String getIdentifier() { return "owner"; }
        @Override public String getDisplayName() { return "Owner"; }
    }

    private static final class Crowned extends ViewableAdapter {
        private final Ruler ruler;
        private Crowned(Ruler ruler) { this.ruler = ruler; }
        @Override public String getIdentifier() { return "crowned"; }
        @Override public String getDisplayName() { return "Crowned"; }
    }

    private static final class Twin extends ViewableAdapter {
        @DisplayField private final String name;
        @SuppressWarnings("unused") private final String note = "a note";
        @objectview.annotations.Inline private Twin other;
        private Twin(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Unannotated extends ViewableAdapter {
        private final String title;
        private final String fallback;
        private Unannotated(String title, String fallback) {
            this.title = title;
            this.fallback = fallback;
        }
        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return fallback; }
    }
}
