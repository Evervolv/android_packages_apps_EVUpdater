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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.evervolv.updater.db.DatabaseManager;
import com.evervolv.updater.db.ManifestEntry;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.misc.Utils;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class UpdateManifestService extends IntentService {

    private static final String TAG = Constants.TAG;

    /* Intent extra fields */
    private static final String EXTRA_MANIFEST_ERROR  = Constants.EXTRA_MANIFEST_ERROR;
    private static final String EXTRA_MANIFEST_ENTRY  = Constants.EXTRA_MANIFEST_ENTRY;
    private static final String EXTRA_MANIFEST_TYPE   = Constants.EXTRA_MANIFEST_TYPE;
    private static final String EXTRA_SCHEDULE_UPDATE = Constants.EXTRA_SCHEDULE_UPDATE;
    private static final String EXTRA_UPDATE_NON_INTERACTIVE = Constants.EXTRA_UPDATE_NON_INTERACTIVE;

    /* Intent actions */
    private static final String ACTION_UPDATE_CHECK_NIGHTLY  = Constants.ACTION_UPDATE_CHECK_NIGHTLY;
    private static final String ACTION_UPDATE_CHECK_RELEASE  = Constants.ACTION_UPDATE_CHECK_RELEASE;
    private static final String ACTION_UPDATE_CHECK_TESTING  = Constants.ACTION_UPDATE_CHECK_TESTING;
    private static final String ACTION_UPDATE_CHECK_GAPPS    = Constants.ACTION_UPDATE_CHECK_GAPPS;
    private static final String ACTION_UPDATE_NOTIFY_NEW     = Constants.ACTION_UPDATE_NOTIFY_NEW;
    private static final String ACTION_CHECK_FINISHED        = Constants.ACTION_UPDATE_CHECK_FINISHED;
    private static final String ACTION_BOOT_COMPLETED        = Constants.ACTION_BOOT_COMPLETED;

    private SharedPreferences preferences;
    private DatabaseManager databaseManager;

    public UpdateManifestService() {
        super("UpdateManifestService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(Constants.APP_NAME, Context.MODE_MULTI_PROCESS);
        databaseManager = new DatabaseManager(this);
        databaseManager.open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        databaseManager.close();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        long now = System.currentTimeMillis();
        boolean error = false;
        String action = intent.getAction();
        boolean nonInteractive = intent.getBooleanExtra(EXTRA_UPDATE_NON_INTERACTIVE, false);
        boolean schedule = intent.getBooleanExtra(EXTRA_SCHEDULE_UPDATE, false);
        String buildType;
        int updateFreq;
        long lastCheck;
        if (action.equals(ACTION_UPDATE_CHECK_NIGHTLY)) {
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK_NIGHTLY, now).commit();
            if (schedule) {
                updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_NIGHTLY,
                        Constants.UPDATE_DEFAULT_NIGHTLY);
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_NIGHTLY, updateFreq, now, false);
                return;
            }
            error = handleManifest(Constants.API_URL_NIGHTLY, Constants.BUILD_TYPE_NIGHTLIES);
            buildType = Constants.BUILD_TYPE_NIGHTLIES;
        } else if (action.equals(ACTION_UPDATE_CHECK_RELEASE)) {
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK_RELEASE, now).commit();
            if (schedule) {
                updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_RELEASE,
                        Constants.UPDATE_DEFAULT_RELEASE);
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_RELEASE, updateFreq, now, false);
                return;
            }
            error = handleManifest(Constants.API_URL_RELEASE, Constants.BUILD_TYPE_RELEASE);
            buildType = Constants.BUILD_TYPE_RELEASE;
        } else if (action.equals(ACTION_UPDATE_CHECK_TESTING)) {
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK_TESTING, now).commit();
            if (schedule) {
                updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_TESTING,
                        Constants.UPDATE_DEFAULT_TESTING);
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_TESTING, updateFreq, now, false);
                return;
            }
            error = handleManifest(Constants.API_URL_TESTING, Constants.BUILD_TYPE_TESTING);
            buildType = Constants.BUILD_TYPE_TESTING;
        } else if (action.equals(ACTION_UPDATE_CHECK_GAPPS)) {
            error = handleManifest(Constants.API_URL_GAPPS, Constants.BUILD_TYPE_GAPPS);
            buildType = Constants.BUILD_TYPE_GAPPS;
        } else if (action.equals(ACTION_BOOT_COMPLETED)) {
            if (Constants.DEBUG) Log.d(TAG, "onBootComplete");
            /* Nightlies */
            updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_NIGHTLY,
                    Constants.UPDATE_DEFAULT_NIGHTLY);
            lastCheck = preferences.getLong(
                    Constants.PREF_LAST_UPDATE_CHECK_NIGHTLY, now);

            if (updateFreq > Constants.UPDATE_CHECK_ONBOOT ) {
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_NIGHTLY, updateFreq, lastCheck, false);
            } else if (updateFreq == Constants.UPDATE_CHECK_ONBOOT) {
                /* Schedule check 2 mins from now to give radio time to connect */
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_NIGHTLY, 2 * 60, now, true);
            }
            /* Releases */
            updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_RELEASE,
                    Constants.UPDATE_DEFAULT_RELEASE);
            lastCheck = preferences.getLong(
                    Constants.PREF_LAST_UPDATE_CHECK_RELEASE, now);

            if (updateFreq > Constants.UPDATE_CHECK_ONBOOT ) {
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_RELEASE, updateFreq, lastCheck, false);
            } else if (updateFreq == Constants.UPDATE_CHECK_ONBOOT) {
                /* Schedule check 2 mins from now to give radio time to connect */
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_RELEASE, 2 * 60, now, true);
            }
            /* Testing */
            updateFreq = preferences.getInt(Constants.PREF_UPDATE_SCHEDULE_TESTING,
                    Constants.UPDATE_DEFAULT_TESTING);
            lastCheck = preferences.getLong(
                    Constants.PREF_LAST_UPDATE_CHECK_TESTING, now);

            if (updateFreq > Constants.UPDATE_CHECK_ONBOOT) {
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_TESTING, updateFreq, lastCheck, false);
            } else if (updateFreq == Constants.UPDATE_CHECK_ONBOOT) {
                /* Schedule check 2 mins from now to give radio time to connect */
                scheduleUpdateCheck(ACTION_UPDATE_CHECK_TESTING, 2 * 60, now, true);
            }
            return;
        } else {
            Log.e(TAG, "Unimplemented: " + action);
            return;
        }
        if (!nonInteractive) {
            Intent checkIntent = new Intent();
            checkIntent.setAction(ACTION_CHECK_FINISHED);
            checkIntent.putExtra(EXTRA_MANIFEST_ERROR, error);
            checkIntent.putExtra(EXTRA_MANIFEST_TYPE, buildType);
            sendBroadcast(checkIntent);
        }
    }

    private boolean handleManifest(String url, String updateType) {
        boolean error = false;
        String jsonString;
        try {
            jsonString = fetchManifest(url);
            processManifest(jsonString, updateType);
        } catch (Exception e) {
            e.printStackTrace();
            error = true;
        }
        return error;
    }

    private String fetchManifest(String url) throws IOException, HttpException {
        if (Constants.DEBUG) Log.d(TAG, "Fetching " + url +
                Utils.getDevice(getApplicationContext()));
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url + Utils.getDevice(
                getApplicationContext()));
        HttpResponse response = client.execute(httpGet);
        String json = null;
        if (response.getStatusLine().getStatusCode() == 200) {
            json = EntityUtils.toString(response.getEntity());
        } else {
            throw new HttpException("Failed to fetch manifest");
        }
        return json;
    }

    private void processManifest(String jsonString, String updateType)
            throws JSONException, SQLiteException {
        JSONArray entries = new JSONArray(jsonString);
        databaseManager.update(updateType, entries);
        for (int i=entries.length()-1; i>=0; i--) {
            JSONObject entry = entries.getJSONObject(i);
            if (Utils.isNewerThanInstalled(entry.optString(ManifestEntry.COLUMN_DATE))) {
                Log.i(TAG, "Found new update " + entry.optString(ManifestEntry.COLUMN_NAME));
                Intent notify = new Intent();
                notify.setAction(ACTION_UPDATE_NOTIFY_NEW);
                notify.putExtra(EXTRA_MANIFEST_ENTRY, new ManifestEntry(entry));
                sendOrderedBroadcast(notify, null);
                break;
            }
        }
    }

    private void scheduleUpdateCheck(String action, int interval, long lastCheck, boolean oneshot) {

        Intent updateCheck = new Intent(this, UpdateManifestService.class);
        updateCheck.setAction(action);
        updateCheck.putExtra(EXTRA_UPDATE_NON_INTERACTIVE, true);
        PendingIntent pi = PendingIntent.getService(this, 0, updateCheck,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        long intervalMillis = ((long)interval) * 1000;
        long now = System.currentTimeMillis();
        if (!oneshot) {
            if (interval > Constants.UPDATE_CHECK_ONBOOT) {
                am.setRepeating(AlarmManager.RTC, lastCheck + intervalMillis, intervalMillis, pi);
                Log.i(TAG, "Scheduled update check for "
                        + (((lastCheck + intervalMillis) - now) / 1000) + " seconds from now"
                        + " repeating every " + interval + " seconds");
            }
        } else {
            am.set(AlarmManager.RTC, lastCheck + intervalMillis, pi);
            Log.i(TAG, "Scheduled update check for "
                    + (((lastCheck + intervalMillis) - now) / 1000) + " seconds from now");
        }
    }

}
