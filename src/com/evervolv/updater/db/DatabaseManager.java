/*
 * Copyright (C) 2013 The Evervolv Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evervolv.updater.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.evervolv.updater.misc.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    /* Version */
    private static final int DATABASE_VERSION        = 1;
    private static final String DATABASE_NAME        = "updates.db";
    /* Table names */
    private static final String NIGHTLIES_TABLE_NAME = Constants.BUILD_TYPE_NIGHTLIES;
    private static final String RELEASES_TABLE_NAME  = Constants.BUILD_TYPE_RELEASE;
    private static final String TESTING_TABLE_NAME   = Constants.BUILD_TYPE_TESTING;
    private static final String GAPPS_TABLE_NAME     = Constants.BUILD_TYPE_GAPPS;
    private static final String DOWNLOADS_TABLE_NAME = "downloads";

    private final Context context;
    private DatabaseHelper databaseHelper;
    private SQLiteDatabase database;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NIGHTLIES_TABLE_NAME + ManifestEntry.TABLE_TEMPLATE);
            db.execSQL("CREATE TABLE " + RELEASES_TABLE_NAME + ManifestEntry.TABLE_TEMPLATE);
            db.execSQL("CREATE TABLE " + TESTING_TABLE_NAME + ManifestEntry.TABLE_TEMPLATE);
            db.execSQL("CREATE TABLE " + GAPPS_TABLE_NAME + ManifestEntry.TABLE_TEMPLATE);
            db.execSQL("CREATE TABLE " + DOWNLOADS_TABLE_NAME + DownloadEntry.TABLE_TEMPLATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO V2
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + NIGHTLIES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + RELEASES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TESTING_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + GAPPS_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DOWNLOADS_TABLE_NAME);
            onCreate(db);
        }

    }

    public DatabaseManager(Context context) {
        this.context = context;
    }

    public DatabaseManager open() throws SQLiteException {
        databaseHelper = new DatabaseHelper(context);
        database = databaseHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        databaseHelper.close();
    }

    /* NIGHTLY, RELEASE, TESTING, GAPPS Tables */

    public void updateManifest(String table, JSONArray entries) throws SQLiteException, JSONException {
        database.delete(table, null, null); // Kill em all
        for (int i=0; i<entries.length();i++) {
            JSONObject entry = entries.getJSONObject(i);
            ContentValues values = new ContentValues();
            values.put(ManifestEntry.COLUMN_DATE,     entry.optString(ManifestEntry.COLUMN_DATE));
            values.put(ManifestEntry.COLUMN_NAME,     entry.optString(ManifestEntry.COLUMN_NAME));
            values.put(ManifestEntry.COLUMN_MD5SUM,   entry.optString(ManifestEntry.COLUMN_MD5SUM));
            values.put(ManifestEntry.COLUMN_LOCATION, entry.optString(ManifestEntry.COLUMN_LOCATION));
            values.put(ManifestEntry.COLUMN_DEVICE,   entry.optString(ManifestEntry.COLUMN_DEVICE));
            values.put(ManifestEntry.COLUMN_MESSAGE,  entry.optString(ManifestEntry.COLUMN_MESSAGE));
            values.put(ManifestEntry.COLUMN_TYPE,     entry.optString(ManifestEntry.COLUMN_TYPE));
            values.put(ManifestEntry.COLUMN_SIZE,     entry.optInt(ManifestEntry.COLUMN_SIZE));
            values.put(ManifestEntry.COLUMN_COUNT,    entry.optInt(ManifestEntry.COLUMN_COUNT));
            database.insert(table, null, values);
        }
    }

    public List<ManifestEntry> fetchManifest(String table) throws SQLiteException {
        List<ManifestEntry> entries = new ArrayList<ManifestEntry>();
        Cursor c = database.query(table, ManifestEntry.ALL_COLUMNS,
                null, null, null, null, null);
        c.moveToFirst();
        while (!c.isAfterLast()) {
            ManifestEntry e = new ManifestEntry(c);
            entries.add(e);
            c.moveToNext();
        }
        c.close();
        return entries;
    }

    /* DOWNLOADS Table */

    public void addDownload(DownloadEntry entry) throws SQLiteException {
        //TODO check and remove duplicates
        ContentValues values = new ContentValues();
        values.put(DownloadEntry.COLUMN_DOWNLOAD_ID, entry.getDownloadId());
        values.put(DownloadEntry.COLUMN_MD5SUM,      entry.getMd5sum());
        database.insert(DOWNLOADS_TABLE_NAME, null, values);
    }

    public long getDownloadId(String md5sum) throws SQLiteException {
        Cursor c = database.query(DOWNLOADS_TABLE_NAME, DownloadEntry.ALL_COLUMNS,
                null,null,null,null,null);
        long id = -1;
        /* Reverse lookup, in case of duplicates
           we want the newest one. */
        c.moveToLast();
        while (!c.isBeforeFirst()) {
            DownloadEntry e = new DownloadEntry(c);
            if (e.getMd5sum().equals(md5sum)) {
                id = e.getDownloadId();
                break;
            }
            c.moveToPrevious();
        }
        c.close();
        return id;
    }

    public boolean queryDownloads(long downloadId) {
        Cursor c = database.query(DOWNLOADS_TABLE_NAME, DownloadEntry.ALL_COLUMNS,
                null,null,null,null,null);
        boolean exists = false;
        c.moveToFirst();
        while (!c.isAfterLast()) {
            DownloadEntry e = new DownloadEntry(c);
            if (e.getDownloadId() == downloadId) {
                exists = true;
                break;
            }
            c.moveToNext();
        }
        c.close();
        return exists;
    }

    public void removeDownload(long downloadId) throws SQLiteException {
        database.delete(DOWNLOADS_TABLE_NAME,
                DownloadEntry.COLUMN_DOWNLOAD_ID + "=" + downloadId, null);
    }

}
