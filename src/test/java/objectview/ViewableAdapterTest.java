package objectview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewableAdapterTest {

    @Test
    void collectionContainingOnlyBlankValuesIsEmptyForRendering() {
        assertFalse(ViewableAdapter.isValidQuizValue(List.of("")));
        assertFalse(ViewableAdapter.isValidQuizValue(List.of(" ", "\t")));
        assertFalse(ViewableAdapter.isValidQuizValue(List.of(List.of(""))));
        assertTrue(ViewableAdapter.isValidQuizValue(List.of("", "topic")));
    }

    @Test
    void mapContainingOnlyBlankValuesIsEmptyForRendering() {
        assertFalse(ViewableAdapter.isValidQuizValue(Map.of("topic", "")));
        assertTrue(ViewableAdapter.isValidQuizValue(Map.of("topic", "physics")));
    }
}
