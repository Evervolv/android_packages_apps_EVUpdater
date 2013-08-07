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

package com.evervolv.updater.misc;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Environment;
import android.preference.Preference;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;

import com.evervolv.updater.FlashActivity;
import com.evervolv.updater.R;
import com.evervolv.updater.UpdatesFragment;
import com.evervolv.updater.db.ManifestEntry;

import java.io.File;

public class DownloadPreference extends Preference implements OnClickListener {

    public static final int STATE_NOTHING     = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DOWNLOADED  = 2;

    private Context mContext;
    private UpdatesFragment mParent;
    private LinearLayout mDownloadPref;
    private ProgressBar mProgress;
    private TextView mSummary;
    private ImageView mDownloadIcon;
    private ImageView mExpandArrow;

    private TableLayout mSlidingInfo;
    private boolean mIsDrawerOpen = false;

    private ActionMode mActionMode;
    private TextView mMd5sumLocal;

    private ManifestEntry mEntry;
    private int mState;
    private String mStorageDir;
    private boolean mInstalled;
    private boolean mNew;

    public DownloadPreference(Context context, UpdatesFragment parent, ManifestEntry entry) {
        super(context);
        mContext = context;
        mParent = parent;

        mEntry = entry;

        mStorageDir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/" + Constants.DOWNLOAD_DIRECTORY + mEntry.getType() + "/";
        mInstalled = Utils.getInstalledVersion().equals(mEntry.getName().replace(".zip", ""));
        mNew = Utils.isNewerThanInstalled(mEntry.getDate());

        setLayoutResource(R.layout.update_download);
        setTitle(mEntry.getName());
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        mProgress = (ProgressBar) view.findViewById(R.id.download_progress_bar);
        mSummary = (TextView) view.findViewById(android.R.id.summary);
        mDownloadIcon = (ImageView) view.findViewById(R.id.updates_icon);

        if  (mEntry.getType().equals(Constants.BUILD_TYPE_NIGHTLIES)) {
            mDownloadIcon.setImageResource(R.drawable.ic_pref_nightly_build);
        } else if  (mEntry.getType().equals(Constants.BUILD_TYPE_RELEASE)) {
            mDownloadIcon.setImageResource(R.drawable.ic_pref_release_build);
        } else if  (mEntry.getType().equals(Constants.BUILD_TYPE_TESTING)) {
            mDownloadIcon.setImageResource(R.drawable.ic_pref_testing_build);
        } else if (mEntry.getType().equals(Constants.BUILD_TYPE_GAPPS)) {
            mDownloadIcon.setImageResource(R.drawable.ic_pref_google_apps);
        }

        mExpandArrow = (ImageView) view.findViewById(R.id.updates_arrow);
        mExpandArrow.setImageResource(R.drawable.ic_pref_expand);
        mDownloadPref = (LinearLayout) view.findViewById(R.id.updates_pref);
        mDownloadPref.setOnClickListener(this);

        mSlidingInfo = (TableLayout) view.findViewById(R.id.tab_info);

        TextView txtDate = (TextView) view.findViewById(R.id.text_date);
        txtDate.setText(mEntry.getDate());

        TextView txtSize = (TextView) view.findViewById(R.id.text_size);
        txtSize.setText(mEntry.getFriendlySize());

        TextView txtFilename = (TextView) view.findViewById(R.id.text_filename);
        txtFilename.setText(mEntry.getName());

        TextView txtMd5sumServer = (TextView) view.findViewById(R.id.text_md5sum_server);
        txtMd5sumServer.setText(mEntry.getMd5sum());

        mMd5sumLocal = (TextView) view.findViewById(R.id.text_md5sum_local);

        File zipFile = new File(mStorageDir + mEntry.getName());
        File zipPartial = new File(mStorageDir + mEntry.getName() + ".partial");

        if (zipFile.exists()) {
            if (Constants.DEBUG) Log.d(Constants.TAG,
                    "DownloadPreference: " + mEntry.getName() + " exists");
            updateState(STATE_DOWNLOADED);
        } else if (zipPartial.exists()) {
            if (Constants.DEBUG) Log.d(Constants.TAG,
                    "DownloadPreference: " + mEntry.getName() + ".partial exists");
            if (mEntry.getDownloadId() > 0) {
                if (Constants.DEBUG) Log.d(Constants.TAG,
                        "DownloadPreference: " + mEntry.getName() + " tracking download "
                                + mEntry.getDownloadId());
                mParent.startDownloadService(mEntry);
                updateState(STATE_DOWNLOADING);
                /* Reinitialize progress bar */
                if (mEntry.getDownloadStatus() > 0) {
                    updateDownloadInternal();
                }
            } else {
                /* File exists but not being tracked
                   just delete it and start over */
                zipPartial.delete();
                updateState(STATE_NOTHING);
            }
        } else {
            updateState(STATE_NOTHING);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mDownloadPref) {
            if (!mIsDrawerOpen) {
                // If another preference is expanded, collapse it.
                int prefCount = mParent.getAvailableCategory().getPreferenceCount();
                for (int i = 0; i < prefCount; i++) {
                    DownloadPreference pref = (DownloadPreference)
                            mParent.getAvailableCategory().getPreference(i);
                    if (pref.mActionMode != null) {
                        pref.mActionMode.finish();
                    }
                }
                animateView(mSlidingInfo, ExpandCollapseAnimation.EXPAND);
                mActionMode = mParent.getActivity().startActionMode(mActionModeCallback);
                mParent.setChildActionMode(mActionMode);
                mIsDrawerOpen = true;
            } else {
                animateView(mSlidingInfo, ExpandCollapseAnimation.COLLAPSE);
                if (mActionMode != null) {
                    mActionMode.finish();
                }
                mIsDrawerOpen = false;
            }
        }
    }

    private void animateView(final View target, final int type) {
        Animation anim = new ExpandCollapseAnimation(target, type);
        anim.setDuration(350);
        target.startAnimation(anim);
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            int actionMenu = -1;
                switch (mState) {
                    case STATE_NOTHING:
                        actionMenu = R.menu.action_menu_nothing;
                        break;
                    case STATE_DOWNLOADING:
                        actionMenu = R.menu.action_menu_downloading;
                        break;
                    case STATE_DOWNLOADED:
                        actionMenu = R.menu.action_menu_downloaded;
                        break;
                }
            inflater.inflate(actionMenu, menu);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // Remove the flash button for gapps
            if (mEntry.getType().equals(Constants.BUILD_TYPE_GAPPS) &&
                    mState == STATE_DOWNLOADED) {
                menu.removeItem(R.id.menu_flash);
            }
            mExpandArrow.setImageResource(R.drawable.ic_pref_collapse);
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_download:
                    String url = Constants.FETCH_URL + mEntry.getName();
                    String fileUri = "file://" + mStorageDir + mEntry.getName() + ".partial";
                    File downloadDir = new File(mStorageDir);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }
                    mParent.initiateDownload(url, fileUri, mEntry);
                    if (Constants.DEBUG) Log.d(Constants.TAG,
                            "DownloadPreference: " + mEntry.getName() + " now tracking download "
                                    + mEntry.getDownloadId());
                    updateDownloadInternal();
                    mode.finish();
                    break;
                case R.id.menu_cancel:
                    mParent.showDialog(UpdatesFragment.DIALOG_CONFIRM_CANCEL, DownloadPreference.this);
                    mode.finish();
                    break;
                case R.id.menu_delete:
                    mParent.showDialog(UpdatesFragment.DIALOG_CONFIRM_DELETE, DownloadPreference.this);
                    mode.finish();
                    break;
                case R.id.menu_changelog:
                    ChangelogDialog dlg = new ChangelogDialog(mEntry, mParent.getActivity());
                    dlg.show(mParent.getChildFragmentManager(), mEntry.getMd5sum());
                    break;
                case R.id.menu_flash:
                    Intent intent = new Intent(mContext, FlashActivity.class);
                    intent.putExtra(Constants.EXTRA_MANIFEST_ENTRY, mEntry);
                    mParent.startActivity(intent);
                    mode.finish();
                    break;
                default:
                    return false;
            }
            return true;
        }

        public void onDestroyActionMode(ActionMode mode) {
            animateView(mSlidingInfo, ExpandCollapseAnimation.COLLAPSE);
            mIsDrawerOpen = false;
            mActionMode = null;
            mParent.setChildActionMode(mActionMode);
            mExpandArrow.setImageResource(R.drawable.ic_pref_expand);
        }
    };

    public void updateState(int state) {
        if (Constants.DEBUG) Log.d(Constants.TAG,
                "DownloadPreference: " + mEntry.getName() + " updateState " + state);
        mState = state;
        switch (state) {
            case STATE_NOTHING:
                mProgress.setVisibility(View.GONE);
                if (mInstalled) {
                    mSummary.setText(R.string.status_installed);
                    mSummary.setVisibility(View.VISIBLE);
                } else if (mNew) {
                    mSummary.setText(R.string.status_new);
                    mSummary.setVisibility(View.VISIBLE);
                } else {
                    mSummary.setVisibility(View.GONE);
                }
                mMd5sumLocal.setText(R.string.build_info_md5sum_local_not_exist);
                mMd5sumLocal.setTextColor(Color.LTGRAY);
                break;
            case STATE_DOWNLOADING:
                mProgress.setVisibility(View.VISIBLE);
                mSummary.setVisibility(View.GONE);
                mMd5sumLocal.setText(R.string.build_info_md5sum_local_not_exist);
                mMd5sumLocal.setTextColor(Color.LTGRAY);
                break;
            case STATE_DOWNLOADED:
                mProgress.setVisibility(View.GONE);
                if (mInstalled) {
                    mSummary.setText(R.string.status_downloaded_and_installed);
                } else {
                    mSummary.setText(R.string.status_downloaded);
                }
                mSummary.setVisibility(View.VISIBLE);
                if (mEntry.getMd5sumLoc() != null) {
                    mMd5sumLocal.setText(mEntry.getMd5sumLoc());
                    if (mEntry.getMd5sumLoc().equals(mEntry.getMd5sum())) {
                        mMd5sumLocal.setTextColor(Color.GREEN);
                    } else {
                        mMd5sumLocal.setTextColor(Color.RED);
                    }
                }
                break;
        }
    }

    public void updateDownload(ManifestEntry entry) {
        if (entry.getDownloadId() != mEntry.getDownloadId()) {
            return;
        }
        mEntry.setMd5sumLoc(entry.getMd5sumLoc());
        mEntry.setDownloadStatus(entry.getDownloadStatus());
        mEntry.setDownloadProgress(entry.getDownloadProgress());
        updateDownloadInternal();
    }

    private void updateDownloadInternal() {
        switch (mEntry.getDownloadStatus()) {
            case DownloadManager.STATUS_RUNNING:
                if (mState != STATE_DOWNLOADING) {
                    updateState(STATE_DOWNLOADING);
                }
                mProgress.setIndeterminate(mEntry.getDownloadProgress() <= 0);
                mProgress.setProgress(mEntry.getDownloadProgress());
                break;
            case DownloadManager.STATUS_PENDING:
            case DownloadManager.STATUS_PAUSED:
                if (mState != STATE_DOWNLOADING) {
                    updateState(STATE_DOWNLOADING);
                }
                mProgress.setIndeterminate(true);
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                updateState(STATE_DOWNLOADED);
                break;
            case DownloadManager.STATUS_FAILED:
            default:
                updateState(STATE_NOTHING);
                break;
        }
    }

    public void cancelDownload() {
        mParent.stopDownload(mEntry);
        updateState(STATE_NOTHING);
    }

    public boolean deleteDownload() {
        mParent.removeDownload(mEntry);
        updateState(STATE_NOTHING);
        File zipFile = new File(mStorageDir + mEntry.getName());
        return zipFile.exists() && zipFile.delete();
    }

}
