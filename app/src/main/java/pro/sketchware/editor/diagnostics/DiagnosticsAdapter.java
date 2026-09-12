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

public class DiagnosticsAdapter extends ListAdapter<Issue, DiagnosticsAdapter.ViewHolder> {
    public interface OnIssueClickListener {
        void onIssueClick(Issue issue);
    }

    private final OnIssueClickListener listener;

    public DiagnosticsAdapter(@NonNull OnIssueClickListener listener) {
        super(new DiffUtil.ItemCallback<Issue>() {
            @Override
            public boolean areItemsTheSame(@NonNull Issue a, @NonNull Issue b) {
                return a.kind() == b.kind()
                        && a.line() == b.line()
                        && a.column() == b.column()
                        && a.message().equals(b.message());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Issue a, @NonNull Issue b) {
                return a.equals(b);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diagnostic_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView line;
        final TextView message;
        final TextView hint;

        ViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.severity_icon);
            line = view.findViewById(R.id.line_number);
            message = view.findViewById(R.id.diagnostic_message);
            hint = view.findViewById(R.id.diagnostic_hint);
        }

        void bind(Issue issue, OnIssueClickListener listener) {
            boolean warning = "warning".equals(issue.severity());
            boolean info = "info".equals(issue.severity());
            int color = warning ? 0xFFFFA726 : info ? 0xFF42A5F5 : 0xFFE53935;
            icon.setImageResource(warning || info
                    ? R.drawable.ic_mtrl_warning
                    : R.drawable.ic_mtrl_bug_report);
            icon.setColorFilter(color);
            line.setText("Line " + issue.line() + ", Col " + issue.column());
            message.setText(issue.message());
            hint.setText(issue.hint());
            hint.setVisibility(issue.hint() == null || issue.hint().isEmpty()
                    ? View.GONE : View.VISIBLE);
            itemView.setContentDescription("Jump to " + line.getText() + ": " + issue.message());
            itemView.setOnClickListener(v -> listener.onIssueClick(issue));
        }
    }
}
