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

import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.evervolv.updater.R;
import com.evervolv.updater.UpdatesFragment;
import com.evervolv.updater.misc.Constants;

public class GappsTab extends UpdatesFragment {

    private static final String AVAILABLE_UPDATES_CAT = "pref_updates_gapps_category_available_updates";

    private PreferenceScreen mPrefSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tab_gapps);

        mPrefSet = getPreferenceScreen();

        mUpdateType = Constants.BUILD_TYPE_GAPPS;
        mUpdateAction = Constants.ACTION_UPDATE_CHECK_GAPPS;

        mAvailableCategory = (PreferenceCategory) mPrefSet
                .findPreference(AVAILABLE_UPDATES_CAT);
    }

}
