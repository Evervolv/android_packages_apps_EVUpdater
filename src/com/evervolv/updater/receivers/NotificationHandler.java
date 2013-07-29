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

package com.evervolv.updater.receivers;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.evervolv.updater.R;
import com.evervolv.updater.Updater;
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.Utils;

public class NotificationHandler extends BroadcastReceiver {

    public static final int notificationId = 1258; // Reuse the same notification always

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        } else if (action.equals(Constants.ACTION_UPDATE_NOTIFY_NEW)) {
            ManifestEntry entry = intent.getParcelableExtra(Constants.EXTRA_MANIFEST_ENTRY);
            if (Utils.isNewerThanInstalled(entry.getDate())) {

                Intent downloadIntent = new Intent();
                downloadIntent.setAction(Constants.ACTION_START_DOWNLOAD);
                downloadIntent.putExtra(Constants.EXTRA_MANIFEST_ENTRY, entry);
                PendingIntent downloadPendingIntent =
                        PendingIntent.getService(context, 0, downloadIntent, 0);

                Intent clickIntent = new Intent(context, Updater.class);
                clickIntent.putExtra(Constants.EXTRA_MANIFEST_ENTRY, entry);
                PendingIntent clickPendingIntent =
                        PendingIntent.getActivity(context, 0, clickIntent, 0);

                Notification notification =
                        new Notification.Builder(context)
                                .setSmallIcon(R.drawable.ic_launcher_updater)
                                .setTicker(context.getString(R.string.notification_update_available))
                                .setContentTitle(context.getString(R.string.notification_update_available))
                                .setContentText(entry.getName())
                                .setContentIntent(clickPendingIntent)
                                .setStyle(new Notification.InboxStyle()
                                        .addLine(context.getString(R.string.build_info_filename)
                                                + " " + entry.getName())
                                        .addLine(context.getString(R.string.build_info_date)
                                                + " " + entry.getDate())
                                        .addLine(context.getString(R.string.build_info_size)
                                                + " " + entry.getFriendlySize())
                                )
                                .addAction(R.drawable.ic_menu_download,
                                        context.getString(R.string.menu_download),
                                        downloadPendingIntent)
                                .setAutoCancel(true)
                                .build();

                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(notificationId, notification);
            }
        } else if (action.equals(Constants.ACTION_UPDATE_DOWNLOAD)) {
            int status = intent.getIntExtra(Constants.EXTRA_DOWNLOAD_STATUS,
                    DownloadManager.STATUS_PENDING);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                long downloadId = intent.getLongExtra(Constants.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId < 0) return;
                DownloadManager downloadManager =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                Cursor c = downloadManager.query(q);
                if (!c.moveToFirst()) { c.close(); return; }
                String name = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE));


                //Intent flashIntent = new Intent(context, FlashActivity.class);
                // TODO somehow build a ManifestEntry
                //PendingIntent downloadPendingIntent =
                //        PendingIntent.getActivity(context, 0, flashIntent, 0);

                Intent clickIntent = new Intent(context, Updater.class);
                PendingIntent clickPendingIntent =
                        PendingIntent.getActivity(context, 0, clickIntent, 0);

                Notification notification =
                        new Notification.Builder(context)
                                .setSmallIcon(R.drawable.ic_launcher_updater)
                                .setTicker(context.getString(R.string.notification_update_downloaded))
                                .setContentTitle(context.getString(R.string.notification_update_downloaded))
                                .setContentText(name)
                                .setContentIntent(clickPendingIntent)
                                //.addAction(R.drawable.ic_menu_flash,
                                //        context.getString(R.string.menu_flash),
                                //        downloadPendingIntent)
                                .setAutoCancel(true)
                                .build();

                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify((int)downloadId, notification);
            }
        }
    }
}
