package solutions.onz.toolbox.gruntface.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CycleDetectorTest {

    @Test
    void findsNoCyclesInDag() {
        Map<String, List<String>> edges = Map.of(
            "a", List.of("b"),
            "b", List.of("c"),
            "c", List.of()
        );
        List<List<String>> cycles = CycleDetector.findCycles(edges);
        assertTrue(cycles.isEmpty());
    }

    @Test
    void findsSimpleTwoNodeCycle() {
        Map<String, List<String>> edges = Map.of(
            "a", List.of("b"),
            "b", List.of("a")
        );
        List<List<String>> cycles = CycleDetector.findCycles(edges);
        assertEquals(1, cycles.size());
        assertEquals(Set.of("a", "b"), Set.copyOf(cycles.get(0)));
    }

    @Test
    void findsThreeNodeCycle() {
        Map<String, List<String>> edges = Map.of(
            "a", List.of("b"),
            "b", List.of("c"),
            "c", List.of("a"),
            "d", List.of("a")
        );
        List<List<String>> cycles = CycleDetector.findCycles(edges);
        assertEquals(1, cycles.size());
        assertEquals(Set.of("a", "b", "c"), Set.copyOf(cycles.get(0)));
    }
}
