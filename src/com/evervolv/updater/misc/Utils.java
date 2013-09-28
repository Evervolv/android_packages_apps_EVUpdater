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

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.evervolv.updater.R;

public class Utils {

    private Utils() { }

    public static String getDevice(Context context) {
        String overrideName = context.getResources().getString(
                R.string.device_prop_override);
        if (!overrideName.equals("")) {
            if (Constants.DEBUG) Log.d(Constants.TAG, "Overriding device name!");
            return overrideName;
        }
        return SystemProperties.get("ro.product.device");
    }

    public static String getInstalledVersion() {
        return SystemProperties.get("ro.build.romversion");
    }

    public static long getInstalledDate() {
        return SystemProperties.getLong("ro.build.date.utc", 0) * 1000;
    }

    public static boolean isNewerThanInstalled(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
        long installedDate = getInstalledDate();
        long date = -1;
        try {
            date = dateFormat.parse(dateString).getTime();
        } catch (ParseException e) {
                e.printStackTrace();
        }
        return date > installedDate;
    }

    public static String getBackupDirectory() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm",Locale.US);
        return dateFormat.format(System.currentTimeMillis()) + "-" + getInstalledVersion();
    }

}
