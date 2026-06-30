package solutions.onz.toolbox.gruntface.ui.graph;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVG → JavaFX Node loader for the Azure icon subset.
 *
 * <p>Handles: {@code <path>}, {@code <circle>}, {@code <rect>} (with {@code rx}),
 * {@code <polygon>}, {@code <g>} (descent), {@code <linearGradient>},
 * {@code <radialGradient>} (including {@code gradientTransform}),
 * {@code <stop>} (offset, stop-color, stop-opacity), fill colors (hex, named,
 * {@code url(#id)}, {@code none}), and per-shape {@code opacity}.</p>
 *
 * <p>Unsupported / silently ignored: {@code <clipPath>}, {@code <filter>},
 * {@code <mask>}, {@code <pattern>}, {@code <use>}, {@code <text>},
 * {@code stroke} styling, {@code transform} on individual shapes.</p>
 */
public final class SvgLoader {

    // ── Fallback color used when a gradient or fill cannot be resolved ──────
    private static final Color FALLBACK = Color.web("#0078d4");

    // ── Pre-compiled patterns ────────────────────────────────────────────────

    private static final Pattern VIEWBOX = Pattern.compile(
        "viewBox\\s*=\\s*\"([^\"]+)\"");

    /** Matches the full content between <defs> … </defs> (non-greedy). */
    private static final Pattern DEFS_BLOCK = Pattern.compile(
        "<defs[^>]*>(.*?)</defs>", Pattern.DOTALL);

    private static final Pattern LINEAR_GRADIENT = Pattern.compile(
        "<linearGradient\\b([^>]*)>(.*?)</linearGradient>", Pattern.DOTALL);

    private static final Pattern RADIAL_GRADIENT = Pattern.compile(
        "<radialGradient\\b([^>]*)>(.*?)</radialGradient>", Pattern.DOTALL);

    private static final Pattern STOP_ELEM = Pattern.compile(
        "<stop\\b([^/]*)/?>", Pattern.DOTALL);

    /** Matches a named attribute in an element's attribute string. */
    private static final Pattern ATTR = Pattern.compile(
        "\\b([\\w:-]+)\\s*=\\s*\"([^\"]*)\"");

    /** Matches self-closing or open <path …/> or <path …></path> elements. */
    private static final Pattern PATH_ELEM = Pattern.compile(
        "<path\\b([^>]*?)(?:/>|>(?:.*?)</path>)", Pattern.DOTALL);

    private static final Pattern CIRCLE_ELEM = Pattern.compile(
        "<circle\\b([^>]*?)/>", Pattern.DOTALL);

    private static final Pattern RECT_ELEM = Pattern.compile(
        "<rect\\b([^>]*?)/>", Pattern.DOTALL);

    private static final Pattern POLYGON_ELEM = Pattern.compile(
        "<polygon\\b([^>]*?)/>", Pattern.DOTALL);

    /**
     * Matches the body of a {@code <g>} element, capturing nested content.
     * We peel one level at a time so we don't need a full recursive parser.
     */
    private static final Pattern G_OPEN  = Pattern.compile("<g\\b[^>]*>");
    private static final Pattern G_CLOSE = Pattern.compile("</g>");

    // ── gradientTransform sub-patterns ──────────────────────────────────────
    private static final Pattern GT_TRANSLATE = Pattern.compile(
        "translate\\s*\\(\\s*([\\d.+-]+)\\s*,\\s*([\\d.+-]+)\\s*\\)");
    private static final Pattern GT_SCALE_TWO = Pattern.compile(
        "scale\\s*\\(\\s*([\\d.+-]+)\\s*,\\s*([\\d.+-]+)\\s*\\)");
    private static final Pattern GT_SCALE_ONE = Pattern.compile(
        "scale\\s*\\(\\s*([\\d.+-]+)\\s*\\)");

    // ── url(#id) reference ──────────────────────────────────────────────────
    private static final Pattern URL_REF = Pattern.compile(
        "^url\\(#([^)]+)\\)$", Pattern.CASE_INSENSITIVE);

    // ─────────────────────────────────────────────────────────────────────────

    private SvgLoader() {}

    /**
     * Returns a Group sized to approximately {@code targetSize × targetSize},
     * or {@code null} if loading failed or the SVG contained no renderable shapes.
     */
    public static Node loadScaled(String resourcePath, double targetSize) {
        try (InputStream in = SvgLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            String body = new String(in.readAllBytes());
            return buildGroup(body, targetSize);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Main builder ─────────────────────────────────────────────────────────

    private static Node buildGroup(String body, double targetSize) {
        // 1. Parse viewBox dimensions
        double vbW = 24, vbH = 24;
        Matcher vb = VIEWBOX.matcher(body);
        if (vb.find()) {
            String[] parts = vb.group(1).trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    vbW = Double.parseDouble(parts[2]);
                    vbH = Double.parseDouble(parts[3]);
                } catch (NumberFormatException ignore) { /* use defaults */ }
            }
        }

        // 2. Parse gradient defs
        Map<String, Paint> gradients = new HashMap<>();
        Matcher defsM = DEFS_BLOCK.matcher(body);
        if (defsM.find()) {
            String defsContent = defsM.group(1);
            parseLinearGradients(defsContent, gradients);
            parseRadialGradients(defsContent, gradients);
        }

        // 3. Build shapes from body (strip defs first to avoid matching elements inside defs)
        String shapeBody = DEFS_BLOCK.matcher(body).replaceAll("");
        // Also strip <title>, <desc>, comments
        shapeBody = shapeBody.replaceAll("<!--.*?-->", "")
                             .replaceAll("<title[^>]*>.*?</title>", "")
                             .replaceAll("<desc[^>]*>.*?</desc>", "");

        List<Node> shapes = new ArrayList<>();
        parseShapes(shapeBody, gradients, shapes);

        if (shapes.isEmpty()) return null;

        Group group = new Group();
        group.getChildren().addAll(shapes);
        double scale = targetSize / Math.max(vbW, vbH);
        group.getTransforms().add(new Scale(scale, scale));
        return group;
    }

    // ── Gradient parsers ─────────────────────────────────────────────────────

    private static void parseLinearGradients(String defs, Map<String, Paint> out) {
        Matcher m = LINEAR_GRADIENT.matcher(defs);
        while (m.find()) {
            String attrs  = m.group(1);
            String body   = m.group(2);
            Map<String, String> a = parseAttrs(attrs);
            String id = a.get("id");
            if (id == null || id.isBlank()) continue;

            // Inherit from xlink:href if present (not common in these files, skip)
            double x1 = parseDoubleAttr(a, "x1", 0);
            double y1 = parseDoubleAttr(a, "y1", 0);
            double x2 = parseDoubleAttr(a, "x2", 1);
            double y2 = parseDoubleAttr(a, "y2", 0);
            List<Stop> stops = parseStops(body);
            if (stops.isEmpty()) {
                out.put(id, FALLBACK);
                continue;
            }
            try {
                LinearGradient lg = new LinearGradient(x1, y1, x2, y2,
                    false, CycleMethod.NO_CYCLE, stops);
                out.put(id, lg);
            } catch (Exception e) {
                out.put(id, FALLBACK);
            }
        }
    }

    private static void parseRadialGradients(String defs, Map<String, Paint> out) {
        Matcher m = RADIAL_GRADIENT.matcher(defs);
        while (m.find()) {
            String attrs = m.group(1);
            String body  = m.group(2);
            Map<String, String> a = parseAttrs(attrs);
            String id = a.get("id");
            if (id == null || id.isBlank()) continue;

            double cx = parseDoubleAttr(a, "cx", 0.5);
            double cy = parseDoubleAttr(a, "cy", 0.5);
            double r  = parseDoubleAttr(a, "r",  0.5);
            // focal point defaults to center
            double fx = parseDoubleAttr(a, "fx", cx);
            double fy = parseDoubleAttr(a, "fy", cy);

            // Apply gradientTransform (translate + scale) to cx, cy, fx, fy, r
            String gt = a.get("gradientTransform");
            if (gt != null && !gt.isBlank()) {
                double[] transform = parseGradientTransform(gt);
                // transform = [tx, ty, sx, sy]
                double tx = transform[0], ty = transform[1];
                double sx = transform[2], sy = transform[3];
                cx = cx * sx + tx;
                cy = cy * sy + ty;
                fx = fx * sx + tx;
                fy = fy * sy + ty;
                r  = r * Math.max(sx, sy);   // approximate: use the larger scale
            }

            List<Stop> stops = parseStops(body);
            if (stops.isEmpty()) {
                out.put(id, FALLBACK);
                continue;
            }
            try {
                // focusAngle=0, focusDistance=0 (put focus at center), proportional=false
                RadialGradient rg = new RadialGradient(0, 0, cx, cy, r,
                    false, CycleMethod.NO_CYCLE, stops);
                out.put(id, rg);
            } catch (Exception e) {
                out.put(id, FALLBACK);
            }
        }
    }

    /**
     * Parses a gradientTransform attribute into [tx, ty, sx, sy].
     * Supports: {@code translate(x,y)}, {@code scale(s)}, {@code scale(sx,sy)},
     * and any combination in order.
     */
    private static double[] parseGradientTransform(String gt) {
        double tx = 0, ty = 0, sx = 1, sy = 1;
        // Try to find translate
        Matcher tMatcher = GT_TRANSLATE.matcher(gt);
        if (tMatcher.find()) {
            try {
                tx = Double.parseDouble(tMatcher.group(1));
                ty = Double.parseDouble(tMatcher.group(2));
            } catch (NumberFormatException ignore) { /* keep defaults */ }
        }
        // Try to find scale(sx, sy)
        Matcher s2 = GT_SCALE_TWO.matcher(gt);
        if (s2.find()) {
            try {
                sx = Double.parseDouble(s2.group(1));
                sy = Double.parseDouble(s2.group(2));
            } catch (NumberFormatException ignore) { /* keep defaults */ }
        } else {
            // Try scale(s)
            Matcher s1 = GT_SCALE_ONE.matcher(gt);
            if (s1.find()) {
                try {
                    sx = sy = Double.parseDouble(s1.group(1));
                } catch (NumberFormatException ignore) { /* keep defaults */ }
            }
        }
        return new double[]{tx, ty, sx, sy};
    }

    private static List<Stop> parseStops(String stopsBody) {
        List<Stop> stops = new ArrayList<>();
        Matcher m = STOP_ELEM.matcher(stopsBody);
        while (m.find()) {
            Map<String, String> a = parseAttrs(m.group(1));
            double offset = 0;
            String offsetStr = a.get("offset");
            if (offsetStr != null) {
                offsetStr = offsetStr.trim();
                try {
                    if (offsetStr.endsWith("%")) {
                        offset = Double.parseDouble(offsetStr.substring(0, offsetStr.length() - 1)) / 100.0;
                    } else {
                        offset = Double.parseDouble(offsetStr);
                    }
                    offset = Math.max(0, Math.min(1, offset));
                } catch (NumberFormatException ignore) { /* keep 0 */ }
            }
            Color color = Color.BLACK;
            String sc = a.get("stop-color");
            if (sc != null && !sc.isBlank()) {
                try { color = Color.web(sc); } catch (Exception ignore) { /* keep black */ }
            }
            double stopOpacity = 1.0;
            String so = a.get("stop-opacity");
            if (so != null && !so.isBlank()) {
                try { stopOpacity = Double.parseDouble(so.trim()); } catch (NumberFormatException ignore) { /* keep 1 */ }
            }
            if (stopOpacity != 1.0) {
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                                  Math.max(0, Math.min(1, stopOpacity)));
            }
            stops.add(new Stop(offset, color));
        }
        return stops;
    }

    /** Lightweight descriptor for a parsed SVG element, used for ordering. */
    private record ElemInfo(int start, String type, String attrStr, String innerBody) {}

    // ── Shape parsers ─────────────────────────────────────────────────────────

    /**
     * Parses all renderable shapes out of {@code body} in document order and
     * appends them to {@code out}. Recurses into {@code <g>} groups.
     */
    private static void parseShapes(String body, Map<String, Paint> gradients, List<Node> out) {
        // We walk through the body character by character to preserve drawing order
        // across the different element types. We keep track of element start positions
        // and process them in order of appearance.

        // Collect all element positions with their type and attribute string
        List<ElemInfo> elems = new ArrayList<>();

        // Match path
        Matcher pm = PATH_ELEM.matcher(body);
        while (pm.find()) {
            elems.add(new ElemInfo(pm.start(), "path", pm.group(1), null));
        }
        // Match circle
        Matcher cm = CIRCLE_ELEM.matcher(body);
        while (cm.find()) {
            elems.add(new ElemInfo(cm.start(), "circle", cm.group(1), null));
        }
        // Match rect
        Matcher rm = RECT_ELEM.matcher(body);
        while (rm.find()) {
            elems.add(new ElemInfo(rm.start(), "rect", rm.group(1), null));
        }
        // Match polygon
        Matcher polyM = POLYGON_ELEM.matcher(body);
        while (polyM.find()) {
            elems.add(new ElemInfo(polyM.start(), "polygon", polyM.group(1), null));
        }
        // Match <g> groups (one level deep) — extract their inner body
        extractGGroups(body, elems);

        // Sort by document position
        elems.sort((a1, b1) -> Integer.compare(a1.start(), b1.start()));

        // Render each element
        for (ElemInfo ei : elems) {
            switch (ei.type()) {
                case "path" -> {
                    SVGPath sp = buildPath(ei.attrStr(), gradients);
                    if (sp != null) out.add(sp);
                }
                case "circle" -> {
                    Circle c = buildCircle(ei.attrStr(), gradients);
                    if (c != null) out.add(c);
                }
                case "rect" -> {
                    Rectangle r = buildRect(ei.attrStr(), gradients);
                    if (r != null) out.add(r);
                }
                case "polygon" -> {
                    Polygon p = buildPolygon(ei.attrStr(), gradients);
                    if (p != null) out.add(p);
                }
                case "g" -> {
                    // Recurse into group content
                    if (ei.innerBody() != null) {
                        parseShapes(ei.innerBody(), gradients, out);
                    }
                }
                default -> { /* ignore */ }
            }
        }
    }

    /**
     * Extracts {@code <g>} group elements from {@code body}, storing their
     * inner content. Handles basic nesting by counting open/close tags.
     */
    private static void extractGGroups(String body,
                                        List<ElemInfo> elems) {
        // We need to find <g ...> ... </g> ranges while handling nesting.
        // Strategy: find all <g ...> and </g> positions, then pair them.
        List<int[]> gOpens  = new ArrayList<>(); // [start, endOfOpenTag]
        List<Integer> gCloses = new ArrayList<>();

        Matcher om = G_OPEN.matcher(body);
        while (om.find()) {
            gOpens.add(new int[]{om.start(), om.end()});
        }
        Matcher clm = G_CLOSE.matcher(body);
        while (clm.find()) {
            gCloses.add(clm.start());
        }

        // Pair using a stack — build an event list: +1 at each <g> start, -1 at each </g> start,
        // then walk to find outermost groups.
        record Event(int pos, int delta, int tagEnd) {}
        List<Event> events = new ArrayList<>();
        for (int[] go : gOpens)  events.add(new Event(go[0],  1, go[1]));
        for (int gc : gCloses)   events.add(new Event(gc,    -1, gc + 4));
        events.sort((a, b) -> Integer.compare(a.pos(), b.pos()));

        int currentDepth = 0;
        int outerStart = -1;
        int outerTagEnd = -1;

        for (Event ev : events) {
            if (ev.delta() == 1) {
                if (currentDepth == 0) {
                    outerStart  = ev.pos();
                    outerTagEnd = ev.tagEnd();
                }
                currentDepth++;
            } else {
                currentDepth--;
                if (currentDepth == 0 && outerStart >= 0) {
                    // Found an outer <g> ... </g>
                    String innerBody = body.substring(outerTagEnd, ev.pos());
                    elems.add(new ElemInfo(outerStart, "g", "", innerBody));
                    outerStart = -1;
                }
            }
        }
    }

    // ── Shape builders ────────────────────────────────────────────────────────

    private static SVGPath buildPath(String attrStr, Map<String, Paint> gradients) {
        Map<String, String> a = parseAttrs(attrStr);
        String d = a.get("d");
        if (d == null || d.isBlank()) return null;
        SVGPath sp = new SVGPath();
        sp.setContent(d);
        Paint fill = resolveFill(a.get("fill"), gradients, FALLBACK);
        sp.setFill(fill);
        sp.setStroke(null);
        applyOpacity(sp, a.get("opacity"));
        return sp;
    }

    private static Circle buildCircle(String attrStr, Map<String, Paint> gradients) {
        Map<String, String> a = parseAttrs(attrStr);
        double cx = parseDoubleAttr(a, "cx", 0);
        double cy = parseDoubleAttr(a, "cy", 0);
        double r  = parseDoubleAttr(a, "r",  0);
        if (r <= 0) return null;
        Circle c = new Circle(cx, cy, r);
        c.setFill(resolveFill(a.get("fill"), gradients, FALLBACK));
        c.setStroke(null);
        applyOpacity(c, a.get("opacity"));
        return c;
    }

    private static Rectangle buildRect(String attrStr, Map<String, Paint> gradients) {
        Map<String, String> a = parseAttrs(attrStr);
        double x  = parseDoubleAttr(a, "x",      0);
        double y  = parseDoubleAttr(a, "y",      0);
        double w  = parseDoubleAttr(a, "width",  0);
        double h  = parseDoubleAttr(a, "height", 0);
        if (w <= 0 || h <= 0) return null;
        Rectangle rect = new Rectangle(x, y, w, h);
        double rx = parseDoubleAttr(a, "rx", 0);
        double ry = parseDoubleAttr(a, "ry", rx);  // ry defaults to rx per SVG spec
        if (rx > 0) {
            rect.setArcWidth(rx * 2);
            rect.setArcHeight(ry * 2);
        }
        rect.setFill(resolveFill(a.get("fill"), gradients, FALLBACK));
        rect.setStroke(null);
        applyOpacity(rect, a.get("opacity"));
        return rect;
    }

    private static Polygon buildPolygon(String attrStr, Map<String, Paint> gradients) {
        Map<String, String> a = parseAttrs(attrStr);
        String pts = a.get("points");
        if (pts == null || pts.isBlank()) return null;
        String[] tokens = pts.trim().split("[\\s,]+");
        if (tokens.length < 4 || tokens.length % 2 != 0) return null;
        double[] coords = new double[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                coords[i] = Double.parseDouble(tokens[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        Polygon poly = new Polygon(coords);
        poly.setFill(resolveFill(a.get("fill"), gradients, FALLBACK));
        poly.setStroke(null);
        applyOpacity(poly, a.get("opacity"));
        return poly;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves a fill attribute value to a JavaFX {@link Paint}.
     *
     * @param fill      raw attribute value (may be null)
     * @param gradients gradient map built from {@code <defs>}
     * @param fallback  used when the value is absent (not "none")
     * @return a Paint, or {@code null} if fill is "none"
     */
    private static Paint resolveFill(String fill, Map<String, Paint> gradients, Paint fallback) {
        if (fill == null || fill.isBlank()) return fallback;
        fill = fill.trim();
        if ("none".equalsIgnoreCase(fill)) return null;
        // url(#id) reference
        Matcher urlM = URL_REF.matcher(fill);
        if (urlM.matches()) {
            String refId = urlM.group(1);
            Paint resolved = gradients.get(refId);
            return resolved != null ? resolved : fallback;
        }
        // Solid color
        try {
            return Color.web(fill);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Parses all {@code name="value"} attribute pairs from an attribute string. */
    private static Map<String, String> parseAttrs(String attrStr) {
        Map<String, String> map = new HashMap<>();
        if (attrStr == null) return map;
        Matcher m = ATTR.matcher(attrStr);
        while (m.find()) {
            // Normalise to local name (strip namespace prefix like xlink:)
            String key = m.group(1);
            int colon = key.lastIndexOf(':');
            if (colon >= 0) key = key.substring(colon + 1);
            map.put(key, m.group(2));
        }
        return map;
    }

    private static double parseDoubleAttr(Map<String, String> attrs, String key, double def) {
        String v = attrs.get(key);
        if (v == null || v.isBlank()) return def;
        v = v.trim();
        // Strip trailing % (not expected in geometry attrs, but be safe)
        if (v.endsWith("%")) v = v.substring(0, v.length() - 1);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void applyOpacity(Node node, String opacityStr) {
        if (opacityStr == null || opacityStr.isBlank()) return;
        try {
            double opacity = Double.parseDouble(opacityStr.trim());
            node.setOpacity(Math.max(0, Math.min(1, opacity)));
        } catch (NumberFormatException ignore) { /* keep default 1.0 */ }
    }
}
