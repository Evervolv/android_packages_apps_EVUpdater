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

package com.evervolv.updater;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.evervolv.updater.db.DatabaseManager;
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.DownloadPreference;
import com.evervolv.updater.services.DownloadService;
import com.evervolv.updater.services.UpdateManifestService;

import java.util.List;

public class UpdatesFragment extends PreferenceFragment {

    protected static final String TAG = Constants.TAG;

    public static final int DIALOG_MANIFEST_ERROR    = 1;
    public static final int DIALOG_CONFIRM_CANCEL    = 2;
    public static final int DIALOG_CONFIRM_DELETE    = 3;

    protected Context mContext;
    protected Resources mRes;
    protected PreferenceCategory mAvailableCategory;
    protected AlertDialog mAlertDialog;

    protected String mUpdateType;
    protected String mUpdateAction;

    private DownloadManager mDownloadManager;
    private DatabaseManager mDatabaseManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private ActionMode mChildActionMode = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.preference_swipe_fragment, container, false);
        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipe_list_container);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkForUpdates();
                    }
                }, 2500);
            }
        });
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mContext = getContext();
        mRes = getResources();

        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);

        mDatabaseManager = new DatabaseManager(mContext);
        mDatabaseManager.open();
    }

    public ContentResolver getContentResolver() {
         return getActivity().getContentResolver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDatabaseManager.close();
    }

    @Override
    public void onStart() {
        super.onStart();
        updateLayout();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_UPDATE_CHECK_FINISHED);
        filter.addAction(Constants.ACTION_UPDATE_NOTIFY_NEW);
        filter.addAction(Constants.ACTION_UPDATE_DOWNLOAD);
        filter.setPriority(2); // Intercept orderedBroadcasts
        mContext.registerReceiver(mUpdateCheckReceiver, filter);
    }

    @Override
    public void onStop() {
        super.onStop();
        mContext.stopService(new Intent(mContext, DownloadService.class));
        mContext.unregisterReceiver(mUpdateCheckReceiver);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.updates_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                checkForUpdates();
                return true;
        }
        return false;
    }

    protected BroadcastReceiver mUpdateCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            ManifestEntry entry = intent.getParcelableExtra(Constants.EXTRA_MANIFEST_ENTRY);
            String buildType = intent.getStringExtra(Constants.EXTRA_MANIFEST_TYPE);
            if (action != null) {
                if (entry != null && entry.getType().equals(mUpdateType)) {
                    if (action.equals(Constants.ACTION_UPDATE_DOWNLOAD)) {
                        for (int i = 0; i < mAvailableCategory.getPreferenceCount(); i++) {
                            DownloadPreference pref = (DownloadPreference) mAvailableCategory.getPreference(i);
                            pref.updateDownload(entry);
                        }
                        if (!Constants.DEBUG) abortBroadcast();
                    } else if (action.equals(Constants.ACTION_UPDATE_NOTIFY_NEW)) {
                        if (!Constants.DEBUG) abortBroadcast();
                    }
                } else if (action.equals(Constants.ACTION_UPDATE_CHECK_FINISHED)
                        && buildType != null
                        && buildType.equals(mUpdateType)) {
                    if (!intent.getBooleanExtra(Constants.EXTRA_MANIFEST_ERROR, false)) {
                        updateLayout();
                    } else {
                        showDialog(DIALOG_MANIFEST_ERROR, null);
                    }
                }
            }
        }
    };

    protected void startUpdateManifestService(boolean scheduleUpdate) {
        Intent updateCheck = new Intent(mContext, UpdateManifestService.class);
        updateCheck.setAction(mUpdateAction);
        if (scheduleUpdate) {
            updateCheck.putExtra(Constants.EXTRA_SCHEDULE_UPDATE, true);
        }
        mContext.startService(updateCheck);
    }

    public void checkForUpdates() {
        getActivity().invalidateOptionsMenu();
        startUpdateManifestService(false);
        mSwipeRefreshLayout.setRefreshing(false);
    }

    protected void updateLayout() {
        mAvailableCategory.removeAll();
        try {
            List<ManifestEntry> entries = mDatabaseManager.getAllEntries(mUpdateType);
            for (ManifestEntry entry : entries) {
                DownloadPreference item = new DownloadPreference(mContext, this, entry);
                mAvailableCategory.addPreference(item);
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    protected String getFriendlyUpdateInterval(int interval) {
        String[] names = mRes.getStringArray(R.array.updates_schedule_entries);
        String[] values = mRes.getStringArray(R.array.updates_schedule_entry_values);
        for (int i = 0; i < values.length; i++) {
            if (Integer.valueOf(values[i]).equals(interval)) {
                return names[i];
            }
        }
        return "";
    }

    public void initiateDownload(String url, String fileUri, ManifestEntry entry) {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(entry.getName());
        req.setDestinationUri(Uri.parse(fileUri));
        req.setAllowedOverRoaming(false);
        req.setVisibleInDownloadsUi(false);
        long id = mDownloadManager.enqueue(req);
        try {
            entry.setDownloadId(id);
            entry.setDownloadStatus(DownloadManager.STATUS_PENDING);
            entry.setDownloadProgress(0);
            mDatabaseManager.updateDownloadInfo(entry);
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        startDownloadService(entry);
        Log.i(TAG,"initiateDownload: started download " + id);
    }

    public void stopDownload(ManifestEntry entry) {
        long downloadId = entry.getDownloadId();
        if (downloadId < 0 ) {
            Log.e(TAG, "stopDownload: not a valid download " + downloadId);
            return;
        }
        Log.i(TAG, "stopDownload: dequeuing download " + downloadId);
        mDownloadManager.remove(downloadId);
        removeDownload(entry);
    }

    public void removeDownload(ManifestEntry entry) {
        try {
            entry.setMd5sumLoc(null);
            entry.setDownloadId(-1);
            entry.setDownloadStatus(-1);
            entry.setDownloadProgress(-1);
            mDatabaseManager.updateDownloadInfo(entry);
            Log.i(TAG, "removeDownload: reset download status for " + entry.getName());
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    public void startDownloadService(ManifestEntry entry) {
        Intent dlService = new Intent(mContext, DownloadService.class);
        dlService.putExtra(Constants.EXTRA_MANIFEST_ENTRY, entry);
        mContext.startService(dlService);
    }

    public void showDialog(int id, final DownloadPreference pref) {
        switch (id) {
            case DIALOG_MANIFEST_ERROR:
                mAlertDialog = new AlertDialog.Builder(getActivity()).create();
                mAlertDialog.setTitle(R.string.alert_dialog_error_title);
                mAlertDialog.setMessage(getString(R.string
                        .alert_dialog_manifest_download_error));
                mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.okay),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                mAlertDialog.show();
                break;
            case DIALOG_CONFIRM_CANCEL:
                mAlertDialog = new AlertDialog.Builder(getActivity()).create();
                mAlertDialog.setTitle(R.string.alert_dialog_cancel_title);
                mAlertDialog.setMessage(getString(R.string
                        .alert_dialog_confirm_cancel));
                mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.okay),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pref.cancelDownload();
                        dialog.dismiss();
                    }
                });
                mAlertDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // Do nothing
                    }
                });
                mAlertDialog.show();
                break;
            case DIALOG_CONFIRM_DELETE:
                mAlertDialog = new AlertDialog.Builder(getActivity()).create();
                mAlertDialog.setTitle(R.string.alert_dialog_delete_title);
                mAlertDialog.setMessage(getString(R.string
                        .alert_dialog_confirm_delete));
                mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.okay),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!pref.deleteDownload()) {
                            Log.e(TAG, "deleteDownload failed");
                        }
                        dialog.dismiss();
                    }
                });
                mAlertDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // Do nothing
                    }
                });
                mAlertDialog.show();
                break;
        }
    }

    public PreferenceCategory getAvailableCategory() {
        return mAvailableCategory;
    }

    public void setChildActionMode(ActionMode actionMode) {
        mChildActionMode = actionMode;
    }

    public ActionMode getChildActionMode() {
        return mChildActionMode;
    }

}
