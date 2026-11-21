package objectview.render;

import objectview.group.GroupNode;
import objectview.group.ViewableGroup;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
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
    private final JLabel status;
    private Consumer<ViewableGroup<?>> showGroupHandler;
    private Consumer<ViewableGroup<?>> selectionHandler;
    private ViewableGroup<?> detailsGroup;
    private Card detailsCard;
    private boolean detailsExpanded;

    public GroupTreeView(ViewableGroup<?> rootGroup) {
        this(rootGroup, new RenderContext());
    }

    public GroupTreeView(ViewableGroup<?> rootGroup, RenderContext renderContext) {
        this.rootGroup = java.util.Objects.requireNonNull(rootGroup, "rootGroup");
        this.renderContext = java.util.Objects.requireNonNull(
                renderContext, "renderContext");
        setLayout(new BorderLayout(4, 4));

        tree = new JTree(buildNode(rootGroup));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> {
            ViewableGroup<?> selected = selectedGroup();
            if (detailsExpanded) showDetails(selected);
            if (selectionHandler != null) selectionHandler.accept(selected);
        });
        tree.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ENTER"), "showGroup");
        tree.getActionMap().put("showGroup", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                showSelectedGroup();
            }
        });
        tree.expandRow(0);
        tree.setSelectionRow(0);
        treeScrollPane = new JScrollPane(tree);

        outlineAndDetails = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, treeScrollPane, null);
        outlineAndDetails.setResizeWeight(0.7);
        outlineAndDetails.setDividerSize(0);
        outlineAndDetails.setBorder(null);

        detailsButton = new JToggleButton("Show fields");
        detailsButton.setToolTipText(
                "Show or hide the selected group's ordinary ObjectView fields");
        detailsButton.addActionListener(event ->
                setDetailsExpanded(detailsButton.isSelected()));

        showButton = new JButton("Show instances");
        showButton.addActionListener(event -> showSelectedGroup());
        controls = new JPanel(new GridLayout(0, 3, 4, 4));
        controls.add(detailsButton);
        controls.add(showButton);

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(status, BorderLayout.NORTH);
        add(outlineAndDetails, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);
    }

    private static DefaultMutableTreeNode buildNode(ViewableGroup<?> group) {
        return buildNode(group, java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>()));
    }

    // A persisted/adapted group graph may carry a back-edge. Show a revisited node as a
    // leaf rather than recursing forever.
    private static DefaultMutableTreeNode buildNode(
            ViewableGroup<?> group, java.util.Set<ViewableGroup<?>> visited) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new GroupNode(group));
        if (visited.add(group)) {
            for (ViewableGroup<?> child : group.getChildren()) {
                node.add(buildNode(child, visited));
            }
        }
        return node;
    }

    private void showSelectedGroup() {
        ViewableGroup<?> selected = selectedGroup();
        if (selected != null && showGroupHandler != null) showGroupHandler.accept(selected);
    }

    private void setDetailsExpanded(boolean expanded) {
        detailsExpanded = expanded && selectedGroup() != null;
        detailsButton.setSelected(detailsExpanded);
        detailsButton.setText(detailsExpanded ? "Hide fields" : "Show fields");
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
