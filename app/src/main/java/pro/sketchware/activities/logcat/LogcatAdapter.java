package pro.sketchware.activities.logcat;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

/**
 * High-performance adapter for real-time logcat entries.
 * Color-coded levels, crash highlighting, search highlighting,
 * efficient batch inserts with auto-scroll, and OOM protection (max 10k entries).
 */
public class LogcatAdapter extends RecyclerView.Adapter<LogcatAdapter.ViewHolder> {

    private static final int MAX_ENTRIES = 10000;
    private static final int TRIM_BATCH = 2000;

    private final List<LogEntry> entries = new ArrayList<>();
    @Nullable private String highlightQuery;
    private boolean autoScroll = true;
    @Nullable private RecyclerView recyclerView;

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);
        this.recyclerView = rv;
    }

    public void setHighlightQuery(@Nullable String query) {
        this.highlightQuery = query;
        notifyDataSetChanged();
    }

    public void setAutoScroll(boolean auto) { this.autoScroll = auto; }
    public boolean isAutoScroll() { return autoScroll; }

    public void addEntries(@NonNull List<LogEntry> newEntries) {
        if (newEntries.isEmpty()) return;
        if (entries.size() + newEntries.size() > MAX_ENTRIES) {
            int removeCount = Math.min(TRIM_BATCH, entries.size());
            entries.subList(0, removeCount).clear();
            notifyItemRangeRemoved(0, removeCount);
        }
        int startPos = entries.size();
        entries.addAll(newEntries);
        notifyItemRangeInserted(startPos, newEntries.size());
        if (autoScroll && recyclerView != null) {
            recyclerView.scrollToPosition(entries.size() - 1);
        }
    }

    public void clear() {
        int size = entries.size();
        entries.clear();
        notifyItemRangeRemoved(0, size);
    }

    public int getErrorCount() {
        int c = 0;
        for (LogEntry e : entries) if (e.level() == LogEntry.Level.ERROR || e.level() == LogEntry.Level.FATAL) c++;
        return c;
    }

    public int getWarningCount() {
        int c = 0;
        for (LogEntry e : entries) if (e.level() == LogEntry.Level.WARN) c++;
        return c;
    }

    public List<LogEntry> getEntries() { return entries; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_realtime_log_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entries.get(position), highlightQuery);
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View levelIndicator;
        final TextView levelText;
        final TextView tagText;
        final TextView messageText;
        final View crashHighlight;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            levelIndicator = itemView.findViewById(R.id.level_indicator);
            levelText = itemView.findViewById(R.id.level_text);
            tagText = itemView.findViewById(R.id.tag_text);
            messageText = itemView.findViewById(R.id.message_text);
            crashHighlight = itemView.findViewById(R.id.crash_highlight);
        }

        void bind(LogEntry entry, @Nullable String query) {
            int color = entry.level().color;
            levelIndicator.setBackgroundColor(color);
            levelText.setText(entry.level().letter);
            levelText.setTextColor(color);
            tagText.setText(entry.tag());

            if (query != null && !query.isEmpty()) {
                SpannableString spannable = new SpannableString(entry.message());
                String lowerMsg = entry.message().toLowerCase();
                String lowerQuery = query.toLowerCase();
                int start = 0;
                while ((start = lowerMsg.indexOf(lowerQuery, start)) >= 0) {
                    spannable.setSpan(new BackgroundColorSpan(0x4DFFA726),
                            start, start + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start += query.length();
                }
                messageText.setText(spannable);
            } else {
                messageText.setText(entry.message());
            }

            if (entry.isCrashRelated()) {
                crashHighlight.setVisibility(View.VISIBLE);
                messageText.setTextColor(0xFFEF5350);
            } else {
                crashHighlight.setVisibility(View.GONE);
                messageText.setTextColor(entry.level() == LogEntry.Level.WARN ? 0xFFFFA726 : 0xDEFFFFFF);
            }
        }
    }
}
