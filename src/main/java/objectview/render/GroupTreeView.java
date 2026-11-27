package objectview.render;

import objectview.group.GroupNode;
import objectview.group.StrictMemberHierarchy;
import objectview.group.ViewableGroup;
import objectview.utils.swing.ScrollPaneUtils;
import objectview.utils.swing.WrapLayout;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Compact hierarchical navigation for {@link ViewableGroup}s.
 *
 * <p>The {@link JTree} remains the one group outline: it owns counts, selection and
 * keyboard navigation. The selected group can reveal its ordinary ObjectView card in
 * one details area below the outline; a tree cell renderer never pretends to be an
 * interactive card.</p>
 */
public class GroupTreeView extends JPanel {
    private final ViewableGroup<?> rootGroup;
    private final RenderContext renderContext;
    private final JTree tree;
    private final JScrollPane treeScrollPane;
    private final JSplitPane outlineAndDetails;
    private final JPanel detailsHost = new JPanel(new BorderLayout());
    private final JScrollPane detailsScrollPane = new JScrollPane(detailsHost);
    private final JPanel controls;
    private final JButton showButton;
    private final JToggleButton detailsButton;
    private final JTextField groupSearch = new JTextField(16);
    private final JButton previousMatch = new JButton("\u25c0");
    private final JButton nextMatch = new JButton("\u25b6");
    private final JLabel matchStatus = new JLabel(" ");
    private final JToggleButton sortByName = new JToggleButton("Sort groups by name");
    private final JLabel status;
    private Consumer<ViewableGroup<?>> showGroupHandler;
    private Consumer<ViewableGroup<?>> selectionHandler;
    private Consumer<List<ViewableGroup<?>>> selectionSetHandler = ignored -> {};
    private ViewableGroup<?> detailsGroup;
    private Card detailsCard;
    private boolean detailsExpanded;
    private List<ViewableGroup<?>> searchMatches = List.of();
    private int searchMatchIndex = -1;

    public GroupTreeView(ViewableGroup<?> rootGroup) {
        this(rootGroup, new RenderContext());
    }

    public GroupTreeView(ViewableGroup<?> rootGroup, RenderContext renderContext) {
        this.rootGroup = java.util.Objects.requireNonNull(rootGroup, "rootGroup");
        this.renderContext = java.util.Objects.requireNonNull(
                renderContext, "renderContext");
        setLayout(new BorderLayout(4, 4));

        renderContext.onEmptyFieldsChanged(visible -> {
            if (detailsExpanded) {
                detailsCard = null;
                showDetails(selectedGroup());
            }
        });

        tree = new JTree(buildNode(rootGroup));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> {
            ViewableGroup<?> selected = selectedGroup();
            if (detailsExpanded) showDetails(selected);
            if (selectionHandler != null) selectionHandler.accept(selected);
            selectionSetHandler.accept(selectedGroups());
        });
        tree.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ENTER"), "showGroup");
        tree.getActionMap().put("showGroup", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                showSelectedGroup();
            }
        });
        tree.expandRow(0);
        expandInitiallyVisibleNodes();
        tree.setSelectionRow(0);
        treeScrollPane = new JScrollPane(tree);

        groupSearch.setBorder(BorderFactory.createTitledBorder("Search group names"));
        groupSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateGroupSearch(); }
            @Override public void removeUpdate(DocumentEvent event) { updateGroupSearch(); }
            @Override public void changedUpdate(DocumentEvent event) { updateGroupSearch(); }
        });
        previousMatch.setToolTipText("Previous matching group");
        nextMatch.setToolTipText("Next matching group");
        previousMatch.addActionListener(event -> moveSearchMatch(-1));
        nextMatch.addActionListener(event -> moveSearchMatch(1));
        sortByName.setToolTipText(
                "Sort sibling groups by name; turn off to restore their declared order");
        sortByName.addActionListener(event -> rebuildTree());
        previousMatch.setEnabled(false);
        nextMatch.setEnabled(false);

        outlineAndDetails = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, treeScrollPane, null);
        outlineAndDetails.setResizeWeight(0.7);
        outlineAndDetails.setDividerSize(0);
        outlineAndDetails.setBorder(null);

        detailsButton = new JToggleButton("Show group fields");
        detailsButton.setToolTipText(
                "Show or hide the selected group's ordinary ObjectView fields");
        detailsButton.addActionListener(event ->
                setDetailsExpanded(detailsButton.isSelected()));

        showButton = new JButton("Show instances");
        showButton.addActionListener(event -> showSelectedGroup());
        controls = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        controls.add(detailsButton);
        controls.add(showButton);

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchRow.add(groupSearch);
        searchRow.add(previousMatch);
        searchRow.add(nextMatch);
        searchRow.add(matchStatus);
        searchRow.add(sortByName);
        JPanel header = new JPanel(new BorderLayout());
        header.add(ScrollPaneUtils.horizontalOnly(searchRow), BorderLayout.CENTER);
        header.add(status, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        add(outlineAndDetails, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);
    }

    private static DefaultMutableTreeNode buildNode(ViewableGroup<?> group) {
        return buildNode(group, false);
    }

    private static DefaultMutableTreeNode buildNode(
            ViewableGroup<?> group, boolean sortByName) {
        return buildNode(group, java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>()), sortByName);
    }

    // A persisted/adapted group graph may carry a back-edge. Show a revisited node as a
    // leaf rather than recursing forever.
    private static DefaultMutableTreeNode buildNode(
            ViewableGroup<?> group, java.util.Set<ViewableGroup<?>> visited,
            boolean sortByName) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new GroupNode(group));
        if (visited.add(group)) {
            addChildForest(node, group.getChildren(), visited, sortByName);
        }
        return node;
    }

    /**
     * Preserve authored nesting, then refine only the current sibling set by effective
     * membership. Leaf groups are the semantic buckets; structural nodes with their own
     * authored children stay where their producer put them.
     */
    private static void addChildForest(
            DefaultMutableTreeNode parentNode,
            Collection<? extends ViewableGroup<?>> source,
            Set<ViewableGroup<?>> visited,
            boolean sortByName) {
        List<ViewableGroup<?>> children = source == null ? new ArrayList<>()
                : new ArrayList<>(source);
        Map<ViewableGroup<?>, ViewableGroup<?>> semanticParents =
                StrictMemberHierarchy.parents(children, ViewableGroup::getMembers,
                        child -> child.getChildren().isEmpty());
        Map<ViewableGroup<?>, List<ViewableGroup<?>>> semanticChildren =
                new IdentityHashMap<>();
        semanticParents.forEach((child, parent) ->
                semanticChildren.computeIfAbsent(parent, ignored -> new ArrayList<>())
                        .add(child));
        List<ViewableGroup<?>> roots = children.stream()
                .filter(child -> !semanticParents.containsKey(child))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        order(roots, sortByName);
        for (ViewableGroup<?> root : roots) {
            parentNode.add(buildSemanticNode(root,
                    semanticChildren, visited, sortByName));
        }
    }

    private static DefaultMutableTreeNode buildSemanticNode(
            ViewableGroup<?> group,
            Map<ViewableGroup<?>, List<ViewableGroup<?>>> semanticChildren,
            Set<ViewableGroup<?>> visited,
            boolean sortByName) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new GroupNode(group));
        if (!visited.add(group)) return node;
        addChildForest(node, group.getChildren(), visited, sortByName);
        List<ViewableGroup<?>> inferred = new ArrayList<>(
                semanticChildren.getOrDefault(group, List.of()));
        order(inferred, sortByName);
        for (ViewableGroup<?> child : inferred) {
            node.add(buildSemanticNode(child, semanticChildren, visited, sortByName));
        }
        return node;
    }

    private static void order(
            List<? extends ViewableGroup<?>> groups, boolean sortByName) {
        if (!sortByName) return;
        groups.sort(Comparator.comparing(
                child -> String.valueOf(child.getDisplayName()),
                String.CASE_INSENSITIVE_ORDER));
    }

    private void rebuildTree() {
        ViewableGroup<?> selected = selectedGroup();
        tree.setModel(new DefaultTreeModel(buildNode(rootGroup, sortByName.isSelected())));
        tree.expandRow(0);
        expandInitiallyVisibleNodes();
        if (selected == null || !selectGroup(selected, false)) tree.setSelectionRow(0);
        updateGroupSearch();
    }

    private void updateGroupSearch() {
        String query = groupSearch.getText() == null
                ? "" : groupSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) {
            searchMatches = List.of();
            searchMatchIndex = -1;
            matchStatus.setText(" ");
            previousMatch.setEnabled(false);
            nextMatch.setEnabled(false);
            return;
        }
        List<ViewableGroup<?>> matches = new ArrayList<>();
        java.util.Enumeration<?> nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot())
                .breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            ViewableGroup<?> group = getViewableGroup(node);
            String name = group == null ? "" : String.valueOf(group.getDisplayName());
            if (name.toLowerCase(java.util.Locale.ROOT).contains(query)) matches.add(group);
        }
        searchMatches = List.copyOf(matches);
        int selected = searchMatches.indexOf(selectedGroup());
        searchMatchIndex = selected >= 0 ? selected : searchMatches.isEmpty() ? -1 : 0;
        updateSearchSelection();
    }

    private void moveSearchMatch(int delta) {
        if (searchMatches.isEmpty()) return;
        searchMatchIndex = Math.floorMod(searchMatchIndex + delta, searchMatches.size());
        updateSearchSelection();
    }

    private void updateSearchSelection() {
        boolean found = searchMatchIndex >= 0 && searchMatchIndex < searchMatches.size();
        previousMatch.setEnabled(searchMatches.size() > 1);
        nextMatch.setEnabled(searchMatches.size() > 1);
        matchStatus.setText(found
                ? (searchMatchIndex + 1) + " of " + searchMatches.size()
                : "No matching groups");
        if (found) selectGroup(searchMatches.get(searchMatchIndex), false);
    }

    private void expandInitiallyVisibleNodes() {
        java.util.Enumeration<?> nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot())
                .breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            ViewableGroup<?> group = getViewableGroup(node);
            if (group != null && group.initiallyExpanded()) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
    }

    private void showSelectedGroup() {
        ViewableGroup<?> selected = selectedGroup();
        if (selected != null && showGroupHandler != null) showGroupHandler.accept(selected);
    }

    private void setDetailsExpanded(boolean expanded) {
        detailsExpanded = expanded && selectedGroup() != null;
        detailsButton.setSelected(detailsExpanded);
        detailsButton.setText(detailsExpanded
                ? "Hide group fields" : "Show group fields");
        if (!detailsExpanded) {
            outlineAndDetails.setBottomComponent(null);
            outlineAndDetails.setDividerSize(0);
            revalidate();
            repaint();
            return;
        }
        showDetails(selectedGroup());
        outlineAndDetails.setBottomComponent(detailsScrollPane);
        outlineAndDetails.setDividerSize(8);
        SwingUtilities.invokeLater(() -> outlineAndDetails.setDividerLocation(0.7));
        revalidate();
        repaint();
    }

    private void showDetails(ViewableGroup<?> group) {
        if (group == null || group == detailsGroup && detailsCard != null) return;
        detailsGroup = group;
        detailsHost.removeAll();
        detailsCard = new Card(group, null, renderContext, false, true);
        detailsHost.add(detailsCard, BorderLayout.NORTH);
        detailsHost.revalidate();
        detailsHost.repaint();
    }

    public ViewableGroup<?> getRootGroup() { return rootGroup; }
    public JTree getTree() { return tree; }
    public JScrollPane getTreeScrollPane() { return treeScrollPane; }

    public void setShowGroupHandler(Consumer<ViewableGroup<?>> handler) {
        showGroupHandler = handler;
    }

    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        selectionHandler = handler;
    }

    /** Enables ordinary modifier-key tree selection. The lead node still controls
     * inspection (members and group fields); the complete set belongs to batch actions. */
    public void setMultipleSelectionEnabled(boolean enabled) {
        tree.getSelectionModel().setSelectionMode(enabled
                ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                : TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    public void setSelectionSetHandler(
            Consumer<List<ViewableGroup<?>>> handler) {
        selectionSetHandler = handler == null ? ignored -> {} : handler;
    }

    /** Selected groups in tree order, independent of the order modifier-clicks arrived. */
    public List<ViewableGroup<?>> selectedGroups() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) return List.of();
        List<TreePath> ordered = new ArrayList<>(List.of(paths));
        ordered.sort(Comparator.comparingInt(path -> {
            int row = tree.getRowForPath(path);
            return row < 0 ? Integer.MAX_VALUE : row;
        }));
        Set<ViewableGroup<?>> seen = Collections.newSetFromMap(
                new IdentityHashMap<>());
        List<ViewableGroup<?>> result = new ArrayList<>();
        for (TreePath path : ordered) {
            Object node = path.getLastPathComponent();
            ViewableGroup<?> group = node instanceof DefaultMutableTreeNode treeNode
                    ? getViewableGroup(treeNode) : null;
            if (group != null && seen.add(group)) result.add(group);
        }
        return List.copyOf(result);
    }

    /** Keep the explicit action only when selection does not already reveal members. */
    public void setShowButtonVisible(boolean visible) {
        if (visible && showButton.getParent() == null) {
            controls.add(showButton);
        } else if (!visible && showButton.getParent() == controls) {
            controls.remove(showButton);
        }
        controls.setVisible(controls.getComponentCount() > 0);
        controls.revalidate();
        controls.repaint();
    }

    public JButton addControl(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        int index = showButton.getParent() == controls
                ? Math.max(0, controls.getComponentCount() - 1)
                : controls.getComponentCount();
        controls.add(button, index);
        controls.setVisible(true);
        controls.revalidate();
        controls.repaint();
        return button;
    }

    public void setStatusText(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    public ViewableGroup<?> selectedGroup() {
        Object selected = tree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node
                ? getViewableGroup(node) : null;
    }

    public ViewableGroup<?> getViewableGroup(DefaultMutableTreeNode node) {
        return node != null && node.getUserObject() instanceof GroupNode groupNode
                ? groupNode.group() : null;
    }

    /** Select and optionally show a group by identity. */
    public boolean selectGroup(ViewableGroup<?> target, boolean show) {
        TreePath path = pathFor(target);
        if (path == null) return false;
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        if (show && showGroupHandler != null) showGroupHandler.accept(target);
        return true;
    }

    /** Programmatic counterpart of the JTree disclosure arrow. */
    public boolean setGroupExpanded(ViewableGroup<?> group, boolean expanded) {
        TreePath path = pathFor(group);
        if (path == null) return false;
        if (expanded) tree.expandPath(path); else tree.collapsePath(path);
        return true;
    }

    public boolean isGroupExpanded(ViewableGroup<?> group) {
        TreePath path = pathFor(group);
        return path != null && tree.isExpanded(path);
    }

    /** Select a group and show or hide the one shared details area. */
    public boolean setGroupDetailsExpanded(ViewableGroup<?> group, boolean expanded) {
        if (!selectGroup(group, false)) return false;
        setDetailsExpanded(expanded);
        return true;
    }

    public boolean isGroupDetailsExpanded(ViewableGroup<?> group) {
        return detailsExpanded && detailsGroup == group;
    }

    // Test seam: proves that the details body is the ordinary ObjectView card.
    JComponent groupDetailsComponent(ViewableGroup<?> group) {
        return detailsExpanded && detailsGroup == group ? detailsCard : null;
    }

    // Test seam: the details card must not fork rendering/navigation policy.
    RenderContext detailsRenderContext() { return renderContext; }

    private TreePath pathFor(ViewableGroup<?> target) {
        if (target == null) return null;
        java.util.Enumeration<?> nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot())
                .breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (getViewableGroup(node) == target) return new TreePath(node.getPath());
        }
        return null;
    }
}
