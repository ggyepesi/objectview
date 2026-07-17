package objectview.viewconfig;

import objectview.Viewable;
import objectview.render.Card;

import javax.swing.*;
import java.awt.*;

public class ViewConfigEditorFrame extends JFrame {

    private final Viewable sample;

    private final JPanel editorHolder = new JPanel(new BorderLayout());
    private final JPanel previewPanel = new JPanel(new BorderLayout());

    private ViewConfigEditor editor;

    public ViewConfigEditorFrame(Viewable sample,
                                 ViewConfig initialConfig) {
        super("View Config Editor - " + sample.getClass().getSimpleName());

        this.sample = sample;

        ViewConfig config = initialConfig == null
                ? ViewConfig.of(sample.getClass())
                : initialConfig.copy();

        this.editor = createEditor(config);
        editorHolder.add(editor, BorderLayout.CENTER);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                editorHolder,
                previewPanel
        );
        split.setResizeWeight(0.40);

        add(split, BorderLayout.CENTER);
        add(buttonPanel(), BorderLayout.SOUTH);

        refreshPreview();

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private ViewConfigEditor createEditor(ViewConfig config) {
        ViewConfigEditor e = new ViewConfigEditor(config);
        e.setChangeListener(this::refreshPreview);
        return e;
    }

    private JPanel buttonPanel() {
        JButton saveJson = new JButton("Save JSON");
        saveJson.addActionListener(e -> saveJson());

        JButton reset = new JButton("Reset to Defaults");
        reset.addActionListener(e -> resetToDefaults());

        JButton refresh = new JButton("Refresh Preview");
        refresh.addActionListener(e -> refreshPreview());

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(saveJson);
        p.add(reset);
        p.add(refresh);
        p.add(close);
        return p;
    }

    private void saveJson() {
        ViewConfig config = editor.getConfig();
        config.clearCache();

        java.io.File file =
                ViewConfigJsonIO.defaultFileFor(sample.getClass());

        ViewConfigJsonIO.save(file, config);

        JOptionPane.showMessageDialog(
                this,
                "Saved:\n" + file.getAbsolutePath(),
                "View config saved",
                JOptionPane.INFORMATION_MESSAGE
                                     );
    }

    private void resetToDefaults() {
        ViewConfig defaultConfig =
                ViewConfig.of(sample.getClass());

        editorHolder.removeAll();

        editor = createEditor(defaultConfig);
        editorHolder.add(editor, BorderLayout.CENTER);

        editorHolder.revalidate();
        editorHolder.repaint();

        refreshPreview();
    }

    private void refreshPreview() {
        ViewConfig config = editor.getConfig();
        config.clearCache();

        previewPanel.removeAll();

        Card panel = new Card(sample, config, true);
        previewPanel.add(new JScrollPane(panel), BorderLayout.CENTER);

        previewPanel.revalidate();
        previewPanel.repaint();
    }

    public ViewConfig getConfig() {
        return editor.getConfig();
    }
}