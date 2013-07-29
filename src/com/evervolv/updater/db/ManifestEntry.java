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
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONObject;

public class ManifestEntry implements Parcelable {

    public static final String COLUMN_ID       = "_id";
    public static final String COLUMN_DATE     = "date";
    public static final String COLUMN_NAME     = "name";
    public static final String COLUMN_MD5SUM   = "md5sum";
    public static final String COLUMN_LOCATION = "location";
    public static final String COLUMN_DEVICE   = "device";
    public static final String COLUMN_MESSAGE  = "message";
    public static final String COLUMN_TYPE     = "type";
    public static final String COLUMN_SIZE     = "size";
    public static final String COLUMN_COUNT    = "count";

    static final String[] ALL_COLUMNS = {
            COLUMN_ID,
            COLUMN_DATE,
            COLUMN_NAME,
            COLUMN_MD5SUM,
            COLUMN_LOCATION,
            COLUMN_DEVICE,
            COLUMN_MESSAGE,
            COLUMN_TYPE,
            COLUMN_SIZE,
            COLUMN_COUNT,
    };

    static final String TABLE_TEMPLATE = " (" +
            COLUMN_ID  + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_DATE     + " TEXT, " +
            COLUMN_NAME     + " TEXT, " +
            COLUMN_MD5SUM   + " TEXT, " +
            COLUMN_LOCATION + " TEXT, " +
            COLUMN_DEVICE   + " TEXT, " +
            COLUMN_MESSAGE  + " TEXT, " +
            COLUMN_TYPE     + " TEXT, " +
            COLUMN_SIZE     + " INT, " +
            COLUMN_COUNT    + " INT);";

    private long id;
    private String date;
    private String name;
    private String md5sum;
    private String location;
    private String device;
    private String message;
    private String type;
    private int size;
    private int count;

    public ManifestEntry() {
        /* pass */
    }

    public ManifestEntry(JSONObject json) {
        this.date     = json.optString(COLUMN_DATE);
        this.name     = json.optString(COLUMN_NAME);
        this.md5sum   = json.optString(COLUMN_MD5SUM);
        this.location = json.optString(COLUMN_LOCATION);
        this.device   = json.optString(COLUMN_DEVICE);
        this.message  = json.optString(COLUMN_MESSAGE);
        this.type     = json.optString(COLUMN_TYPE);
        this.size     = json.optInt(COLUMN_SIZE);
        this.count    = json.optInt(COLUMN_COUNT);
    }

    public ManifestEntry(Cursor cursor) {
        this.id       = cursor.getLong(0);
        this.date     = cursor.getString(1);
        this.name     = cursor.getString(2);
        this.md5sum   = cursor.getString(3);
        this.location = cursor.getString(4);
        this.device   = cursor.getString(5);
        this.message  = cursor.getString(6);
        this.type     = cursor.getString(7);
        this.size     = cursor.getInt(8);
        this.count    = cursor.getInt(9);
    }

    public static final Parcelable.Creator<ManifestEntry> CREATOR =
            new Parcelable.Creator<ManifestEntry>() {
        public ManifestEntry createFromParcel(Parcel in) {
            return new ManifestEntry(in);
        }

        public ManifestEntry[] newArray(int size) {
            return new ManifestEntry[size];
        }
    };

    private ManifestEntry(Parcel in) {
        this.id = in.readLong();
        this.date = in.readString();
        this.name = in.readString();
        this.md5sum = in.readString();
        this.location = in.readString();
        this.device = in.readString();
        this.message = in.readString();
        this.type = in.readString();
        this.size = in.readInt();
        this.count = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeString(date);
        out.writeString(name);
        out.writeString(md5sum);
        out.writeString(location);
        out.writeString(device);
        out.writeString(message);
        out.writeString(type);
        out.writeInt(size);
        out.writeInt(count);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date){
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public String getFriendlySize() {
        return String.format("%d MB", size / 1024 / 1024);
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
