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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DatabaseManager {

    /* Version */
    private static final int DATABASE_VERSION        = 2;
    private static final String DATABASE_NAME        = "updates.db";
    /* Table names */
    private static final String NIGHTLIES_TABLE_NAME = Constants.BUILD_TYPE_NIGHTLIES;
    private static final String RELEASES_TABLE_NAME  = Constants.BUILD_TYPE_RELEASE;
    private static final String TESTING_TABLE_NAME   = Constants.BUILD_TYPE_TESTING;
    private static final String GAPPS_TABLE_NAME     = Constants.BUILD_TYPE_GAPPS;
    /* V1 Compat */
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
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + NIGHTLIES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + RELEASES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TESTING_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + GAPPS_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DOWNLOADS_TABLE_NAME);
            onCreate(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            /* We don't know what the next version will look like
               so just drop all tables present in onCreate and remake them
             */
            db.execSQL("DROP TABLE IF EXISTS " + NIGHTLIES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + RELEASES_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TESTING_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + GAPPS_TABLE_NAME);
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

    private void insertRow(String table, ManifestEntry entry) {
        ContentValues values = new ContentValues();
        values.put(ManifestEntry.COLUMN_DATE,     entry.getDate());
        values.put(ManifestEntry.COLUMN_NAME,     entry.getName());
        values.put(ManifestEntry.COLUMN_MD5SUM,   entry.getMd5sum());
        values.put(ManifestEntry.COLUMN_LOCATION, entry.getLocation());
        values.put(ManifestEntry.COLUMN_DEVICE,   entry.getDevice());
        values.put(ManifestEntry.COLUMN_MESSAGE,  entry.getMessage());
        values.put(ManifestEntry.COLUMN_TYPE,     entry.getType());
        values.put(ManifestEntry.COLUMN_SIZE,     entry.getSize());
        values.put(ManifestEntry.COLUMN_COUNT,    entry.getCount());
        values.put(ManifestEntry.COLUMN_MD5SUM_LOC,        entry.getMd5sumLoc());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_ID,       entry.getDownloadId());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_STATUS,   entry.getDownloadStatus());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_PROGRESS, entry.getDownloadProgress());
        database.insert(table, null, values);
    }

    /* Warn: only call if manifest entry was created with cursor
       id will be null otherwise.
     */
    private void deleteRow(String table, ManifestEntry entry) {
        database.delete(table,ManifestEntry.COLUMN_ID + "=" + entry.getId(), null);
    }

    /* Loop db:
         For each server entry:
           Check db entry exists in server list.
         If db entry not in server list:
           Remove it if it hasn't been downloaded

       For each server entry:
         Loop db:
           Check server entry exists in db.
         If server entry not in db:
           Add it.
     */
    public void update(String table, JSONArray entries) throws JSONException {
        /* Convert json array into list of manifest entries */
        List<ManifestEntry> newEntries = new ArrayList<ManifestEntry>();
        for (int i=0; i<entries.length();i++) {
            newEntries.add(new ManifestEntry(entries.getJSONObject(i)));
        }
        /* Compare current against new, removing old entries */
        boolean isPresent;
        Cursor c = database.query(table, ManifestEntry.ALL_COLUMNS,
                null, null, null, null, null);
        c.moveToFirst();
        while (!c.isAfterLast()) {
            isPresent = false;
            ManifestEntry e = new ManifestEntry(c);
            for (ManifestEntry newEntry: newEntries) {
                if (e.getMd5sum().equals(newEntry.getMd5sum())) {
                    isPresent = true;
                    break;
                }
            }
            if (!isPresent) {
                if (e.getDownloadId() < 0) {
                    deleteRow(table,e);
                }
            }
            c.moveToNext();
        }
        /* Compare new against current, adding new entries */
        for (ManifestEntry newEntry: newEntries) {
            isPresent = false;
            c.moveToFirst();
            while (!c.isAfterLast()) {
                ManifestEntry e = new ManifestEntry(c);
                if (e.getMd5sum().equals(newEntry.getMd5sum())) {
                    isPresent = true;
                    break;
                }
                c.moveToNext();
            }
            if (!isPresent) {
                insertRow(table,newEntry);
            }
        }
        c.close();
    }

    public List<ManifestEntry> getAllEntries(String table) throws SQLiteException {
        List<ManifestEntry> entries = new ArrayList<ManifestEntry>();
        Cursor c = database.query(table, ManifestEntry.ALL_COLUMNS,
                null, null, null, null, null);
        c.moveToLast();
        while (!c.isBeforeFirst()) {
            ManifestEntry e = new ManifestEntry(c);
            entries.add(e);
            c.moveToPrevious();
        }
        c.close();
        return entries;
    }

    /* Warn: requires all DOWNLOAD_** fields, md5sums
       and the entry must exist.
     */
    public void updateDownloadInfo(ManifestEntry entry) {
        ContentValues values = new ContentValues();
        values.put(ManifestEntry.COLUMN_MD5SUM_LOC, entry.getMd5sumLoc());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_ID, entry.getDownloadId());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_STATUS, entry.getDownloadStatus());
        values.put(ManifestEntry.COLUMN_DOWNLOAD_PROGRESS, entry.getDownloadProgress());
        database.update(entry.getType(), values,
                String.format("%s=\"%s\"",
                        ManifestEntry.COLUMN_MD5SUM,
                        entry.getMd5sum()
                ), null );
    }

    public ManifestEntry getDownloadFromId(long id) {
        List<String> tables = Arrays.asList(NIGHTLIES_TABLE_NAME,
                RELEASES_TABLE_NAME, TESTING_TABLE_NAME, GAPPS_TABLE_NAME);
        for (String table : tables) {
            Cursor c = database.query(table, ManifestEntry.ALL_COLUMNS,
                    null, null, null, null, null);
            c.moveToFirst();
            while (!c.isAfterLast()) {
                ManifestEntry e = new ManifestEntry(c);
                if (e.getDownloadId() == id) {
                    c.close();
                    return e;
                }
                c.moveToNext();
            }
            c.close();
        }
        return null;
    }

}
