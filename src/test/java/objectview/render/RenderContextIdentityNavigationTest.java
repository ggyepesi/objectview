package objectview.render;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** References navigate by complete logical identity, not Java object identity or QID. */
class RenderContextIdentityNavigationTest {

    @Test void aReferenceShellFindsTheMatchingTypedTopLevelInstance() {
        Item person = new Item("Person", "Q1", "Person one");
        Item position = new Item("Position", "Q1", "Position one");
        Item personReference = new Item("Person", "Q1", "Person reference");
        Item otherTypeReference = new Item("Office", "Q1", "Office reference");
        RenderContext context = new RenderContext(List.of(person, position));
        AtomicReference<Object> resolved = new AtomicReference<>();
        AtomicInteger revealed = new AtomicInteger();
        JPanel host = new JPanel();
        JPanel card = new JPanel();
        host.add(card); // a detached component is deliberately treated as stale
        context.registerTopLevelRevealer(person, revealed::incrementAndGet);
        context.addTopLevelResolver(candidate -> {
            resolved.set(candidate);
            return candidate == person ? card : null;
        });

        assertTrue(context.isTopLevel(personReference));
        assertFalse(context.isTopLevel(otherTypeReference),
                "the same identifier in another logical type is not the same object");
        assertTrue(context.focusTopLevel(personReference));
        assertSame(person, resolved.get(),
                "the resolver receives the actual top-level instance, not the shell");
        assertEquals(1, revealed.get());
    }

    private static final class Item extends ViewableAdapter {
        private final String type;
        private final String id;
        private final String label;

        private Item(String type, String id, String label) {
            this.type = type;
            this.id = id;
            this.label = label;
        }

        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
        @Override public String typeName() { return type; }
        @Override public String identityTypeName() { return type; }
    }
}
