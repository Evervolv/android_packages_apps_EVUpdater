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

import android.database.Cursor;

public class DownloadEntry {

    public static final String COLUMN_ID          = "_id";
    public static final String COLUMN_DOWNLOAD_ID = "download_id";
    public static final String COLUMN_MD5SUM      = "md5sum";

    static final String[] ALL_COLUMNS = {
            COLUMN_ID,
            COLUMN_DOWNLOAD_ID,
            COLUMN_MD5SUM,
    };

    static final String TABLE_TEMPLATE = " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_DOWNLOAD_ID + " INTEGER, " +
            COLUMN_MD5SUM      + " TEXT);";

    private long id;
    private long downloadId;
    private String md5sum;

    public DownloadEntry() {
        /* pass */
    }

    public DownloadEntry(Cursor cursor) {
        this.id         = cursor.getLong(0);
        this.downloadId = cursor.getLong(1);
        this.md5sum     = cursor.getString(2);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

}
