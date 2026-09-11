package pro.sketchware.editor.diagnostics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.R;
import pro.sketchware.utility.RealtimeDiagnostics.Issue;

/** Adapter for the live diagnostics list. */
public class DiagnosticsAdapter extends ListAdapter<Issue, DiagnosticsAdapter.ViewHolder> {
    public interface OnIssueClickListener { void onIssueClick(Issue issue); }
    private final OnIssueClickListener clickListener;

    public DiagnosticsAdapter(@NonNull OnIssueClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Issue> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Issue a, @NonNull Issue b) {
            return a.line() == b.line() && a.message().equals(b.message());
        }
        @Override public boolean areContentsTheSame(@NonNull Issue a, @NonNull Issue b) {
            return a.equals(b);
        }
    };

    @NonNull
    @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diagnostic_entry, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView severityIcon;
        private final TextView lineNumber;
        private final TextView message;
        private final TextView hint;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            severityIcon = itemView.findViewById(R.id.severity_icon);
            lineNumber = itemView.findViewById(R.id.line_number);
            message = itemView.findViewById(R.id.diagnostic_message);
            hint = itemView.findViewById(R.id.diagnostic_hint);
        }

        void bind(Issue issue, OnIssueClickListener listener) {
            int icon = "error".equals(issue.severity()) ? R.drawable.ic_mtrl_warning : R.drawable.ic_mtrl_info;
            int color = "error".equals(issue.severity()) ? 0xFFE53935 : 0xFFFFA726;
            severityIcon.setImageResource(icon);
            severityIcon.setColorFilter(color);
            lineNumber.setText("Line " + issue.line());
            message.setText(issue.message());
            if (issue.hint() == null || issue.hint().isEmpty()) {
                hint.setVisibility(View.GONE);
            } else {
                hint.setVisibility(View.VISIBLE);
                hint.setText(issue.hint());
            }
            itemView.setOnClickListener(v -> listener.onIssueClick(issue));
        }
    }
}
