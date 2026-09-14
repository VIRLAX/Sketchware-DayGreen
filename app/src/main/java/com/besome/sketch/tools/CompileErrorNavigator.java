package com.besome.sketch.tools;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.CompileLogHelper;
import pro.sketchware.R;
import mod.hey.studios.code.SrcCodeEditor;

/** Turns compiler file:line:column locations into one-tap editor links. */
public final class CompileErrorNavigator {
    private static final Pattern LOCATION = Pattern.compile("(?m)(/[^\\n\\r\\t ]+\\.(?:java|kt|xml)):(\\d+)(?::(\\d+))?");

    private CompileErrorNavigator() { }

    public static void bind(Context context, TextView target, String log, String scId) {
        CharSequence colored = CompileLogHelper.getColoredLogs(context, log);
        SpannableStringBuilder value = new SpannableStringBuilder(colored);
        Matcher matcher = LOCATION.matcher(value.toString());
        int links = 0;
        while (matcher.find()) {
            final String path = matcher.group(1);
            final int line = parseInt(matcher.group(2), 1);
            final int column = parseInt(matcher.group(3), 1);
            value.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    Intent intent = new Intent(context, SrcCodeEditor.class);
                    intent.putExtra("sc_id", scId);
                    intent.putExtra("content", path);
                    intent.putExtra("title", new File(path).getName());
                    intent.putExtra("line", line);
                    intent.putExtra("column", column);
                    intent.putExtra("from_compile_error", true);
                    context.startActivity(intent);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new ForegroundColorSpan(0xFF80CBC4), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            links++;
        }
        target.setText(value);
        target.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        target.setLinksClickable(true);
        target.setTextIsSelectable(false);
        target.setContentDescription(links == 0 ? "Compile log" : links + " clickable source locations");
    }

    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
}
