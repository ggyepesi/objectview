package objectview.demo;

import objectview.Viewable;
import objectview.render.Card;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

public class CardFrame {
    private static final Map<Viewable, WeakReference<JFrame>> openFrames = new IdentityHashMap<>();

    public CardFrame(Viewable q, ViewConfig cfg) {
        this(q.getName(), q, cfg);
    }

    public CardFrame(String title, Viewable viewable,
                     ViewConfig config) {
        if (viewable == null) {
            return;
        }
        JFrame existing = getExistingFrame(viewable);

        if (existing != null) {
            existing.setVisible(true);
            existing.toFront();
            existing.requestFocus();
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
        Card panel = new Card(viewable, config, true, true);

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