package pro.sketchware.editor.layoutbuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines available widgets for the drag & drop layout builder.
 * Each template generates proper XML when dropped onto the preview.
 * Supports 6 categories: Layouts, Basic, Input, Lists, Media, Material Design.
 */
public class WidgetTemplate {

    public enum Category {
        LAYOUTS("Layouts"),
        BASIC("Basic Widgets"),
        INPUT("Input"),
        LISTS("Lists & Containers"),
        MEDIA("Media"),
        MATERIAL("Material Design");

        final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final String name;
    private final String icon;
    private final Category category;
    private final String xmlTemplate;
    private final boolean isContainer;

    public WidgetTemplate(String name, String icon, Category category, String xmlTemplate, boolean isContainer) {
        this.name = name;
        this.icon = icon;
        this.category = category;
        this.xmlTemplate = xmlTemplate;
        this.isContainer = isContainer;
    }

    public String getName() { return name; }
    public String getIcon() { return icon; }
    public Category getCategory() { return category; }
    public String getXmlTemplate() { return xmlTemplate; }
    public boolean isContainer() { return isContainer; }

    public String generateXml(String idSuffix, int indent) {
        String indentStr = " ".repeat(indent);
        return xmlTemplate
                .replace("{ID}", idSuffix)
                .replace("{INDENT}", indentStr);
    }

    public static List<WidgetTemplate> getAllTemplates() {
        List<WidgetTemplate> templates = new ArrayList<>();

        // --- LAYOUTS ---
        templates.add(new WidgetTemplate("LinearLayout\n(Vertical)", "linear_v", Category.LAYOUTS,
                "<LinearLayout\n" +
                "{INDENT}    android:id=\"@+id/linear_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:orientation=\"vertical\"\n" +
                "{INDENT}    android:padding=\"8dp\">\n" +
                "{INDENT}</LinearLayout>", true));

        templates.add(new WidgetTemplate("LinearLayout\n(Horizontal)", "linear_h", Category.LAYOUTS,
                "<LinearLayout\n" +
                "{INDENT}    android:id=\"@+id/linear_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:orientation=\"horizontal\"\n" +
                "{INDENT}    android:padding=\"8dp\">\n" +
                "{INDENT}</LinearLayout>", true));

        templates.add(new WidgetTemplate("RelativeLayout", "relative", Category.LAYOUTS,
                "<RelativeLayout\n" +
                "{INDENT}    android:id=\"@+id/relative_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:padding=\"8dp\">\n" +
                "{INDENT}</RelativeLayout>", true));

        templates.add(new WidgetTemplate("FrameLayout", "frame", Category.LAYOUTS,
                "<FrameLayout\n" +
                "{INDENT}    android:id=\"@+id/frame_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\">\n" +
                "{INDENT}</FrameLayout>", true));

        templates.add(new WidgetTemplate("ScrollView", "scroll", Category.LAYOUTS,
                "<ScrollView\n" +
                "{INDENT}    android:id=\"@+id/scroll_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"match_parent\">\n" +
                "{INDENT}    <LinearLayout\n" +
                "{INDENT}        android:layout_width=\"match_parent\"\n" +
                "{INDENT}        android:layout_height=\"wrap_content\"\n" +
                "{INDENT}        android:orientation=\"vertical\" />\n" +
                "{INDENT}</ScrollView>", true));

        templates.add(new WidgetTemplate("CardView", "card", Category.LAYOUTS,
                "<androidx.cardview.widget.CardView\n" +
                "{INDENT}    android:id=\"@+id/card_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    app:cardCornerRadius=\"12dp\"\n" +
                "{INDENT}    app:cardElevation=\"4dp\"\n" +
                "{INDENT}    android:layout_margin=\"8dp\">\n" +
                "{INDENT}</androidx.cardview.widget.CardView>", true));

        // --- BASIC WIDGETS ---
        templates.add(new WidgetTemplate("TextView", "textview", Category.BASIC,
                "<TextView\n" +
                "{INDENT}    android:id=\"@+id/textview_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"TextView\"\n" +
                "{INDENT}    android:textSize=\"14sp\" />", false));

        templates.add(new WidgetTemplate("Button", "button", Category.BASIC,
                "<Button\n" +
                "{INDENT}    android:id=\"@+id/button_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"Button\" />", false));

        templates.add(new WidgetTemplate("ImageView", "imageview", Category.BASIC,
                "<ImageView\n" +
                "{INDENT}    android:id=\"@+id/imageview_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"48dp\"\n" +
                "{INDENT}    android:layout_height=\"48dp\"\n" +
                "{INDENT}    android:src=\"@drawable/ic_launcher\" />", false));

        templates.add(new WidgetTemplate("View (Divider)", "divider", Category.BASIC,
                "<View\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"1dp\"\n" +
                "{INDENT}    android:background=\"#E0E0E0\" />", false));

        // --- INPUT ---
        templates.add(new WidgetTemplate("EditText", "edittext", Category.INPUT,
                "<EditText\n" +
                "{INDENT}    android:id=\"@+id/edittext_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:hint=\"Enter text\" />", false));

        templates.add(new WidgetTemplate("CheckBox", "checkbox", Category.INPUT,
                "<CheckBox\n" +
                "{INDENT}    android:id=\"@+id/checkbox_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"CheckBox\" />", false));

        templates.add(new WidgetTemplate("Switch", "switch_w", Category.INPUT,
                "<Switch\n" +
                "{INDENT}    android:id=\"@+id/switch_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"Switch\" />", false));

        templates.add(new WidgetTemplate("Spinner", "spinner", Category.INPUT,
                "<Spinner\n" +
                "{INDENT}    android:id=\"@+id/spinner_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\" />", false));

        templates.add(new WidgetTemplate("SeekBar", "seekbar", Category.INPUT,
                "<SeekBar\n" +
                "{INDENT}    android:id=\"@+id/seekbar_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:max=\"100\" />", false));

        // --- LISTS ---
        templates.add(new WidgetTemplate("RecyclerView", "recyclerview", Category.LISTS,
                "<androidx.recyclerview.widget.RecyclerView\n" +
                "{INDENT}    android:id=\"@+id/recyclerview_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:clipToPadding=\"false\" />", false));

        templates.add(new WidgetTemplate("ListView", "listview", Category.LISTS,
                "<ListView\n" +
                "{INDENT}    android:id=\"@+id/listview_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\" />", false));

        // --- MEDIA ---
        templates.add(new WidgetTemplate("WebView", "webview", Category.MEDIA,
                "<WebView\n" +
                "{INDENT}    android:id=\"@+id/webview_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"300dp\" />", false));

        templates.add(new WidgetTemplate("ProgressBar", "progress", Category.MEDIA,
                "<ProgressBar\n" +
                "{INDENT}    android:id=\"@+id/progress_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\" />", false));

        // --- MATERIAL DESIGN ---
        templates.add(new WidgetTemplate("MaterialButton", "mat_button", Category.MATERIAL,
                "<com.google.android.material.button.MaterialButton\n" +
                "{INDENT}    android:id=\"@+id/matbutton_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"Material Button\" />", false));

        templates.add(new WidgetTemplate("TextInputLayout", "textinput", Category.MATERIAL,
                "<com.google.android.material.textfield.TextInputLayout\n" +
                "{INDENT}    android:id=\"@+id/textinput_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"match_parent\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:hint=\"Label\">\n" +
                "{INDENT}    <com.google.android.material.textfield.TextInputEditText\n" +
                "{INDENT}        android:layout_width=\"match_parent\"\n" +
                "{INDENT}        android:layout_height=\"wrap_content\" />\n" +
                "{INDENT}</com.google.android.material.textfield.TextInputLayout>", true));

        templates.add(new WidgetTemplate("FloatingActionButton", "fab", Category.MATERIAL,
                "<com.google.android.material.floatingactionbutton.FloatingActionButton\n" +
                "{INDENT}    android:id=\"@+id/fab_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:layout_gravity=\"bottom|end\"\n" +
                "{INDENT}    android:layout_margin=\"16dp\" />", false));

        templates.add(new WidgetTemplate("Chip", "chip", Category.MATERIAL,
                "<com.google.android.material.chip.Chip\n" +
                "{INDENT}    android:id=\"@+id/chip_{ID}\"\n" +
                "{INDENT}    android:layout_width=\"wrap_content\"\n" +
                "{INDENT}    android:layout_height=\"wrap_content\"\n" +
                "{INDENT}    android:text=\"Chip\" />", false));

        return templates;
    }
}
