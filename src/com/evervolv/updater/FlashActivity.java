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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.RecoveryScriptBuilder;
import com.evervolv.updater.misc.SwipeDismissListViewTouchListener;

import java.io.File;
import java.sql.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlashActivity extends Activity {

    private static final String RECOVERY_PREF = "recovery_pref";

    private ListView mGappsListView;
    private ZipAdapter mAdapter;
    private List<Zip> mZipItems = new ArrayList<Zip>();
    private String mFileName;
    private String mBuildType;
    private String mSDCardPath;
    private ManifestEntry mEntry;
    private ActionMode mActionMode;

    private ListView mFileListView;
    private LinearLayout mZipListLayout;
    private LinearLayout mFileExplorerLayout;
    private FileArrayAdapter mFileAdapter;

    private CheckBox mWipeDataCheckbox;
    private CheckBox mWipeCacheCheckbox;
    private CheckBox mWipeDalvikCheckbox;
    private CheckBox mBackupCheckbox;
    private int mWhichRecovery;

    private SharedPreferences mSharedPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.flash_activity);

        mSharedPrefs = getSharedPreferences(Constants.APP_NAME, Context.MODE_PRIVATE);

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

        mZipListLayout = (LinearLayout) findViewById(R.id.zip_list_layout);
        mFileExplorerLayout = (LinearLayout) findViewById(R.id.file_explorer_layout);
        mFileListView = (ListView) findViewById(R.id.file_list);
        mFileListView.setOnItemClickListener( new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                Item o = mFileAdapter.getItem(position);
                if (Constants.DEBUG) Log.d("FlashActivity", "FileClick: " + o.getType());
                if(o.getType() == Item.TYPE_UP || o.getType() == Item.TYPE_DIRECTORY) {
                    fillFileExplorer(new File(o.getPath()));
                } else if (o.getType() == Item.TYPE_ZIP) {
                    mZipItems.add(new Zip(new File(o.getPath())));
                    mActionMode.finish();
                    mActionMode = null;
                } else {
                    //TODO: Add toast for wrong file type.
                }
            }
        });

        mWipeDataCheckbox = (CheckBox) findViewById(R.id.checkbox_data);
        mWipeCacheCheckbox = (CheckBox) findViewById(R.id.checkbox_cache);
        mWipeDalvikCheckbox = (CheckBox) findViewById(R.id.checkbox_dalvik);
        mBackupCheckbox = (CheckBox) findViewById(R.id.checkbox_backup);

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
                showRebootDialog();
            }
        });
    }

    private void fillFileExplorer(File f) {
        File[]dirs = f.listFiles();
        List<Item>dir = new ArrayList<Item>();
        List<Item>fls = new ArrayList<Item>();

        try {
            for(File ff: dirs) {
               Date lastModDate = new Date(ff.lastModified());
               DateFormat formater = DateFormat.getDateTimeInstance();
               String date_modify = formater.format(lastModDate);
               if (ff.isDirectory()) {
                   File[] fbuf = ff.listFiles();
                   int buf = 0;
                   if (fbuf != null) {
                       buf = fbuf.length;
                   } else {
                       buf = 0;
                   }

                   String num_item = String.valueOf(buf);
                   if (buf == 0) {
                       num_item = num_item + " item";
                   } else {
                       num_item = num_item + " items";
                   }

                   dir.add(new Item(ff.getName(), num_item,date_modify,
                           ff.getAbsolutePath(), R.drawable.directory_icon, Item.TYPE_DIRECTORY));
               } else {
                   if (ff.getName().endsWith(".zip")) {
                       if (Constants.DEBUG) Log.d("FlashActivity", "Found a zip!");
                       fls.add(new Item(ff.getName(), ff.length() + " Byte",
                               date_modify, ff.getAbsolutePath(),R.drawable.zip_icon, Item.TYPE_ZIP));
                   } else {
                       fls.add(new Item(ff.getName(), ff.length() + " Byte",
                               date_modify, ff.getAbsolutePath(),R.drawable.file_icon, Item.TYPE_NONZIP));
                   }
               }
            }
        } catch(Exception e) {
            // Do nothing
        }
        Collections.sort(dir);
        Collections.sort(fls);
        dir.addAll(fls);

        if(!f.getName().equalsIgnoreCase("sdcard")) {
            dir.add(0,new Item("..", "Parent Directory","", f.getParent(),
                    R.drawable.directory_up, Item.TYPE_UP));
        }

        mFileAdapter = new FileArrayAdapter(this,R.layout.file_view,dir);
        mFileListView.setAdapter(mFileAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.flash_activity_menu, menu);
        View v = (View) menu.findItem(R.id.menu_recovery).getActionView();
        Spinner recoveryList = (Spinner) v.findViewById(R.id.recovery_spinner);
        recoveryList.setSelection(mSharedPrefs.getInt(RECOVERY_PREF,
                RecoveryScriptBuilder.TWRP));
        recoveryList.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                switch (position) {
                    case RecoveryScriptBuilder.CWM:
                        mWipeDalvikCheckbox.setChecked(false);
                        mWipeDalvikCheckbox.setEnabled(false);
                        break;
                    case RecoveryScriptBuilder.TWRP:
                        mWipeDalvikCheckbox.setEnabled(true);
                        break;
                }
                mSharedPrefs.edit().putInt(RECOVERY_PREF, position).commit();
                mWhichRecovery = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add:
                if (mActionMode == null) {
                    mActionMode = startActionMode(mActionModeCallback);
                }
        }
        return false;
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            fillFileExplorer(new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/" + Constants.DOWNLOAD_DIRECTORY + Constants.BUILD_TYPE_GAPPS));
            mFileExplorerLayout.setVisibility(View.VISIBLE);
            mZipListLayout.setVisibility(View.GONE);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return true;
        }

        public void onDestroyActionMode(ActionMode mode) {
            mFileExplorerLayout.setVisibility(View.GONE);
            mZipListLayout.setVisibility(View.VISIBLE);
            mActionMode = null;
            mFileAdapter = null;
            mFileListView.setAdapter(null);
        }
    };

    private void showRebootDialog() {
        AlertDialog.Builder rebootDialog = new AlertDialog.Builder(this);
        rebootDialog.setTitle(R.string.alert_dialag_reboot_title);
        rebootDialog.setMessage(R.string.alert_dialag_reboot_message);

        rebootDialog.setPositiveButton(R.string.okay,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int flags = 0;

                        if (mWipeDataCheckbox.isChecked()) {
                            flags |= RecoveryScriptBuilder.WIPE_DATA;
                        }
                        if (mWipeCacheCheckbox.isChecked()) {
                            flags |= RecoveryScriptBuilder.WIPE_CACHE;
                        }
                        if (mWipeDalvikCheckbox.isChecked()) {
                            flags |= RecoveryScriptBuilder.WIPE_DALVIK;
                        }
                        if (mBackupCheckbox.isChecked()) {
                            flags |= RecoveryScriptBuilder.BACKUP;
                        }

                        RecoveryScriptBuilder script = new RecoveryScriptBuilder(mWhichRecovery,
                                mZipItems, flags);

                        if (script.create()) {
                            PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                            pm.reboot("recovery");
                        } else {
                            Toast t = Toast.makeText(getApplicationContext(), R.string.toast_failed_recovery_script, 30);
                            t.show();
                        }
                        dialog.dismiss();
                    }
                });
        rebootDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        rebootDialog.show();
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

    public class FileArrayAdapter extends ArrayAdapter<Item>{

        private Context c;
        private int id;
        private List<Item> items;

        public FileArrayAdapter(Context context, int textViewResourceId,
                List<Item> objects) {
            super(context, textViewResourceId, objects);
            c = context;
            id = textViewResourceId;
            items = objects;
        }
        public Item getItem(int i) {
             return items.get(i);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(id, null);
            }

            /* create a new view of my layout and inflate it in the row */
            //convertView = ( RelativeLayout ) inflater.inflate( resource, null );
            final Item o = items.get(position);
            if (o != null) {
            TextView t1 = (TextView) v.findViewById(R.id.TextView01);
            TextView t2 = (TextView) v.findViewById(R.id.TextView02);
            TextView t3 = (TextView) v.findViewById(R.id.TextViewDate);
            /* Take the ImageView from layout and set the city's image */
            ImageView icon = (ImageView) v.findViewById(R.id.fd_Icon1);
            icon.setImageResource(o.getIconRes());

            if(t1!=null)
                t1.setText(o.getName());
            if(t2!=null)
                t2.setText(o.getData());
            if(t3!=null)
                t3.setText(o.getDate());
            }
            return v;
        }

    }

    public class Item implements Comparable<Item> {

        public static final int TYPE_UP         = 0;
        public static final int TYPE_DIRECTORY  = 1;
        public static final int TYPE_ZIP        = 2;
        public static final int TYPE_NONZIP     = 3;

        private String name;
        private String data;
        private String date;
        private String path;
        private int mIconResource;
        private int mType;

        public Item(String n,String d, String dt, String p, int icon, int type) {
            name = n;
            data = d;
            date = dt;
            path = p;
            mIconResource = icon;
            mType = type;
        }

        public int compareTo(Item o) {
            if(this.name != null) {
                return this.name.toLowerCase().compareTo(o.getName().toLowerCase());
            } else {
                throw new IllegalArgumentException();
            }
        }

        public String getName() { return name; }
        public String getData() { return data; }
        public String getDate() { return date; }
        public String getPath(){ return path; }
        public int getIconRes() { return mIconResource; }
        public int getType(){ return mType; }
    }

}
