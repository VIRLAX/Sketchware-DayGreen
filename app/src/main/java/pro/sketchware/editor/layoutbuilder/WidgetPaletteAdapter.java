package pro.sketchware.editor.layoutbuilder;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;

/**
 * Adapter for the widget palette.
 * Displays widgets grouped by category. Long press initiates drag & drop,
 * single tap inserts at cursor position.
 */
public class WidgetPaletteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_WIDGET = 1;

    private final List<Object> items = new ArrayList<>();

    public WidgetPaletteAdapter() {
        buildItems();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void buildItems() {
        items.clear();
        Map<WidgetTemplate.Category, List<WidgetTemplate>> grouped = new LinkedHashMap<>();
        for (WidgetTemplate t : WidgetTemplate.getAllTemplates()) {
            grouped.computeIfAbsent(t.getCategory(), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<WidgetTemplate.Category, List<WidgetTemplate>> entry : grouped.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof WidgetTemplate.Category ? TYPE_HEADER : TYPE_WIDGET;
    }

    @Override
    public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_widget_palette_header, parent, false));
        } else {
            return new WidgetViewHolder(inflater.inflate(R.layout.item_widget_palette_widget, parent, false));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof HeaderViewHolder h && item instanceof WidgetTemplate.Category cat) {
            h.title.setText(cat.getLabel());
        } else if (holder instanceof WidgetViewHolder w && item instanceof WidgetTemplate template) {
            w.name.setText(template.getName());
            w.container.setAlpha(1f);
            w.iconText.setText(template.getName().substring(0, 1).toUpperCase());

            w.badge.setVisibility(template.isContainer() ? View.VISIBLE : View.GONE);

            w.container.setOnLongClickListener(v -> {
                DragDropLayoutBuilder.startWidgetDrag(v, template);
                v.setAlpha(0.5f);
                return true;
            });

            w.container.setOnClickListener(v -> {
                if (onWidgetTapListener != null) onWidgetTapListener.onWidgetTap(template);
            });
        }
    }

    public interface OnWidgetTapListener {
        void onWidgetTap(WidgetTemplate template);
    }

    private OnWidgetTapListener onWidgetTapListener;

    public void setOnWidgetTapListener(OnWidgetTapListener listener) {
        this.onWidgetTapListener = listener;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.category_title);
        }
    }

    static class WidgetViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final TextView name;
        final TextView iconText;
        final View badge;
        WidgetViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.widget_container);
            name = itemView.findViewById(R.id.widget_name);
            iconText = itemView.findViewById(R.id.widget_icon_text);
            badge = itemView.findViewById(R.id.container_badge);
        }
    }
}
