package pro.sketchware.activities.preview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, safe runtime renderer for the XML preview. It intentionally does not
 * execute project Java/Kotlin code or inflate arbitrary app resources.
 */
public final class RuntimeXmlPreview {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private RuntimeXmlPreview() {
    }

    public static ParseResult parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return ParseResult.error("XML is empty", 1, 1);
        }
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            ArrayList<Node> roots = new ArrayList<>();
            ArrayList<Node> stack = new ArrayList<>();
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    Node node = new Node(parser.getName());
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String prefix = parser.getAttributePrefix(i);
                        String name = parser.getAttributeName(i);
                        String key = prefix == null || prefix.isEmpty() ? name : prefix + ":" + name;
                        node.attributes.put(key, parser.getAttributeValue(i));
                    }
                    if (stack.isEmpty()) {
                        if (!roots.isEmpty()) {
                            return ParseResult.error("XML must contain one root view", parser.getLineNumber(), parser.getColumnNumber());
                        }
                        roots.add(node);
                    } else {
                        stack.get(stack.size() - 1).children.add(node);
                    }
                    stack.add(node);
                } else if (event == XmlPullParser.END_TAG && !stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            }
            if (roots.isEmpty()) return ParseResult.error("No root view found", 1, 1);
            return ParseResult.success(roots.get(0));
        } catch (XmlPullParserException e) {
            int line = e.getLineNumber() > 0 ? e.getLineNumber() : 1;
            int column = e.getColumnNumber() > 0 ? e.getColumnNumber() : 1;
            return ParseResult.error(cleanMessage(e.getMessage()), line, column);
        } catch (IOException | RuntimeException e) {
            return ParseResult.error(cleanMessage(e.getMessage()), 1, 1);
        }
    }

    public static RenderResult render(Context context, Node root) {
        ArrayList<String> warnings = new ArrayList<>();
        View view = build(context, root, null, warnings);
        if (view == null) return RenderResult.error("Unsupported root view: " + root.tagName, warnings);
        return RenderResult.success(view, warnings);
    }

    private static View build(Context context, Node node, ViewGroup parent, List<String> warnings) {
        View view = createView(context, node.tagName, warnings);
        if (view == null) return null;
        applyAttributes(context, view, node, warnings);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (Node child : node.children) {
                View childView = build(context, child, group, warnings);
                if (childView == null) {
                    warnings.add("Unsupported view: " + child.tagName);
                    continue;
                }
                if ((group instanceof ScrollView || group instanceof HorizontalScrollView) && group.getChildCount() > 0) {
                    warnings.add(node.tagName + " supports one direct child; extra child skipped");
                    continue;
                }
                group.addView(childView, layoutParams(context, childView, child, group));
            }
        } else if (!node.children.isEmpty()) {
            warnings.add(node.tagName + " cannot contain child views");
        }
        return view;
    }

    private static View createView(Context context, String tag, List<String> warnings) {
        String name = simpleName(tag);
        switch (name) {
            case "LinearLayout": return new LinearLayout(context);
            case "FrameLayout": return new FrameLayout(context);
            case "RelativeLayout": return new RelativeLayout(context);
            case "ScrollView":
            case "NestedScrollView": return new ScrollView(context);
            case "HorizontalScrollView":
            case "HScrollView": return new HorizontalScrollView(context);
            case "TextView": return new TextView(context);
            case "EditText":
            case "TextInputEditText": return new EditText(context);
            case "Button": return new Button(context);
            case "MaterialButton": return new MaterialButton(context);
            case "CheckBox": return new CheckBox(context);
            case "Switch":
            case "MaterialSwitch": return new Switch(context);
            case "ImageView":
            case "ImageButton": return new ImageView(context);
            case "ProgressBar": return new ProgressBar(context);
            case "SeekBar": return new SeekBar(context);
            case "Spinner": return new Spinner(context);
            case "Space": return new Space(context);
            default:
                warnings.add("Unsupported widget " + tag + ", rendered as a placeholder");
                TextView placeholder = new TextView(context);
                placeholder.setText("<" + name + ">");
                placeholder.setGravity(Gravity.CENTER);
                placeholder.setTextColor(Color.DKGRAY);
                placeholder.setBackgroundColor(0xFFE8EAF0);
                return placeholder;
        }
    }

    private static void applyAttributes(Context context, View view, Node node, List<String> warnings) {
        String id = attr(node, "android:id");
        if (id != null) {
            String idName = resourceName(id);
            int resId = context.getResources().getIdentifier(idName, "id", context.getPackageName());
            view.setId(resId != 0 ? resId : View.generateViewId());
            view.setTag(idName);
        }

        String enabled = attr(node, "android:enabled");
        if (enabled != null) view.setEnabled(parseBoolean(enabled, true));
        String visibility = attr(node, "android:visibility");
        if (visibility != null) {
            view.setVisibility("gone".equals(visibility) ? View.GONE : "invisible".equals(visibility) ? View.INVISIBLE : View.VISIBLE);
        }
        String alpha = attr(node, "android:alpha");
        if (alpha != null) view.setAlpha(parseFloat(alpha, 1f));
        String rotation = attr(node, "android:rotation");
        if (rotation != null) view.setRotation(parseFloat(rotation, 0f));
        applyBackground(context, view, attr(node, "android:background"), warnings);
        applyPadding(context, view, node);
        if (view instanceof LinearLayout) {
            String orientation = attr(node, "android:orientation");
            ((LinearLayout) view).setOrientation("horizontal".equals(orientation) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        }
        String gravity = attr(node, "android:gravity");
        if (gravity != null && view instanceof LinearLayout) ((LinearLayout) view).setGravity(parseGravity(gravity));
        if (view instanceof TextView) applyTextAttributes(context, (TextView) view, node, warnings);
        if (view instanceof CheckBox || view instanceof Switch) {
            String checked = attr(node, "android:checked");
            if (checked != null) ((android.widget.CompoundButton) view).setChecked(parseBoolean(checked, false));
        }
        if (view instanceof ProgressBar || view instanceof SeekBar) {
            String max = attr(node, "android:max");
            if (max != null) ((android.widget.ProgressBar) view).setMax(parseInt(max, 100));
            String progress = attr(node, "android:progress");
            if (progress != null) ((android.widget.ProgressBar) view).setProgress(parseInt(progress, 0));
        }
        if (view instanceof Button || view instanceof MaterialButton) {
            String idName = resourceName(attr(node, "android:id"));
            view.setOnClickListener(v -> Toast.makeText(context, "Preview click" + (idName.isEmpty() ? "" : ": " + idName), Toast.LENGTH_SHORT).show());
        }
        for (String key : node.attributes.keySet()) {
            if (!SUPPORTED.contains(key)) warnings.add("Unsupported attribute: " + key + " on " + node.tagName);
        }
    }

    private static final java.util.Set<String> SUPPORTED = new java.util.HashSet<>(java.util.Arrays.asList(
            "android:id", "android:layout_width", "android:layout_height", "android:layout_margin", "android:layout_marginLeft",
            "android:layout_marginTop", "android:layout_marginRight", "android:layout_marginBottom", "android:orientation",
            "android:text", "android:hint", "android:textSize", "android:textColor", "android:background", "android:padding",
            "android:paddingLeft", "android:paddingTop", "android:paddingRight", "android:paddingBottom", "android:gravity",
            "android:layout_gravity", "android:visibility", "android:enabled", "android:checked", "android:max", "android:progress",
            "android:alpha", "android:rotation", "android:src", "android:scaleType", "tools:listitem"));

    private static void applyTextAttributes(Context context, TextView view, Node node, List<String> warnings) {
        String text = attr(node, "android:text");
        if (text != null) view.setText(resolveText(context, text));
        String hint = attr(node, "android:hint");
        if (view instanceof EditText && hint != null) ((EditText) view).setHint(resolveText(context, hint));
        String size = attr(node, "android:textSize");
        if (size != null) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, parseDimension(context, size, 14));
        String color = attr(node, "android:textColor");
        if (color != null) {
            Integer parsed = resolveColor(context, color);
            if (parsed != null) view.setTextColor(parsed);
            else warnings.add("Cannot resolve text color: " + color);
        }
        String singleLine = attr(node, "android:singleLine");
        if (singleLine != null) view.setSingleLine(parseBoolean(singleLine, false));
    }

    private static void applyPadding(Context context, View view, Node node) {
        String all = attr(node, "android:padding");
        if (all != null) {
            int p = parseDimension(context, all, 0);
            view.setPadding(p, p, p, p);
            return;
        }
        int left = parseDimension(context, attr(node, "android:paddingLeft"), 0);
        int top = parseDimension(context, attr(node, "android:paddingTop"), 0);
        int right = parseDimension(context, attr(node, "android:paddingRight"), 0);
        int bottom = parseDimension(context, attr(node, "android:paddingBottom"), 0);
        view.setPadding(left, top, right, bottom);
    }

    private static ViewGroup.LayoutParams layoutParams(Context context, View child, Node node, ViewGroup parent) {
        int width = parseLayoutSize(context, attr(node, "android:layout_width"), ViewGroup.LayoutParams.WRAP_CONTENT);
        int height = parseLayoutSize(context, attr(node, "android:layout_height"), ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewGroup.LayoutParams params;
        if (parent instanceof LinearLayout) params = new LinearLayout.LayoutParams(width, height);
        else if (parent instanceof RelativeLayout) params = new RelativeLayout.LayoutParams(width, height);
        else params = new FrameLayout.LayoutParams(width, height);
        int left = parseDimension(context, attr(node, "android:layout_marginLeft"), -1);
        int top = parseDimension(context, attr(node, "android:layout_marginTop"), -1);
        int right = parseDimension(context, attr(node, "android:layout_marginRight"), -1);
        int bottom = parseDimension(context, attr(node, "android:layout_marginBottom"), -1);
        int all = parseDimension(context, attr(node, "android:layout_margin"), -1);
        if (all >= 0) left = top = right = bottom = all;
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.setMargins(Math.max(0, left), Math.max(0, top), Math.max(0, right), Math.max(0, bottom));
        }
        String gravity = attr(node, "android:layout_gravity");
        if (gravity != null && params instanceof FrameLayout.LayoutParams) ((FrameLayout.LayoutParams) params).gravity = parseGravity(gravity);
        if (gravity != null && params instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) params).gravity = parseGravity(gravity);
        return params;
    }

    private static void applyBackground(Context context, View view, String value, List<String> warnings) {
        if (value == null) return;
        Integer color = resolveColor(context, value);
        if (color != null) {
            view.setBackgroundColor(color);
            return;
        }
        if (value.startsWith("@drawable/")) {
            int id = context.getResources().getIdentifier(resourceName(value), "drawable", context.getPackageName());
            if (id != 0) view.setBackgroundResource(id);
            else warnings.add("Cannot resolve drawable: " + value);
        }
    }

    private static String resolveText(Context context, String value) {
        if (value == null) return "";
        if (value.startsWith("@string/")) {
            int id = context.getResources().getIdentifier(resourceName(value), "string", context.getPackageName());
            if (id != 0) return context.getString(id);
        }
        return value.replace("\\n", "\n");
    }

    private static Integer resolveColor(Context context, String value) {
        if (value == null) return null;
        try {
            if (value.startsWith("@color/")) {
                int id = context.getResources().getIdentifier(resourceName(value), "color", context.getPackageName());
                return id == 0 ? null : context.getResources().getColor(id, context.getTheme());
            }
            if (value.startsWith("#")) return Color.parseColor(value);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int parseLayoutSize(Context context, String value, int fallback) {
        if (value == null) return fallback;
        if ("match_parent".equals(value) || "fill_parent".equals(value)) return ViewGroup.LayoutParams.MATCH_PARENT;
        if ("wrap_content".equals(value)) return ViewGroup.LayoutParams.WRAP_CONTENT;
        return parseDimension(context, value, fallback);
    }

    private static int parseDimension(Context context, String value, int fallback) {
        if (value == null) return fallback;
        try {
            String lower = value.toLowerCase(Locale.US).trim();
            if (lower.endsWith("dp")) return (int) (Float.parseFloat(lower.substring(0, lower.length() - 2)) * context.getResources().getDisplayMetrics().density + .5f);
            if (lower.endsWith("sp")) return (int) (Float.parseFloat(lower.substring(0, lower.length() - 2)) * context.getResources().getDisplayMetrics().scaledDensity + .5f);
            if (lower.endsWith("px")) return (int) Float.parseFloat(lower.substring(0, lower.length() - 2));
            if (lower.startsWith("@dimen/")) {
                int id = context.getResources().getIdentifier(resourceName(lower), "dimen", context.getPackageName());
                return id == 0 ? fallback : (int) context.getResources().getDimension(id);
            }
            return (int) Float.parseFloat(lower);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private static float parseFloat(String value, float fallback) {
        try { return Float.parseFloat(value); } catch (Exception ignored) { return fallback; }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int parseGravity(String value) {
        int gravity = 0;
        for (String token : value.split("\\|")) {
            switch (token.trim()) {
                case "center": gravity |= Gravity.CENTER; break;
                case "center_horizontal": gravity |= Gravity.CENTER_HORIZONTAL; break;
                case "center_vertical": gravity |= Gravity.CENTER_VERTICAL; break;
                case "left": case "start": gravity |= Gravity.LEFT; break;
                case "right": case "end": gravity |= Gravity.RIGHT; break;
                case "top": gravity |= Gravity.TOP; break;
                case "bottom": gravity |= Gravity.BOTTOM; break;
            }
        }
        return gravity;
    }

    private static String attr(Node node, String key) { return node.attributes.get(key); }
    private static String resourceName(String value) {
        if (value == null) return "";
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }
    private static String simpleName(String tag) {
        int dot = tag == null ? -1 : tag.lastIndexOf('.');
        return dot >= 0 ? tag.substring(dot + 1) : tag;
    }
    private static String cleanMessage(String message) {
        if (TextUtils.isEmpty(message)) return "Invalid XML";
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }

    public static final class Node {
        public final String tagName;
        public final Map<String, String> attributes = new LinkedHashMap<>();
        public final List<Node> children = new ArrayList<>();
        Node(String tagName) { this.tagName = tagName; }
    }

    public static final class ParseResult {
        public final Node root;
        public final String error;
        public final int line;
        public final int column;
        private ParseResult(Node root, String error, int line, int column) { this.root = root; this.error = error; this.line = line; this.column = column; }
        static ParseResult success(Node root) { return new ParseResult(root, null, 0, 0); }
        static ParseResult error(String error, int line, int column) { return new ParseResult(null, error, line, column); }
        public boolean isSuccess() { return root != null && error == null; }
    }

    public static final class RenderResult {
        public final View root;
        public final List<String> warnings;
        public final String error;
        private RenderResult(View root, List<String> warnings, String error) { this.root = root; this.warnings = warnings; this.error = error; }
        static RenderResult success(View root, List<String> warnings) { return new RenderResult(root, warnings, null); }
        static RenderResult error(String error, List<String> warnings) { return new RenderResult(null, warnings, error); }
        public boolean isSuccess() { return root != null && error == null; }
    }
}