package com.app.fakesensor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.app.fakesensor.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/all");

    public static Uri allUri() {
        return CONTENT_URI;
    }

    private SharedPreferences prefs;

    @Override
    public boolean onCreate() {
        prefs = getContext().getSharedPreferences("fake_sensor_config", 0);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
        for (String k : prefs.getAll().keySet()) {
            Object v = prefs.getAll().get(k);
            cursor.newRow().add("key", k).add("value", v != null ? v.toString() : "");
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) { return "vnd.android.cursor.dir/config"; }
    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
