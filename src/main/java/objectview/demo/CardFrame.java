package objectview.demo;

import objectview.Viewable;
import objectview.render.Card;
import objectview.render.RenderContext;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CardFrame {
    private static final Map<Viewable, WeakReference<JFrame>> openFrames = new IdentityHashMap<>();

    public CardFrame(Viewable q, ViewConfig cfg) {
        this(titleOf(q), q, cfg);
    }

    public CardFrame(Viewable q, ViewConfig cfg, RenderContext context) {
        this(titleOf(q), q, cfg, context);
    }

    /**
     * Title for a detached object window. The window replaces the card's own heading,
     * so it must identify both the instance and the logical class that is otherwise
     * visible from the section containing its top-level card.
     */
    public static String titleOf(Viewable viewable) {
        if (viewable == null) {
            return "";
        }

        String name = clean(viewable.getDisplayName());
        List<String> classNames = new ArrayList<>();

        if (viewable.directClassNames() != null) {
            for (String className : viewable.directClassNames()) {
                String cleaned = clean(className);
                if (!cleaned.isEmpty() && !classNames.contains(cleaned)) {
                    classNames.add(cleaned);
                }
            }
        }

        if (classNames.isEmpty()) {
            String typeName = clean(viewable.typeName());
            if (!typeName.isEmpty()) {
                classNames.add(typeName);
            }
        }

        Collections.sort(classNames);
        String classes = String.join(", ", classNames);

        if (classes.isEmpty()) {
            return name;
        }
        if (name.isEmpty()) {
            return "Class: " + classes;
        }
        return name + " — Class: " + classes;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public CardFrame(String title, Viewable viewable,
                     ViewConfig config) {
        this(title, viewable, config, new RenderContext());
    }

    public CardFrame(String title, Viewable viewable,
                     ViewConfig config, RenderContext context) {
        if (viewable == null) {
            return;
        }
        JFrame existing = getExistingFrame(viewable);

        if (existing != null) {
            objectview.utils.swing.SwingWindowActivation.showAndFocus(existing);
            return;
        }

        JFrame frame = new JFrame(title);

        openFrames.put(viewable, new WeakReference<>(frame));

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openFrames.remove(viewable);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                openFrames.remove(viewable);
            }
        });
        // The frame's own title bar already shows the name, so suppress the
        // card's title header to avoid showing the same name twice.
        Card panel = new Card(viewable, config,
                context == null ? new RenderContext() : context, true, true);

        JScrollPane scroll = new JScrollPane(panel);
        frame.add(scroll);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setVisible(true);
    }

    private static JFrame getExistingFrame(Viewable viewable) {
        WeakReference<JFrame> ref = openFrames.get(viewable);

        if (ref == null) {
            return null;
        }

        JFrame frame = ref.get();

        if (frame == null || !frame.isDisplayable()) {
            openFrames.remove(viewable);
            return null;
        }

        return frame;
    }
}
