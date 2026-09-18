package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.editor.LogicEditorActivity;

/** Installs the logic-editor enhancements without changing the legacy editor layout. */
public final class LogicEditorFeatureProvider extends ContentProvider {
    private static boolean installed;

    @Override
    public boolean onCreate() {
        if (!installed && getContext() != null) {
            installed = true;
            ((Application) getContext().getApplicationContext())
                    .registerActivityLifecycleCallbacks(new LogicEditorEnhancer());
        }
        return true;
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                                            String[] selectionArgs, String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
