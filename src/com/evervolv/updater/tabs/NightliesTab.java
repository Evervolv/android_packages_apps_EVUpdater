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

package com.evervolv.updater.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.evervolv.updater.R;
import com.evervolv.updater.UpdatesFragment;
import com.evervolv.updater.misc.Constants;

public class NightliesTab extends UpdatesFragment implements OnPreferenceChangeListener {

    private static final String AVAILABLE_UPDATES_CAT = "pref_updates_nightlies_category_available_updates";

    private ListPreference mCheckUpdates;
    private PreferenceScreen mPrefSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tab_nightlies);

        mPrefSet = getPreferenceScreen();

        mCheckUpdates = (ListPreference) mPrefSet
                .findPreference(Constants.PREF_UPDATE_SCHEDULE_NIGHTLY);
        mCheckUpdates.setSummary(mCheckUpdates.getEntry());
        mCheckUpdates.setOnPreferenceChangeListener(this);

        mUpdateType = Constants.BUILD_TYPE_NIGHTLIES;
        mUpdateAction = Constants.ACTION_UPDATE_CHECK_NIGHTLY;

        mAvailableCategory = (PreferenceCategory) mPrefSet
                .findPreference(AVAILABLE_UPDATES_CAT);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mCheckUpdates) {
            int value = Integer.valueOf((String) newValue);
            mCheckUpdates.setSummary(getFriendlyUpdateInterval(value));
            SharedPreferences sPrefs = getActivity().getSharedPreferences(Constants.APP_NAME, Context.MODE_MULTI_PROCESS);
            sPrefs.edit().putInt(Constants.PREF_UPDATE_SCHEDULE_NIGHTLY,
                    value).apply();
            startUpdateManifestService(true);
            return true;
        }
        return false;
    }

}
