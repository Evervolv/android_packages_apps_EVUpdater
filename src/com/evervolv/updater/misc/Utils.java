package com.evervolv.updater.misc;

import android.os.SystemProperties;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Utils {

    private Utils() { }

    public static String getDevice() {
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
