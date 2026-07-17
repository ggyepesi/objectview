package objectview.virtual;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.group.ViewableGroup;
import objectview.render.Card;
import objectview.render.CardListener;
import objectview.render.RenderContext;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class VirtualizedGroupTreeView extends JPanel
        implements ConfigurableVirtualizedContainer {

    private static final int INDENT = 18;

    private final ViewableGroup<?> root;
    private final VirtualizedCardList rows;
    private final JScrollPane rowsScroll;
    private final RenderContext renderContext = new RenderContext();

    private final Set<ViewableGroup<?>> expandedGroups =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Viewable> members = new ArrayList<>();

    private final Map<Viewable, List<MemberRow>> rowsByMember =
            new IdentityHashMap<>();
    private final Map<ViewableGroup<?>, GroupRow> rowByGroup =
            new IdentityHashMap<>();

    private final List<Viewable> visibleRows = new ArrayList<>();

    private ViewConfig cardConfig;
    private Comparator<Viewable> memberComparator;
    private Viewable lastNavigated;
    private CardListener targetListener;

    public VirtualizedGroupTreeView(
            ViewableGroup<?> root,
            ViewConfig cardConfig) {

        this.root = Objects.requireNonNull(root, "root");
        this.cardConfig = cardConfig == null ? null : cardConfig.copy();

        setLayout(new BorderLayout());

        collectUniqueMembers(root, members);
        for (Viewable member : members) {
            renderContext.addTopLevel(member);
        }

        renderContext.setCollapsibleCards(true);

        rows = new VirtualizedCardList(this::createRowComponent);

        renderContext.addCardToggleHandler(this::invalidateMemberOccurrences);

        rows.setNavigateRevealHandler(rowObject -> {
            if (rowObject instanceof MemberRow row) {
                revealMemberCard(row.member);
            }
        });

        rows.setOnCardBuilt(component -> {
            Card panel = findCard(component);
            if (panel != null && targetListener != null) {
                targetListener.cardMaterialized(panel);
            }
        });

        rowsScroll = new JScrollPane();
        rows.install(rowsScroll);
        add(rowsScroll, BorderLayout.CENTER);

        expandedGroups.add(root);
        rebuildVisibleRows();
    }

    public void setTargetListener(CardListener listener) {
        this.targetListener = listener;
    }

    public RenderContext renderContext() {
        return renderContext;
    }

    public JScrollPane scrollPane() {
        return rowsScroll;
    }

    public VirtualizedCardList virtualList() {
        return rows;
    }

    @Override
    public List<Viewable> items() {
        return Collections.unmodifiableList(members);
    }

    @Override
    public Viewable topVisibleItem() {
        Viewable topRow = rows.topVisibleItem();

        if (topRow instanceof MemberRow memberRow) {
            return memberRow.member;
        }

        if (topRow != null) {
            int index = identityIndexOf(visibleRows, topRow);
            for (int i = Math.max(0, index + 1); i < visibleRows.size(); i++) {
                if (visibleRows.get(i) instanceof MemberRow memberRow) {
                    return memberRow.member;
                }
            }
        }

        return lastNavigated != null
                ? lastNavigated
                : members.isEmpty() ? null : members.getFirst();
    }

    @Override
    public JComponent navigateToTop(Viewable member) {
        if (member == null) {
            return null;
        }

        List<MemberRow> occurrences = rowsByMember.get(member);

        if (occurrences == null || occurrences.isEmpty()) {
            expandPathTo(member);
            rebuildVisibleRows();
            occurrences = rowsByMember.get(member);
        }

        if (occurrences == null || occurrences.isEmpty()) {
            return null;
        }

        MemberRow row = occurrences.getFirst();
        JComponent wrapper = rows.navigateToTop(row);
        lastNavigated = member;

        return findCard(wrapper);
    }

    @Override
    public void setItems(List<Viewable> orderedMembers) {
        Map<Viewable, Integer> rank = new IdentityHashMap<>();

        if (orderedMembers != null) {
            for (int i = 0; i < orderedMembers.size(); i++) {
                rank.put(orderedMembers.get(i), i);
            }
            members.clear();
            members.addAll(orderedMembers);
        }

        memberComparator = Comparator.comparingInt(
                member -> rank.getOrDefault(member, Integer.MAX_VALUE));

        rebuildVisibleRows();
    }

    @Override
    public void setCardConfig(ViewConfig config) {
        cardConfig = config == null ? null : config.copy();
        rows.setCardFactory(this::createRowComponent);
    }

    private void invalidateMemberOccurrences(Viewable member) {
        List<MemberRow> occurrences = rowsByMember.get(member);
        if (occurrences == null) {
            return;
        }
        for (MemberRow row : new ArrayList<>(occurrences)) {
            rows.invalidateCard(row);
        }
    }

    private void revealMemberCard(Viewable member) {
        if (member == null || renderContext.isCardExpanded(member)) {
            return;
        }

        renderContext.toggleCardExpanded(member);
        renderContext.notifyCardToggled(member);
    }

    private void rebuildVisibleRows() {
        visibleRows.clear();
        rowsByMember.clear();
        rowByGroup.clear();

        appendGroupContents(root, 0);
        rows.setItems(new ArrayList<>(visibleRows));
    }

    private void appendGroupContents(ViewableGroup<?> group, int depth) {
        for (ViewableGroup<?> child : new ArrayList<>(group.getChildren())) {
            if (child == null || child.getMembers().isEmpty()) {
                continue;
            }

            GroupRow groupRow =
                    new GroupRow(child, depth, expandedGroups.contains(child));

            visibleRows.add(groupRow);
            rowByGroup.put(child, groupRow);

            if (!groupRow.expanded) {
                continue;
            }

            if (!child.getChildren().isEmpty()) {
                appendGroupContents(child, depth + 1);
            }

            addMemberRows(directMembersOf(child), depth + 1);
        }

        if (group == root) {
            addMemberRows(directMembersOf(root), depth);
        }
    }

    private void addMemberRows(List<Viewable> directMembers, int depth) {
        directMembers.sort(effectiveMemberComparator());

        for (Viewable member : directMembers) {
            MemberRow row = new MemberRow(member, depth);
            visibleRows.add(row);
            rowsByMember
                    .computeIfAbsent(member, ignored -> new ArrayList<>())
                    .add(row);
        }
    }

    private Comparator<Viewable> effectiveMemberComparator() {
        if (memberComparator != null) {
            return memberComparator;
        }

        return Comparator.comparing(
                member -> Objects.toString(member.getDisplayName(), ""),
                String.CASE_INSENSITIVE_ORDER);
    }

    private List<Viewable> directMembersOf(ViewableGroup<?> group) {
        if (group.getChildren().isEmpty()) {
            return identityDistinct(group.getMembers());
        }

        Set<Viewable> childMembers =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (ViewableGroup<?> child : group.getChildren()) {
            collectUniqueMembers(child, childMembers);
        }

        List<Viewable> direct = new ArrayList<>();

        for (Viewable member : group.getMembers()) {
            if (member != null && !childMembers.contains(member)) {
                direct.add(member);
            }
        }

        return identityDistinct(direct);
    }

    private JComponent createRowComponent(Viewable rowObject) {
        if (rowObject instanceof GroupRow groupRow) {
            return createGroupHeader(groupRow);
        }
        if (rowObject instanceof MemberRow memberRow) {
            return createMemberCard(memberRow);
        }
        throw new IllegalArgumentException("Unknown outline row: " + rowObject);
    }

    private JComponent createGroupHeader(GroupRow row) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(
                3, row.depth * INDENT, 3, 4));

        JButton button = new JButton(
                (row.expanded ? "▾ " : "▸ ") + groupLabel(row.group));

        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.addActionListener(e -> toggleGroup(row.group));

        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

    private String groupLabel(ViewableGroup<?> group) {
        int memberCount = identityDistinct(group.getMembers()).size();
        int childCount = nonEmptyChildCount(group);

        if (childCount > 0) {
            return group.getDisplayName()
                    + " (" + childCount + " groups · "
                    + memberCount + " members)";
        }

        return group.getDisplayName()
                + " (" + memberCount + " members)";
    }

    private JComponent createMemberCard(MemberRow row) {
        JPanel indented = new JPanel(new BorderLayout());
        indented.setOpaque(false);
        indented.setBorder(BorderFactory.createEmptyBorder(
                2, row.depth * INDENT, 2, 4));

        ViewConfig config = configFor(row.member);

        Card card = new Card(
                row.member,
                config,
                renderContext,
                false);

        renderContext.registerTopLevel(row.member, card);
        indented.add(card, BorderLayout.CENTER);

        return indented;
    }

    private ViewConfig configFor(Viewable member) {
        if (cardConfig == null) {
            @SuppressWarnings("unchecked")
            Class<? extends Viewable> cls =
                    (Class<? extends Viewable>) member.getClass();
            return ViewConfig.all(cls);
        }

        ViewConfig copy = cardConfig.copy();

        if (copy.getCls() == null) {
            @SuppressWarnings("unchecked")
            Class<? extends Viewable> cls =
                    (Class<? extends Viewable>) member.getClass();
            copy.setCls(cls);
        }

        return copy;
    }

    private void toggleGroup(ViewableGroup<?> group) {
        if (!expandedGroups.remove(group)) {
            expandedGroups.add(group);
        }

        rebuildVisibleRows();

        GroupRow replacement = rowByGroup.get(group);
        if (replacement != null) {
            rows.ensureVisible(replacement);
        }
    }

    private void expandPathTo(Viewable member) {
        List<ViewableGroup<?>> path = findContainingPath(root, member);
        if (path == null) {
            return;
        }

        expandedGroups.add(root);
        expandedGroups.addAll(path);
    }

    private List<ViewableGroup<?>> findContainingPath(
            ViewableGroup<?> group,
            Viewable target) {

        for (ViewableGroup<?> child : group.getChildren()) {
            if (!containsIdentity(child.getMembers(), target)) {
                continue;
            }

            List<ViewableGroup<?>> deeper = findContainingPath(child, target);
            List<ViewableGroup<?>> result = new ArrayList<>();
            result.add(child);

            if (deeper != null) {
                result.addAll(deeper);
            }

            return result;
        }

        return containsIdentity(group.getMembers(), target)
                ? List.of()
                : null;
    }

    private static Card findCard(Component root) {
        if (root instanceof Card panel) {
            return panel;
        }

        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Card found = findCard(child);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static int nonEmptyChildCount(ViewableGroup<?> group) {
        int count = 0;

        for (ViewableGroup<?> child : group.getChildren()) {
            if (child != null
                    && (!child.getChildren().isEmpty()
                    || !child.getMembers().isEmpty())) {
                count++;
            }
        }

        return count;
    }

    private static void collectUniqueMembers(
            ViewableGroup<?> group,
            Collection<Viewable> out) {

        if (group == null) {
            return;
        }

        for (Viewable member : group.getMembers()) {
            if (member != null && !containsIdentity(out, member)) {
                out.add(member);
            }
        }

        for (ViewableGroup<?> child : group.getChildren()) {
            collectUniqueMembers(child, out);
        }
    }

    private static List<Viewable> identityDistinct(
            Collection<? extends Viewable> input) {

        Set<Viewable> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        List<Viewable> result = new ArrayList<>();

        if (input != null) {
            for (Viewable value : input) {
                if (value != null && seen.add(value)) {
                    result.add(value);
                }
            }
        }

        return result;
    }

    private static boolean containsIdentity(
            Collection<? extends Viewable> values,
            Viewable candidate) {

        for (Viewable value : values) {
            if (value == candidate) {
                return true;
            }
        }

        return false;
    }

    private static int identityIndexOf(
            List<? extends Viewable> values,
            Viewable candidate) {

        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == candidate) {
                return i;
            }
        }

        return -1;
    }

    private sealed interface OutlineRow permits GroupRow, MemberRow {}

    private static final class GroupRow
            extends ViewableAdapter
            implements OutlineRow {

        private final ViewableGroup<?> group;
        private final int depth;
        private final boolean expanded;

        private GroupRow(
                ViewableGroup<?> group,
                int depth,
                boolean expanded) {
            this.group = group;
            this.depth = depth;
            this.expanded = expanded;
        }

        @Override
        public String getIdentifier() {
            return "group:"
                    + System.identityHashCode(group)
                    + ":"
                    + depth;
        }

        @Override
        public String getDisplayName() {
            return group.getDisplayName();
        }
    }

    private static final class MemberRow
            extends ViewableAdapter
            implements OutlineRow {

        private final Viewable member;
        private final int depth;

        private MemberRow(Viewable member, int depth) {
            this.member = member;
            this.depth = depth;
        }

        @Override
        public String getIdentifier() {
            return "member-row:"
                    + System.identityHashCode(this);
        }

        @Override
        public String getDisplayName() {
            return member.getDisplayName();
        }
    }
}
