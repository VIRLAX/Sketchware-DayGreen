package pro.sketchware.activities.preview;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
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

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.besome.sketch.beans.ProjectResourceBean;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.jC;

/** Native XML renderer used by both full-screen and floating preview. */
public final class RuntimeXmlPreview {
    private static volatile String previewProjectId;

    private RuntimeXmlPreview() { }

    public static void setProjectId(String projectId) { previewProjectId = projectId; }

    public static ParseResult parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) return ParseResult.error("XML is empty", 1, 1);
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
                        if (!roots.isEmpty()) return ParseResult.error("XML must contain one root view", parser.getLineNumber(), parser.getColumnNumber());
                        roots.add(node);
                    } else stack.get(stack.size() - 1).children.add(node);
                    stack.add(node);
                } else if (event == XmlPullParser.END_TAG && !stack.isEmpty()) stack.remove(stack.size() - 1);
            }
            if (roots.isEmpty()) return ParseResult.error("No root view found", 1, 1);
            return ParseResult.success(roots.get(0));
        } catch (XmlPullParserException e) {
            return ParseResult.error(cleanMessage(e.getMessage()), e.getLineNumber() > 0 ? e.getLineNumber() : 1, e.getColumnNumber() > 0 ? e.getColumnNumber() : 1);
        } catch (IOException | RuntimeException e) {
            return ParseResult.error(cleanMessage(e.getMessage()), 1, 1);
        }
    }

    public static RenderResult render(Context context, Node root) {
        String projectId = projectIdFor(context);
        ArrayList<String> warnings = new ArrayList<>();
        PreviewLog.log("Rendering <" + root.tagName + ">");
        View view = build(context, root, null, warnings, projectId);
        if (view == null) return RenderResult.error("Unsupported root view: " + root.tagName, warnings);
        PreviewLog.log("Rendered native view tree" + (warnings.isEmpty() ? "" : " with " + warnings.size() + " warning(s)"));
        return RenderResult.success(view, warnings);
    }

    private static View build(Context context, Node node, ViewGroup parent, List<String> warnings, String projectId) {
        View view = createView(context, node.tagName, warnings);
        if (view == null) return null;
        applyAttributes(context, view, node, warnings, projectId);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (parseBoolean(attr(node, "android:animateLayoutChanges"), false)) group.setLayoutTransition(new LayoutTransition());
            for (Node child : node.children) {
                View childView = build(context, child, group, warnings, projectId);
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
        } else if (!node.children.isEmpty()) warnings.add(node.tagName + " cannot contain child views");
        return view;
    }

    private static View createView(Context context, String tag, List<String> warnings) {
        String name = simpleName(tag);
        switch (name) {
            case "LinearLayout": return new LinearLayout(context);
            case "FrameLayout": return new FrameLayout(context);
            case "RelativeLayout": return new RelativeLayout(context);
            case "ConstraintLayout": return new ConstraintLayout(context);
            case "CoordinatorLayout": return new CoordinatorLayout(context);
            case "AppBarLayout": return new AppBarLayout(context);
            case "Toolbar": return new androidx.appcompat.widget.Toolbar(context);
            case "MaterialToolbar": return new MaterialToolbar(context);
            case "ScrollView": case "NestedScrollView": return new ScrollView(context);
            case "HorizontalScrollView": case "HScrollView": return new HorizontalScrollView(context);
            case "CardView": return new CardView(context);
            case "MaterialCardView": return new MaterialCardView(context);
            case "TextInputLayout": return new TextInputLayout(context);
            case "TextView": return new TextView(context);
            case "EditText": return new EditText(context);
            case "TextInputEditText": return new TextInputEditText(context);
            case "Button": return new Button(context);
            case "MaterialButton": return new MaterialButton(context);
            case "CheckBox": return new CheckBox(context);
            case "Switch": case "MaterialSwitch": return new Switch(context);
            case "ImageButton": return new ImageButton(context);
            case "ImageView": return new ImageView(context);
            case "ProgressBar": return new ProgressBar(context);
            case "SeekBar": return new SeekBar(context);
            case "Spinner": return new Spinner(context);
            case "Space": return new Space(context);
            default:
                View reflected = reflectView(context, tag);
                if (reflected != null) return reflected;
                warnings.add("Unsupported widget " + tag + ", rendered as a container");
                PreviewLog.log("Unsupported widget kept as container: " + tag);
                FrameLayout container = new FrameLayout(context);
                container.setBackgroundColor(0x22000000);
                TextView label = new TextView(context);
                label.setText("<" + name + ">");
                label.setTextSize(10);
                label.setTextColor(0xFF9EAAA7);
                label.setGravity(Gravity.CENTER);
                container.addView(label, new FrameLayout.LayoutParams(-1, dp(context, 24), Gravity.TOP));
                return container;
        }
    }

    private static View reflectView(Context context, String tag) {
        try {
            Class<?> type = Class.forName(tag);
            if (!View.class.isAssignableFrom(type)) return null;
            Constructor<?> constructor = type.getConstructor(Context.class);
            return (View) constructor.newInstance(context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyAttributes(Context context, View view, Node node, List<String> warnings, String projectId) {
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
        if (visibility != null) view.setVisibility("gone".equals(visibility) ? View.GONE : "invisible".equals(visibility) ? View.INVISIBLE : View.VISIBLE);
        String alpha = attr(node, "android:alpha");
        if (alpha != null) view.setAlpha(parseFloat(alpha, 1f));
        String rotation = attr(node, "android:rotation");
        if (rotation != null) view.setRotation(parseFloat(rotation, 0f));
        String elevation = attr(node, "android:elevation");
        if (elevation != null) view.setElevation(parseDimension(context, elevation, 0));
        String translationX = attr(node, "android:translationX");
        String translationY = attr(node, "android:translationY");
        if (translationX != null) view.setTranslationX(parseDimension(context, translationX, 0));
        if (translationY != null) view.setTranslationY(parseDimension(context, translationY, 0));
        String contentDescription = attr(node, "android:contentDescription");
        if (contentDescription != null) view.setContentDescription(resolveText(context, contentDescription));
        applyBackground(context, view, attr(node, "android:background"), warnings, projectId);
        applyPadding(context, view, node);
        applyTint(context, view, attr(node, "android:backgroundTint"));
        if (view instanceof LinearLayout) {
            String orientation = attr(node, "android:orientation");
            ((LinearLayout) view).setOrientation("horizontal".equals(orientation) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        }
        String gravity = attr(node, "android:gravity");
        if (gravity != null) {
            if (view instanceof LinearLayout) ((LinearLayout) view).setGravity(parseGravity(gravity));
            if (view instanceof TextView) ((TextView) view).setGravity(parseGravity(gravity));
        }
        if (view instanceof TextView) applyTextAttributes(context, (TextView) view, node, warnings);
        if (view instanceof ImageView) applyImageAttributes(context, (ImageView) view, node, warnings, projectId);
        if (view instanceof TextInputLayout) applyTextInputLayout(context, (TextInputLayout) view, node, warnings);
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
        if (view instanceof EditText) {
            String inputType = attr(node, "android:inputType");
            if (inputType != null) applyInputType((EditText) view, inputType);
        }
        if (view instanceof Button || view instanceof MaterialButton) {
            String idName = resourceName(attr(node, "android:id"));
            view.setOnClickListener(v -> {
                PreviewLog.log("Click: " + (idName.isEmpty() ? node.tagName : idName));
                Toast.makeText(context, "Preview click" + (idName.isEmpty() ? "" : ": " + idName), Toast.LENGTH_SHORT).show();
            });
        }
        applyCardAttributes(context, view, node, warnings);
        applyPreviewAnimation(view, node);
        for (String key : node.attributes.keySet()) if (!SUPPORTED.contains(key)) warnings.add("Unsupported attribute: " + key + " on " + node.tagName);
    }

    private static void applyTextInputLayout(Context context, TextInputLayout layout, Node node, List<String> warnings) {
        String hint = attr(node, "android:hint");
        if (hint != null) layout.setHint(resolveText(context, hint));
        String boxMode = attr(node, "app:boxBackgroundMode");
        if ("filled".equals(boxMode)) layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
        if ("outline".equals(boxMode)) layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        Integer boxColor = resolveColor(context, attr(node, "app:boxBackgroundColor"));
        if (boxColor != null) layout.setBoxBackgroundColor(boxColor);
        Integer strokeColor = resolveColor(context, attr(node, "app:boxStrokeColor"));
        if (strokeColor != null) layout.setBoxStrokeColor(strokeColor);
        String endIcon = attr(node, "app:endIconMode");
        if ("password_toggle".equals(endIcon)) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        if ("clear_text".equals(endIcon)) layout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
    }

    private static void applyCardAttributes(Context context, View view, Node node, List<String> warnings) {
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            Integer background = resolveColor(context, attr(node, "app:cardBackgroundColor"));
            if (background != null) card.setCardBackgroundColor(background);
            String radius = attr(node, "app:cardCornerRadius");
            if (radius != null) card.setRadius(parseDimension(context, radius, 0));
            String elevation = attr(node, "app:cardElevation");
            if (elevation != null) card.setCardElevation(parseDimension(context, elevation, 0));
            Integer stroke = resolveColor(context, attr(node, "app:strokeColor"));
            if (stroke != null) card.setStrokeColor(stroke);
            String strokeWidth = attr(node, "app:strokeWidth");
            if (strokeWidth != null) card.setStrokeWidth(parseDimension(context, strokeWidth, 0));
        } else if (view instanceof CardView) {
            CardView card = (CardView) view;
            Integer background = resolveColor(context, attr(node, "app:cardBackgroundColor"));
            if (background != null) card.setCardBackgroundColor(background);
            String radius = attr(node, "app:cardCornerRadius");
            if (radius != null) card.setRadius(parseDimension(context, radius, 0));
            String elevation = attr(node, "app:cardElevation");
            if (elevation != null) card.setCardElevation(parseDimension(context, elevation, 0));
        }
    }

    private static void applyImageAttributes(Context context, ImageView view, Node node, List<String> warnings, String projectId) {
        String src = attr(node, "android:src");
        if (src != null) {
            int resId = resolveResourceId(context, src, "drawable");
            if (resId != 0) view.setImageResource(resId);
            else {
                File projectFile = findProjectImage(projectId, resourceName(src));
                if (projectFile != null) {
                    if (projectFile.getName().toLowerCase(Locale.US).endsWith(".svg")) view.setImageURI(android.net.Uri.fromFile(projectFile));
                    else view.setImageBitmap(BitmapFactory.decodeFile(projectFile.getAbsolutePath()));
                } else warnings.add("Cannot resolve image: " + src);
            }
        }
        String scaleType = attr(node, "android:scaleType");
        if (scaleType != null) {
            try { view.setScaleType(ImageView.ScaleType.valueOf(scaleType.toUpperCase(Locale.US))); } catch (Exception ignored) { }
        }
    }

    private static File findProjectImage(String projectId, String name) {
        if (projectId == null || name == null) return null;
        try {
            Object manager = jC.d(projectId);
            String directory = (String) manager.getClass().getMethod("l").invoke(manager);
            Object list = manager.getClass().getField("b").get(manager);
            if (list instanceof Iterable) for (Object item : (Iterable<?>) list) {
                ProjectResourceBean image = (ProjectResourceBean) item;
                if (name.equals(image.resName)) {
                    File file = new File(directory, image.resFullName);
                    if (file.exists()) return file;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void applyInputType(EditText editText, String value) {
        if (value.contains("textPassword")) editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else if (value.contains("number")) editText.setInputType(InputType.TYPE_CLASS_NUMBER);
    }

    private static void applyTint(Context context, View view, String value) {
        Integer color = resolveColor(context, value);
        if (color != null) view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private static void applyPreviewAnimation(View view, Node node) {
        String animation = attr(node, "app:previewAnimation");
        if (animation == null) return;
        ObjectAnimator animator;
        switch (animation) {
            case "fade": animator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.15f, 1f); break;
            case "pulse": animator = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1.04f, 1f); break;
            case "slide": animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(view.getContext(), 18), 0f); break;
            case "scale": animator = ObjectAnimator.ofPropertyValuesHolder(view, android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1f), android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1f)); break;
            default: PreviewLog.log("Unknown preview animation: " + animation); return;
        }
        animator.setDuration(260L); animator.setInterpolator(new DecelerateInterpolator());
        if ("pulse".equals(animation)) animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start(); PreviewLog.log("Animation: " + animation + " on <" + node.tagName + ">");
    }

    private static void applyTextAttributes(Context context, TextView view, Node node, List<String> warnings) {
        String text = attr(node, "android:text");
        if (text != null) view.setText(resolveText(context, text));
        String hint = attr(node, "android:hint");
        if (view instanceof EditText && hint != null) ((EditText) view).setHint(resolveText(context, hint));
        String size = attr(node, "android:textSize");
        if (size != null) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, parseDimension(context, size, 14));
        Integer textColor = resolveColor(context, attr(node, "android:textColor"));
        if (textColor != null) view.setTextColor(textColor); else if (attr(node, "android:textColor") != null) warnings.add("Cannot resolve text color: " + attr(node, "android:textColor"));
        Integer hintColor = resolveColor(context, attr(node, "android:textColorHint"));
        if (hintColor != null && view instanceof EditText) ((EditText) view).setHintTextColor(hintColor);
        String singleLine = attr(node, "android:singleLine");
        if (singleLine != null) view.setSingleLine(parseBoolean(singleLine, false));
        String textStyle = attr(node, "android:textStyle");
        if ("bold".equals(textStyle)) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
    }

    private static void applyPadding(Context context, View view, Node node) {
        String all = attr(node, "android:padding");
        if (all != null) { int p = parseDimension(context, all, 0); view.setPadding(p, p, p, p); return; }
        view.setPadding(parseDimension(context, attr(node, "android:paddingLeft"), 0), parseDimension(context, attr(node, "android:paddingTop"), 0), parseDimension(context, attr(node, "android:paddingRight"), 0), parseDimension(context, attr(node, "android:paddingBottom"), 0));
    }

    private static ViewGroup.LayoutParams layoutParams(Context context, View child, Node node, ViewGroup parent) {
        int width = parseLayoutSize(context, attr(node, "android:layout_width"), ViewGroup.LayoutParams.WRAP_CONTENT);
        int height = parseLayoutSize(context, attr(node, "android:layout_height"), ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewGroup.LayoutParams params;
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            lp.weight = parseFloat(attr(node, "android:layout_weight"), 0f);
            params = lp;
        } else if (parent instanceof RelativeLayout) params = new RelativeLayout.LayoutParams(width, height);
        else if (parent instanceof CoordinatorLayout) params = new CoordinatorLayout.LayoutParams(width, height);
        else if (parent instanceof AppBarLayout) params = new AppBarLayout.LayoutParams(width, height);
        else params = new FrameLayout.LayoutParams(width, height);
        int left = parseDimension(context, attr(node, "android:layout_marginLeft"), -1);
        int top = parseDimension(context, attr(node, "android:layout_marginTop"), -1);
        int right = parseDimension(context, attr(node, "android:layout_marginRight"), -1);
        int bottom = parseDimension(context, attr(node, "android:layout_marginBottom"), -1);
        int all = parseDimension(context, attr(node, "android:layout_margin"), -1);
        if (all >= 0) left = top = right = bottom = all;
        if (params instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams) params).setMargins(Math.max(0, left), Math.max(0, top), Math.max(0, right), Math.max(0, bottom));
        String gravity = attr(node, "android:layout_gravity");
        if (gravity != null && params instanceof FrameLayout.LayoutParams) ((FrameLayout.LayoutParams) params).gravity = parseGravity(gravity);
        if (gravity != null && params instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) params).gravity = parseGravity(gravity);
        return params;
    }

    private static void applyBackground(Context context, View view, String value, List<String> warnings, String projectId) {
        if (value == null) return;
        Integer color = resolveColor(context, value);
        if (color != null) { view.setBackgroundColor(color); return; }
        if (value.startsWith("@drawable/")) {
            int id = resolveResourceId(context, value, "drawable");
            if (id != 0) view.setBackgroundResource(id);
            else {
                File projectFile = findProjectImage(projectId, resourceName(value));
                if (projectFile != null) {
                    Drawable drawable = Drawable.createFromPath(projectFile.getAbsolutePath());
                    if (drawable != null) view.setBackground(drawable);
                } else warnings.add("Cannot resolve drawable: " + value);
            }
        }
    }

    private static String resolveText(Context context, String value) {
        if (value == null) return "";
        if (value.startsWith("@string/")) {
            int id = resolveResourceId(context, value, "string");
            if (id != 0) return context.getString(id);
        }
        return value.replace("\\n", "\n");
    }

    private static Integer resolveColor(Context context, String value) {
        if (value == null) return null;
        try {
            if (value.startsWith("@color/")) {
                int id = resolveResourceId(context, value, "color");
                return id == 0 ? null : context.getResources().getColor(id, context.getTheme());
            }
            if (value.startsWith("#")) return Color.parseColor(value);
        } catch (Exception ignored) { }
        return null;
    }

    private static int resolveResourceId(Context context, String value, String type) {
        if (value == null) return 0;
        return context.getResources().getIdentifier(resourceName(value), type, context.getPackageName());
    }

    private static String projectIdFor(Context context) {
        if (previewProjectId != null) return previewProjectId;
        if (context instanceof Activity) return ((Activity) context).getIntent().getStringExtra("sc_id");
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
            if (lower.startsWith("@dimen/")) { int id = resolveResourceId(context, lower, "dimen"); return id == 0 ? fallback : (int) context.getResources().getDimension(id); }
            return (int) Float.parseFloat(lower);
        } catch (Exception ignored) { return fallback; }
    }

    private static int dp(Context context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static float parseFloat(String value, float fallback) { try { return Float.parseFloat(value); } catch (Exception ignored) { return fallback; } }
    private static boolean parseBoolean(String value, boolean fallback) { if ("true".equalsIgnoreCase(value)) return true; if ("false".equalsIgnoreCase(value)) return false; return fallback; }

    private static int parseGravity(String value) {
        int gravity = 0;
        for (String token : value.split("\\|")) switch (token.trim()) {
            case "center": gravity |= Gravity.CENTER; break;
            case "center_horizontal": gravity |= Gravity.CENTER_HORIZONTAL; break;
            case "center_vertical": gravity |= Gravity.CENTER_VERTICAL; break;
            case "left": case "start": gravity |= Gravity.LEFT; break;
            case "right": case "end": gravity |= Gravity.RIGHT; break;
            case "top": gravity |= Gravity.TOP; break;
            case "bottom": gravity |= Gravity.BOTTOM; break;
        }
        return gravity;
    }

    private static String attr(Node node, String key) { return node.attributes.get(key); }
    private static String resourceName(String value) { if (value == null) return ""; int slash = value.indexOf('/'); return slash >= 0 ? value.substring(slash + 1) : value; }
    private static String simpleName(String tag) { int dot = tag == null ? -1 : tag.lastIndexOf('.'); return dot >= 0 ? tag.substring(dot + 1) : tag; }
    private static String cleanMessage(String message) { if (TextUtils.isEmpty(message)) return "Invalid XML"; int newline = message.indexOf('\n'); return newline >= 0 ? message.substring(0, newline) : message; }

    private static final java.util.Set<String> SUPPORTED = new java.util.HashSet<>(java.util.Arrays.asList(
            "android:id", "android:layout_width", "android:layout_height", "android:layout_margin", "android:layout_marginLeft", "android:layout_marginTop", "android:layout_marginRight", "android:layout_marginBottom", "android:layout_weight", "android:orientation", "android:text", "android:hint", "android:textSize", "android:textColor", "android:textColorHint", "android:textStyle", "android:background", "android:backgroundTint", "android:padding", "android:paddingLeft", "android:paddingTop", "android:paddingRight", "android:paddingBottom", "android:gravity", "android:layout_gravity", "android:visibility", "android:enabled", "android:checked", "android:max", "android:progress", "android:alpha", "android:rotation", "android:elevation", "android:translationX", "android:translationY", "android:contentDescription", "android:src", "android:scaleType", "android:singleLine", "android:inputType", "android:animateLayoutChanges", "app:previewAnimation", "app:cardBackgroundColor", "app:cardCornerRadius", "app:cardElevation", "app:strokeColor", "app:strokeWidth", "app:boxBackgroundMode", "app:boxBackgroundColor", "app:boxStrokeColor", "app:endIconMode", "tools:listitem"));

    public static final class Node {
        public final String tagName;
        public final Map<String, String> attributes = new LinkedHashMap<>();
        public final List<Node> children = new ArrayList<>();
        Node(String tagName) { this.tagName = tagName; }
    }

    public static final class ParseResult {
        public final Node root; public final String error; public final int line; public final int column;
        private ParseResult(Node root, String error, int line, int column) { this.root = root; this.error = error; this.line = line; this.column = column; }
        static ParseResult success(Node root) { return new ParseResult(root, null, 0, 0); }
        static ParseResult error(String error, int line, int column) { PreviewLog.log("XML error at " + line + ":" + column + " " + error); return new ParseResult(null, error, line, column); }
        public boolean isSuccess() { return root != null && error == null; }
    }

    public static final class RenderResult {
        public final View root; public final List<String> warnings; public final String error;
        private RenderResult(View root, List<String> warnings, String error) { this.root = root; this.warnings = warnings; this.error = error; }
        static RenderResult success(View root, List<String> warnings) { return new RenderResult(root, warnings, null); }
        static RenderResult error(String error, List<String> warnings) { PreviewLog.log("Render error: " + error); return new RenderResult(null, warnings, error); }
        public boolean isSuccess() { return root != null && error == null; }
    }
}
