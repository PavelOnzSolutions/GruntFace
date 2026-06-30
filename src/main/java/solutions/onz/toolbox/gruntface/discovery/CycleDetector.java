package solutions.onz.toolbox.gruntface.discovery;

import java.util.*;

public final class CycleDetector {

    private CycleDetector() {}

    public static <T> List<List<T>> findCycles(Map<T, List<T>> edges) {
        State<T> s = new State<>(edges);
        for (T node : edges.keySet()) {
            if (!s.index.containsKey(node)) strongConnect(node, s);
        }
        List<List<T>> cycles = new ArrayList<>();
        for (List<T> scc : s.sccs) {
            if (scc.size() > 1) cycles.add(scc);
            else {
                T only = scc.get(0);
                if (edges.getOrDefault(only, List.of()).contains(only)) cycles.add(scc);
            }
        }
        return cycles;
    }

    private static <T> void strongConnect(T v, State<T> s) {
        s.index.put(v, s.counter);
        s.lowlink.put(v, s.counter);
        s.counter++;
        s.stack.push(v);
        s.onStack.add(v);

        for (T w : s.edges.getOrDefault(v, List.of())) {
            if (!s.index.containsKey(w)) {
                strongConnect(w, s);
                s.lowlink.put(v, Math.min(s.lowlink.get(v), s.lowlink.get(w)));
            } else if (s.onStack.contains(w)) {
                s.lowlink.put(v, Math.min(s.lowlink.get(v), s.index.get(w)));
            }
        }

        if (s.lowlink.get(v).equals(s.index.get(v))) {
            List<T> scc = new ArrayList<>();
            T w;
            do {
                w = s.stack.pop();
                s.onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            s.sccs.add(scc);
        }
    }

    private static class State<T> {
        final Map<T, List<T>> edges;
        final Map<T, Integer> index = new HashMap<>();
        final Map<T, Integer> lowlink = new HashMap<>();
        final Deque<T> stack = new ArrayDeque<>();
        final Set<T> onStack = new HashSet<>();
        final List<List<T>> sccs = new ArrayList<>();
        int counter = 0;
        State(Map<T, List<T>> edges) { this.edges = edges; }
    }
}
