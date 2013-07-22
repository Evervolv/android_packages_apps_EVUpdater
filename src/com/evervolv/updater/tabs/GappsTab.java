package com.evervolv.updater.tabs;

import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.evervolv.updater.R;
import com.evervolv.updater.UpdatesFragment;
import com.evervolv.updater.db.DatabaseManager;
import com.evervolv.updater.services.UpdateManifestService;

public class GappsTab extends UpdatesFragment {

    private static final String AVAILABLE_UPDATES_CAT = "pref_updates_gapps_category_available_updates";

    private PreferenceScreen mPrefSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tab_gapps);

        mPrefSet = getPreferenceScreen();

        mDbType = DatabaseManager.GAPPS;
        mAvailableCategory = (PreferenceCategory) mPrefSet
                .findPreference(AVAILABLE_UPDATES_CAT);
    }

    @Override
    protected String getUpdateCheckAction() {
        return UpdateManifestService.ACTION_UPDATE_CHECK_GAPPS;
    }
}
