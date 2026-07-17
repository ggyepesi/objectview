package objectview.demo;

import objectview.ViewableAdapter;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Demo of {@link MultiView}: a Constellations view and a Stars view
 * sharing one render context. Constellation cards show their stars and
 * neighbours as chips; star cards show their constellation as a chip.
 * Click any chip to navigate (scroll to + flash) the target's card in its
 * own view. The Card equivalent of the old hand-built TypePanelDemo.
 */
public class MultiViewDemo {

    public static final class Constellation extends ViewableAdapter {
        public String name = "";
        public String abbreviation = "";
        public List<Constellation> neighbours = new ArrayList<>();
        public List<Star> stars = new ArrayList<>();

        public Constellation() {}

        public Constellation(String name, String abbreviation) {
            this.name = name;
            this.abbreviation = abbreviation;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String toString() { return name; }
    }

    public static final class Star extends ViewableAdapter {
        public String name = "";
        public double magnitude = 0.0;
        public Constellation constellation;

        public Star() {}

        public Star(String name, double magnitude) {
            this.name = name;
            this.magnitude = magnitude;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String toString() { return name; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MultiViewDemo::launch);
    }

    private static void launch() {
        Constellation orion  = new Constellation("Orion", "Ori");
        Constellation taurus = new Constellation("Taurus", "Tau");
        Constellation gemini = new Constellation("Gemini", "Gem");

        link(orion, taurus, gemini);
        link(taurus, orion, gemini);
        link(gemini, orion, taurus);

        Star rigel      = star("Rigel", 0.13, orion);
        Star betelgeuse = star("Betelgeuse", 0.42, orion);
        Star aldebaran  = star("Aldebaran", 0.85, taurus);
        Star pollux     = star("Pollux", 1.14, gemini);
        Star castor     = star("Castor", 1.58, gemini);

        orion.stars.addAll(List.of(rigel, betelgeuse));
        taurus.stars.add(aldebaran);
        gemini.stars.addAll(List.of(pollux, castor));

        List<Constellation> constellations = List.of(orion, taurus, gemini);

        Set<Star> stars = new LinkedHashSet<>(
                List.of(rigel, betelgeuse, aldebaran, pollux, castor));

        MultiView mv = new MultiView();
        mv.addSection("Constellations", Constellation.class, constellations);
        mv.addSection("Stars", Star.class, new ArrayList<>(stars));
        mv.build(1);

        JFrame frame = new JFrame(
                "MultiView demo — click a chip to navigate");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(mv, BorderLayout.CENTER);
        frame.setSize(1200, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void link(Constellation a, Constellation... neighbours) {
        a.neighbours.addAll(List.of(neighbours));
    }

    private static Star star(String name, double mag, Constellation c) {
        Star s = new Star(name, mag);
        s.constellation = c;
        return s;
    }
}
