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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.evervolv.updater.R;
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.RecoveryScriptBuilder;
import com.evervolv.updater.misc.SwipeDismissListViewTouchListener;
import com.evervolv.updater.misc.afiledialog.FileChooserDialog;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FlashActivity extends Activity {

    private ListView mGappsListView;
    private ZipAdapter mAdapter;
    private List<Zip> mZipItems = new ArrayList<Zip>();
    private String mFileName;
    private String mBuildType;
    private String mSDCardPath;
    private ManifestEntry mEntry;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.flash_dialog);

        mEntry = getIntent().getParcelableExtra(Constants.EXTRA_MANIFEST_ENTRY);
        mFileName = mEntry.getName();
        mBuildType = mEntry.getType();

        mSDCardPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";

        mZipItems.add(new Zip(mFileName, mBuildType));
        mAdapter = new ZipAdapter(this, mZipItems);

        mGappsListView = (ListView) findViewById(R.id.gapps_list);
        SwipeDismissListViewTouchListener swipeDismissTouchListener =
                new SwipeDismissListViewTouchListener(
                        mGappsListView,
                        new SwipeDismissListViewTouchListener.OnDismissCallback() {
                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {
                                    mAdapter.remove(mAdapter.getItem(position));
                                }
                                mAdapter.notifyDataSetChanged();
                            }
                        });
        swipeDismissTouchListener.setEnabled(true);
        mGappsListView.setOnScrollListener(swipeDismissTouchListener.makeScrollListener());
        mGappsListView.setOnTouchListener(swipeDismissTouchListener);
        mGappsListView.setItemsCanFocus(true);
        mGappsListView.setAdapter(mAdapter);
        mGappsListView.performItemClick(null, 0, mGappsListView.getFirstVisiblePosition());

        Button cancelButton = (Button) findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button rebootButton = (Button) findViewById(R.id.button_reboot);
        rebootButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tempTwrpDialog();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.flash_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add:
                FileChooserDialog dialog = new FileChooserDialog(this);
                dialog.addListener(new FileChooserDialog.OnFileSelectedListener() {
                    public void onFileSelected(Dialog source, File file) {
                        mZipItems.add(new Zip(file));
                        mAdapter.notifyDataSetChanged();
                        source.dismiss();
                    }
                    public void onFileSelected(Dialog source, File folder, String name) {
                        //Pass, called when file is created, we should disable that
                    }
                });
                dialog.setFilter(".*zip");
                dialog.setShowOnlySelectable(true);
                dialog.loadFolder(Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/" + Constants.DOWNLOAD_DIRECTORY + Constants.BUILD_TYPE_GAPPS);
                dialog.show();
                return true;
        }
        return false;
    }

    //FIXME
    private void tempTwrpDialog() {
        AlertDialog.Builder twrpDialog = new AlertDialog.Builder(this);
        twrpDialog.setTitle(R.string.alert_dialag_warning_title);
        twrpDialog.setMessage("Backup?");

        twrpDialog.setPositiveButton(R.string.okay,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Testing do both
                        RecoveryScriptBuilder script = new RecoveryScriptBuilder(RecoveryScriptBuilder.TWRP,
                                mZipItems, RecoveryScriptBuilder.BACKUP | RecoveryScriptBuilder.WIPE);
                        if (!script.create()) {
                            Toast t = Toast.makeText(getApplicationContext(), "Unable to create recovery script", 30);
                            t.show();
                        }
                        RecoveryScriptBuilder script2 = new RecoveryScriptBuilder(RecoveryScriptBuilder.CWM,
                                mZipItems, RecoveryScriptBuilder.BACKUP | RecoveryScriptBuilder.WIPE);
                        if (!script2.create()) {
                            Toast t = Toast.makeText(getApplicationContext(), "Unable to create recovery script", 30);
                            t.show();
                        } else {
                            PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                            //pm.reboot("recovery");
                        }
                        dialog.dismiss();
                    }
                });
        twrpDialog.setNegativeButton("NO",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Testing do both
                        RecoveryScriptBuilder script = new RecoveryScriptBuilder(RecoveryScriptBuilder.TWRP,
                                mZipItems, 0);
                        if (!script.create()) {
                            Toast t = Toast.makeText(getApplicationContext(), "Unable to create recovery script", 30);
                            t.show();
                        }
                        RecoveryScriptBuilder script2 = new RecoveryScriptBuilder(RecoveryScriptBuilder.CWM,
                                mZipItems, 0);
                        if (!script2.create()) {
                            Toast t = Toast.makeText(getApplicationContext(), "Unable to create recovery script", 30);
                            t.show();
                        } else {
                            PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                            //pm.reboot("recovery");
                        }
                        dialog.dismiss();
                    }
                });
        twrpDialog.show();
    }

    class ZipAdapter extends ArrayAdapter<Zip>{
        private List<Zip> mZipList = new ArrayList<Zip>();
        private Context mContext;

        public ZipAdapter(Context context, List<Zip> items) {
            super(context, R.layout.listview_flash_item, items);
            mContext = context;
            mZipList = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.listview_flash_item, parent, false);
            }

            TextView zipName = (TextView) convertView.findViewById(R.id.zip_name);
            zipName.setText(mZipList.get(position).getFileName());

            String buildType = mZipList.get(position).getBuildType();
            int iconResource = -1;
            if (buildType.equals(Constants.BUILD_TYPE_NIGHTLIES)) {
                iconResource = R.drawable.ic_pref_nightly_build;
            } else if (buildType.equals(Constants.BUILD_TYPE_RELEASE)) {
                iconResource = R.drawable.ic_pref_release_build;
            } else if (buildType.equals(Constants.BUILD_TYPE_TESTING)) {
                iconResource = R.drawable.ic_pref_testing_build;
            } else if (buildType.equals(Constants.BUILD_TYPE_GAPPS)) {
                iconResource = R.drawable.ic_pref_google_apps;
            }
            ImageView zipIcon = (ImageView) convertView.findViewById(R.id.zip_icon);
            zipIcon.setImageResource(iconResource);

            return convertView;
        }
    }

    public class Zip {
        private String mFileName;
        private String mBuildType;
        private String mPath = null;

        public Zip(String filename, String type) {
            mFileName = filename;
            mBuildType = type;
        }

        public Zip(File file) {
            mFileName = file.getName();
            mBuildType = Constants.BUILD_TYPE_GAPPS; // Just assume
            mPath = file.getAbsolutePath();
        }

        public String getFileName() {
            return mFileName;
        }

        public String getBuildType() {
            return mBuildType;
        }

        public String getPath() {
            if (mPath == null) {
                return Constants.DOWNLOAD_DIRECTORY + mBuildType + "/" + mFileName;
            } else {
                return mPath.replace(mSDCardPath,"");
            }
        }
    }

}
