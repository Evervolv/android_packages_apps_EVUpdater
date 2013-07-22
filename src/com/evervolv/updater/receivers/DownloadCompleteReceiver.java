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
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.evervolv.updater.R;
import com.evervolv.updater.Updater;
import com.evervolv.updater.services.DownloadService;

public class DownloadCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            //boolean isUpdate;
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //DatabaseManager m = new DatabaseManager(context);
            //m.open();
            //isUpdate = m.queryDownloads(downloadId);
            //m.close();

            //if (isUpdate) {
                Intent dlService = new Intent(context, DownloadService.class);
                dlService.putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId);
                context.startService(dlService);
            //}
        } else if (action.equals(DownloadService.ACTION_UPDATE_DOWNLOAD)) {
            int status = intent.getIntExtra(DownloadService.EXTRA_DOWNLOAD_STATUS,
                    DownloadManager.STATUS_PENDING);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                long downloadId = intent.getLongExtra(DownloadService.EXTRA_DOWNLOAD_ID, -1);
                //if (downloadId < 0) return;
                //DownloadManager downloadManager =
                //        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                //DownloadManager.Query q = new DownloadManager.Query();
                //q.setFilterById(downloadId);
                //Cursor c = downloadManager.query(q);
                //String name = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE));
                Notification.Builder mBuilder =
                        new Notification.Builder(context)
                                .setSmallIcon(R.drawable.ic_launcher_toolbox) // TODO white icon
                                .setContentTitle(context.getString(R.string.notification_update_downloaded))
                                ;//.setContentText(name);
                Intent resultIntent = new Intent(context, Updater.class);

                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                stackBuilder.addParentStack(Updater.class);
                stackBuilder.addNextIntent(resultIntent);
                PendingIntent resultPendingIntent =
                        stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                mBuilder.setContentIntent(resultPendingIntent);
                NotificationManager mNotificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify((int) downloadId, mBuilder.build());
            }
        }
    }

}
