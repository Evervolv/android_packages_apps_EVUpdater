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
import com.evervolv.updater.db.DatabaseManager;
import com.evervolv.updater.misc.Constants;
import com.evervolv.updater.services.UpdateManifestService;

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

        mDbType = DatabaseManager.NIGHTLIES;
        mAvailableCategory = (PreferenceCategory) mPrefSet
                .findPreference(AVAILABLE_UPDATES_CAT);
        Log.d(TAG, "onCreate");
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

    @Override
    protected String getUpdateCheckAction() {
        return UpdateManifestService.ACTION_UPDATE_CHECK_NIGHTLY;
    }

}
