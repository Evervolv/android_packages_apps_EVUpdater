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
import android.app.NotificationManager;
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
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.MD5;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadService extends Service {

    private static final String TAG = Constants.TAG;

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
        ManifestEntry entry = intent.getParcelableExtra(Constants.EXTRA_MANIFEST_ENTRY);
        if (entry != null) {
            if (action != null && action.equals(ACTION_START_DOWNLOAD)) {
                initiateNewDownload(entry);
            }
            if (entry.getDownloadId() > 0) {
                for (Download dl : mDownloads) {
                    if (entry.getDownloadId() == dl.id) {
                        /* We are already running for this download... do nothing */
                        if (Constants.DEBUG) Log.d(TAG,
                                "DownloadService: already tracking download " + entry.getDownloadId());
                        return START_NOT_STICKY;
                    }
                }
                mDownloads.add(new Download(entry.getDownloadId()));
                /* Start new thread for each download */
                ProgressThread p = new ProgressThread(entry);
                p.start();
            }
        }
        return START_NOT_STICKY; /* We handle restarting */
    }

    /* Only use when started externally */
    /* TODO refactor and get rid of this */
    private void initiateNewDownload(ManifestEntry entry) {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        String storageDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/" + Constants.DOWNLOAD_DIRECTORY + entry.getType() + "/";
        File downloadDir = new File(storageDir);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String url = Constants.FETCH_URL + entry.getName();
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        String fileUri = "file://" + storageDir + entry.getName() + ".partial";
        req.setTitle(entry.getName());
        req.setDestinationUri(Uri.parse(fileUri));
        req.setAllowedOverRoaming(false);
        req.setVisibleInDownloadsUi(false);
        DownloadManager dlm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dlm.enqueue(req);
        try {
            entry.setDownloadId(downloadId);
            entry.setDownloadStatus(DownloadManager.STATUS_PENDING);
            entry.setDownloadProgress(0);
            DatabaseManager dm = new DatabaseManager(this);
            dm.open();
            dm.updateDownloadInfo(entry);
            dm.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    private class ProgressThread extends Thread {

        private ManifestEntry entry;

        public ProgressThread(ManifestEntry entry) {
            super("EVUpdates Download Service");
            this.entry = entry;
        }

        @Override
        public void run() {
            long id = entry.getDownloadId();
            String LogPrefix = "ProgressThread: download " + id;
            Log.i(TAG, LogPrefix + " thread starting");
            boolean end = false;
            boolean updateUI = true;
            int deferUpdate = 0;
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
                Cursor c = downloadManager.query(q);
                if (!c.moveToFirst()) { c.close(); break; }
                status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (entry.getDownloadStatus() != status) {
                    entry.setDownloadStatus(status);
                    databaseManager.updateDownloadInfo(entry);
                }
                updateUI = true;
                switch (status) {
                    case DownloadManager.STATUS_RUNNING:
                        completed = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        total = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        progress = (int) ((completed * 100) / total);
                        if (progress == previousProgress) {
                            deferUpdate++;
                            updateUI = false;
                            break;
                        }
                        previousProgress = progress;
                        entry.setDownloadProgress(progress);
                        //databaseManager.updateDownloadInfo(entry);
                        if (Constants.DEBUG) Log.d(TAG, LogPrefix + " at " + progress + "%");
                        break;
                    case DownloadManager.STATUS_PENDING:
                    case DownloadManager.STATUS_PAUSED:
                        if (Constants.DEBUG) Log.d(TAG, LogPrefix + " paused/pending");
                        entry.setDownloadProgress(-1);
                        deferUpdate++;
                        updateUI = false;
                        break;
                    case DownloadManager.STATUS_SUCCESSFUL:
                        String file = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                        File partialFile = new File(file);
                        File newFile = new File(file.replace(".partial", ""));
                        partialFile.renameTo(newFile);
                        entry.setDownloadProgress(100);
                        try {
                            Log.i(TAG, LogPrefix + " complete... Calculating MD5SUM");
                            entry.setMd5sumLoc(MD5.calculateMD5(newFile));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        end = true;
                        if (Constants.DEBUG) Log.d(TAG, LogPrefix + " completed successfully");
                        break;
                    case DownloadManager.STATUS_FAILED:
                    default: /* We don't want this running forever if something goes wrong */
                        Log.w(TAG, LogPrefix + " failed");
                        entry.setDownloadProgress(-1);
                        end = true;
                        break;
                }
                c.close();
                /* Certain scenarios where the download stalls and the ui
                   fails to update borks the progress bar, so force
                   updating every 5 seconds
                 */
                if (deferUpdate > 4) {
                    if (Constants.DEBUG) Log.d(TAG, LogPrefix + " deferred too many times... forcing update");
                    deferUpdate = 0;
                    updateUI = true;
                }
                /* build the broadcast */
                if (updateUI) {
                    Intent dlIntent = new Intent();
                    dlIntent.setAction(ACTION_UPDATE_DOWNLOAD);
                    dlIntent.putExtra(Constants.EXTRA_MANIFEST_ENTRY, entry);
                    sendOrderedBroadcast(dlIntent, null);
                }
                if (mDestroyed || end) {
                    databaseManager.updateDownloadInfo(entry);
                    break;
                }
                synchronized (this) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            databaseManager.close();
            Log.i(TAG, LogPrefix + " thread ending");
        }
    }

    private class Download {
        public long id;
        public Download(long id) {
            this.id  = id;
        }
    }

}
