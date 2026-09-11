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

/**
 * Adapter for the diagnostics panel RecyclerView.
 * Shows each issue with severity icon, line number, message, and hint.
 * Tap an issue to jump to the error line in the editor.
 */
public class DiagnosticsAdapter extends ListAdapter<Issue, DiagnosticsAdapter.ViewHolder> {

    public interface OnIssueClickListener {
        void onIssueClick(Issue issue);
    }

    private final OnIssueClickListener clickListener;

    public DiagnosticsAdapter(@NonNull OnIssueClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Issue> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Issue a, @NonNull Issue b) {
            return a.line() == b.line() && a.message().equals(b.message());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Issue a, @NonNull Issue b) {
            return a.equals(b);
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diagnostic_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Issue issue = getItem(position);
        holder.bind(issue, clickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView severityIcon;
        final TextView lineNumber;
        final TextView message;
        final TextView hint;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            severityIcon = itemView.findViewById(R.id.severity_icon);
            lineNumber = itemView.findViewById(R.id.line_number);
            message = itemView.findViewById(R.id.diagnostic_message);
            hint = itemView.findViewById(R.id.diagnostic_hint);
        }

        void bind(Issue issue, OnIssueClickListener listener) {
            switch (issue.severity()) {
                case "error":
                    severityIcon.setColorFilter(0xFFE53935);
                    severityIcon.setImageResource(R.drawable.ic_mtrl_error);
                    break;
                case "warning":
                    severityIcon.setColorFilter(0xFFFFA726);
                    severityIcon.setImageResource(R.drawable.ic_warning_96dp);
                    break;
                default:
                    severityIcon.setColorFilter(0xFF42A5F5);
                    severityIcon.setImageResource(R.drawable.ic_mtrl_info);
                    break;
            }

            lineNumber.setText("Line " + issue.line());
            message.setText(issue.message());

            if (issue.hint() != null && !issue.hint().isEmpty()) {
                hint.setVisibility(View.VISIBLE);
                hint.setText(issue.hint());
            } else {
                hint.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onIssueClick(issue));
        }
    }
}
