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

package com.evervolv.updater.services;

import android.app.DownloadManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.evervolv.updater.db.DatabaseManager;
import com.evervolv.updater.db.DownloadEntry;
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadService extends Service {

    private static final String TAG = Constants.TAG;

    /* Intent extra fields */
    private static final String EXTRA_DOWNLOAD_ID       = Constants.EXTRA_DOWNLOAD_ID;
    private static final String EXTRA_DOWNLOAD_STATUS   = Constants.EXTRA_DOWNLOAD_STATUS;
    private static final String EXTRA_DOWNLOAD_PROGRESS = Constants.EXTRA_DOWNLOAD_PROGRESS;

    /* Intent actions */
    private static final String ACTION_UPDATE_DOWNLOAD = Constants.ACTION_UPDATE_DOWNLOAD;
    private static final String ACTION_START_DOWNLOAD  = Constants.ACTION_START_DOWNLOAD;

    List<Download> mDownloads = new ArrayList<Download>();
    private boolean mDestroyed = false;

    @Override
    public void onDestroy() {
        mDestroyed = true; /* Tell our threads to die */
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        long downloadId;
        if (action != null && action.equals(ACTION_START_DOWNLOAD)) {
            ManifestEntry entry = intent.getParcelableExtra(Constants.EXTRA_MANIFEST_ENTRY);
            downloadId = initiateNewDownload(entry);
        } else {
            downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1);
        }
        if (downloadId > 0) {
            for (Download dl : mDownloads) {
                if (downloadId == dl.id) {
                    /* We are already running for this download... do nothing */
                    if (Constants.DEBUG) Log.d(TAG, "DownloadService: already tracking download " + downloadId);
                    return START_NOT_STICKY;
                }
            }
            mDownloads.add(new Download(downloadId));
            /* Start new thread for each download */
            ProgressThread p = new ProgressThread(downloadId);
            p.start();
        }
        return START_NOT_STICKY; /* We handle restarting */
    }

    /* Only use when started externally */
    private long initiateNewDownload(ManifestEntry entry) {
        String storageDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/" + Constants.DOWNLOAD_DIRECTORY + entry.getType() + "/";
        File downloadDir = new File(storageDir);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String url = Constants.FETCH_URL + entry.getName();
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        String filename = "file://" + storageDir + entry.getName() + ".partial";
        req.setTitle(new File(filename).getName().replace(".partial", ""));
        req.setDestinationUri(Uri.parse(filename));
        req.setAllowedOverRoaming(false);
        req.setVisibleInDownloadsUi(false);
        DownloadManager dlm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dlm.enqueue(req);
        try {
            DownloadEntry dlEntry = new DownloadEntry();
            dlEntry.setDownloadId(downloadId);
            dlEntry.setMd5sum(entry.getMd5sum());
            DatabaseManager dm = new DatabaseManager(this);
            dm.open();
            dm.addDownload(dlEntry);
            dm.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        return downloadId;
    }

    private class ProgressThread extends Thread {

        private long id;

        public ProgressThread(long id) {
            super("EVUpdates Download Service");
            this.id = id;
        }

        @Override
        public void run() {
            Log.i(TAG, "ProgressThread: download " + id + " thread starting");
            boolean end = false;
            boolean deferUpdate;
            long completed;
            long total;
            int status;
            int progress;
            int previousProgress = -1;

            DatabaseManager databaseManager = new DatabaseManager(getApplicationContext());
            databaseManager.open();

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(id);

            while (!mDestroyed) {
                if (end) break;
                Cursor c = downloadManager.query(q);
                if (!c.moveToFirst()) { c.close(); break; }
                /* Reset all our intent variables */
                deferUpdate = false;
                progress = -1;
                status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                switch (status) {
                    case DownloadManager.STATUS_RUNNING:
                        completed = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        total = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        progress = (int) ((completed * 100) / total);
                        if (progress == previousProgress) {
                            deferUpdate = true;
                            break;
                        }
                        previousProgress = progress;
                        if (Constants.DEBUG) Log.d(TAG, "ProgressThread: download " + id + " at " + progress + "%");
                        break;
                    case DownloadManager.STATUS_PENDING:
                    case DownloadManager.STATUS_PAUSED:
                        if (Constants.DEBUG) Log.d(TAG, "ProgressThread: download " + id + " paused/pending");
                        deferUpdate = true;
                        break;
                    case DownloadManager.STATUS_SUCCESSFUL:
                        String file = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                        File partialFile = new File(file);
                        File newFile = new File(file.replace(".partial", ""));
                        partialFile.renameTo(newFile);
                        databaseManager.removeDownload(id);
                        end = true;
                        if (Constants.DEBUG) Log.d(TAG, "ProgressThread: download " + id + " completed successfully");
                        break;
                    case DownloadManager.STATUS_FAILED:
                    default: /* We don't want this running forever if something goes wrong */
                        Log.w(TAG, "ProgressThread: download " + id + " failed");
                        databaseManager.removeDownload(id);
                        end = true;
                        break;
                }
                c.close();
                /* build the broadcast */
                if (!deferUpdate) {
                    Intent dlIntent = new Intent();
                    dlIntent.setAction(ACTION_UPDATE_DOWNLOAD);
                    dlIntent.putExtra(EXTRA_DOWNLOAD_ID, id);
                    dlIntent.putExtra(EXTRA_DOWNLOAD_STATUS, status);
                    dlIntent.putExtra(EXTRA_DOWNLOAD_PROGRESS, progress);
                    sendOrderedBroadcast(dlIntent, null);
                }
                if (mDestroyed) break;
                if (end) break;
                synchronized (this) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            databaseManager.close();
            Log.i(TAG, "ProgressThread: download " + id + " thread ending");
        }
    }

    private class Download {
        public long id;
        public Download(long id) {
            this.id  = id;
        }
    }

}
