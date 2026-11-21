package objectview.demo;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.group.DefaultViewableGroup;
import objectview.group.ViewableGroup;
import objectview.group.ViewableGroupFields;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldSet;
import objectview.render.GroupMembersView;
import objectview.render.RenderContext;
import objectview.view.SearchableView;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Standalone demonstration of ObjectView's group tree and member browser.
 *
 * <p>The outline is the production {@link objectview.render.GroupTreeView}: its disclosure
 * arrow expands hierarchy, its row includes the group's member count, Enter shows the
 * selected group, and <em>Show group fields</em> reveals the group itself as an ordinary ObjectView
 * card. Selecting a row replaces the right side with the same card browser used for ordinary
 * Viewables. Search controls are deliberately omitted until the tree needs them.</p>
 */
public final class ViewableTreeDemo {

    private ViewableTreeDemo() { }

    /** A small domain object whose references demonstrate navigation between member cards. */
    public static final class CelestialBody extends ViewableAdapter {
        @DisplayField public final String name;
        public final String kind;
        @Reference public final CelestialBody parentBody;
        public final String note;

        CelestialBody(String name, String kind, CelestialBody parentBody, String note) {
            this.name = name;
            this.kind = kind;
            this.parentBody = parentBody;
            this.note = note;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String toString() { return name; }
    }

    /** Concrete group adapter used only to supply the self-typed child factory. */
    static final class BodyGroup
            extends DefaultViewableGroup<CelestialBody, BodyGroup> {
        private String description = "";
        private String groupingRule = "";

        BodyGroup(String name) { super(name); }
        @Override protected BodyGroup newChild(String name) { return new BodyGroup(name); }

        BodyGroup details(String description, String groupingRule) {
            this.description = description == null ? "" : description;
            this.groupingRule = groupingRule == null ? "" : groupingRule;
            return this;
        }

        /** The group's own metadata is composed with the standard members/subgroups
         * fields; Show group fields therefore renders both through one ObjectView card. */
        @Override public FieldSet fields() {
            FieldSet metadata = new FieldSet() {
                private final List<FieldRef> fields = List.of(
                        FieldRef.of("description", FieldKind.TEXT, "String",
                                false, false, false),
                        FieldRef.of("groupingRule", FieldKind.TEXT, "String",
                                false, false, false));

                @Override public List<FieldRef> fields() { return fields; }
                @Override public Object read(String name) {
                    return switch (name) {
                        case "description" -> description;
                        case "groupingRule" -> groupingRule;
                        default -> null;
                    };
                }
                @Override public boolean has(String name) {
                    return "description".equals(name) || "groupingRule".equals(name);
                }
                @Override public void write(String name, Object value) {
                    throw new UnsupportedOperationException("read-only demo group field");
                }
            };
            return ViewableGroupFields.of(this, metadata);
        }
    }

    /** Named fixture so the demo's hierarchy can be checked without opening a frame. */
    record DemoData(
            BodyGroup root,
            BodyGroup byKind,
            BodyGroup stars,
            BodyGroup planets,
            BodyGroup moons,
            BodyGroup byParent,
            List<CelestialBody> bodies) { }

    static DemoData sampleData() {
        CelestialBody sun = new CelestialBody(
                "Sun", "star", null, "The Solar System's central star");
        CelestialBody mercury = new CelestialBody(
                "Mercury", "planet", sun, "Innermost planet");
        CelestialBody earth = new CelestialBody(
                "Earth", "planet", sun, "Planet with one natural satellite");
        CelestialBody jupiter = new CelestialBody(
                "Jupiter", "planet", sun, "Largest planet");
        CelestialBody moon = new CelestialBody(
                "Moon", "moon", earth, "Earth's natural satellite");
        CelestialBody io = new CelestialBody(
                "Io", "moon", jupiter, "Volcanically active Galilean moon");
        CelestialBody europa = new CelestialBody(
                "Europa", "moon", jupiter, "Icy Galilean moon");
        List<CelestialBody> bodies = List.of(
                sun, mercury, earth, jupiter, moon, io, europa);

        BodyGroup root = new BodyGroup("Solar System").details(
                "Every celestial body in this bounded demo", "All demo instances");
        BodyGroup byKind = root.getOrCreateChild("By kind").role(ViewableGroup.Role.FACET)
                .details("One branch for each body kind", "kind");
        BodyGroup stars = byKind.getOrCreateChild("Stars").role(ViewableGroup.Role.BUCKET)
                .details("Bodies classified as stars", "kind = star");
        BodyGroup planets = byKind.getOrCreateChild("Planets").role(ViewableGroup.Role.BUCKET)
                .details("Bodies classified as planets", "kind = planet");
        BodyGroup moons = byKind.getOrCreateChild("Moons").role(ViewableGroup.Role.BUCKET)
                .details("Bodies classified as moons", "kind = moon");
        stars.addMember(sun, true);
        List.of(mercury, earth, jupiter).forEach(body -> planets.addMember(body, true));
        List.of(moon, io, europa).forEach(body -> moons.addMember(body, true));

        BodyGroup byParent = root.getOrCreateChild("By parent body")
                .role(ViewableGroup.Role.FACET)
                .details("One branch for each parent body", "parentBody");
        BodyGroup sunSystem = byParent.getOrCreateChild("Sun system")
                .role(ViewableGroup.Role.BUCKET)
                .details("Bodies whose parent is the Sun", "parentBody = Sun");
        BodyGroup earthSystem = byParent.getOrCreateChild("Earth system")
                .role(ViewableGroup.Role.BUCKET)
                .details("Bodies whose parent is Earth", "parentBody = Earth");
        BodyGroup jupiterSystem = byParent.getOrCreateChild("Jupiter system")
                .role(ViewableGroup.Role.BUCKET)
                .details("Bodies whose parent is Jupiter", "parentBody = Jupiter");
        List.of(mercury, earth, jupiter).forEach(body -> sunSystem.addMember(body, true));
        earthSystem.addMember(moon, true);
        List.of(io, europa).forEach(body -> jupiterSystem.addMember(body, true));

        return new DemoData(root, byKind, stars, planets, moons, byParent, bodies);
    }

    static GroupMembersView createView(DemoData data) {
        RenderContext memberContext = new RenderContext();
        SearchableView[] activeMembers = new SearchableView[1];
        GroupMembersView view = new GroupMembersView(
                data.root(),
                group -> {
                    if (activeMembers[0] != null) activeMembers[0].dispose();
                    activeMembers[0] = SearchableView.builder(group.getMembers())
                                .type(CelestialBody.class)
                                .sample(data.bodies().get(0))
                                .renderContext(memberContext)
                                .columns(2)
                                .inlineControls(false)
                                .emptyMessage("This structural group has no direct members.")
                                .build();
                    return activeMembers[0];
                },
                JSplitPane.HORIZONTAL_SPLIT,
                false,
                0.31,
                true,
                memberContext);
        view.setSelectionHandler(group -> view.groups().setStatusText(
                group.getFullName() + " — " + group.getMembers().size()
                        + (group.getMembers().size() == 1 ? " member" : " members")));
        view.selectGroup(data.root(), true);
        return view;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DemoData data = sampleData();
            GroupMembersView view = createView(data);

            JLabel guide = new JLabel("<html><b>Tree:</b> use the disclosure arrows; "
                    + "select a group to show its members; press Enter for the same action. "
                    + "<b>Group fields:</b> select a row and press Show group fields. "
                    + "<b>Members:</b> use the parallel cards to inspect or follow references."
                    + "</html>");
            guide.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
            JPanel content = new JPanel(new BorderLayout(4, 4));
            content.add(guide, BorderLayout.NORTH);
            content.add(view, BorderLayout.CENTER);

            JFrame frame = new JFrame("ObjectView — viewable tree demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(content);
            frame.setSize(1180, 720);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
