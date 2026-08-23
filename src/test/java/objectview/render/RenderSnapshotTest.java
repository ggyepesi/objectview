package objectview.render;

import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSnapshotTest {

    @Test void retriesACollectionChangedDuringTheCopy() {
        AbstractCollection<String> changing = new AbstractCollection<>() {
            int attempts;
            @Override public Iterator<String> iterator() { return List.of("one", "two").iterator(); }
            @Override public int size() { return 2; }
            @Override public Object[] toArray() {
                if (attempts++ == 0) throw new ConcurrentModificationException();
                return new Object[] {"one", "two"};
            }
        };

        assertEquals(List.of("one", "two"), RenderSnapshot.collection(changing));
    }

    @Test void abandonsAnUnstableValueWithoutThrowingOnTheEdt() {
        AbstractCollection<String> changing = new AbstractCollection<>() {
            @Override public Iterator<String> iterator() { throw new ConcurrentModificationException(); }
            @Override public int size() { return 1; }
            @Override public Object[] toArray() { throw new ConcurrentModificationException(); }
        };

        assertTrue(RenderSnapshot.collection(changing).isEmpty());
    }
}
