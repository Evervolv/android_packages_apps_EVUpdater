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

import android.os.Environment;
import android.os.UserHandle;

import com.evervolv.updater.FlashActivity.Zip;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class RecoveryScriptBuilder {

    public static final int TWRP = 0;
    public static final int CWM  = 1;

    public static final int BACKUP        = 1;
    public static final int WIPE_DATA     = 2;
    public static final int WIPE_CACHE    = 4;
    public static final int WIPE_DALVIK   = 8;

    private int mType;
    private List<Zip> mZipList;
    private boolean mBackup = false;
    private boolean mWipeData = false;
    private boolean mWipeCache = false;
    private boolean mWipeDalvik = false;

    public RecoveryScriptBuilder(int type, List<Zip> zips, int flags) {
        mType = type;
        mZipList = zips;
        parseFlags(flags);
    }

    public boolean create() {
        boolean success = false;
        switch (mType) {
            case TWRP:
                success = buildOpenRecoveryScript();
                break;
            case CWM:
                success = buildExtendedCommandScript();
                break;
        }
        return success;
    }

    private void parseFlags(int flags) {
        if ((flags & BACKUP) == BACKUP) {
            mBackup = true;
        }
        if ((flags & WIPE_DATA) == WIPE_DATA) {
            mWipeData = true;
        }
        if ((flags & WIPE_CACHE) == WIPE_CACHE) {
            mWipeCache = true;
        }
        if ((flags & WIPE_DALVIK) == WIPE_DALVIK) {
            mWipeDalvik = true;
        }
    }

    private boolean buildOpenRecoveryScript() {
        try {
            String recoveryScript = "/cache/recovery/openrecoveryscript";
            Process p = Runtime.getRuntime().exec("sh");
            OutputStream o = p.getOutputStream();
            o.write("mkdir -p /cache/recovery/\n".getBytes());
            o.write(String.format("echo -n > %s\n",
                    recoveryScript).getBytes());
            if (mBackup) {
                o.write(String.format("echo 'backup SDBO %s' >> %s\n",
                        Utils.getBackupDirectory(),
                        recoveryScript).getBytes());
            }
            if (mWipeData) {
                o.write(String.format("echo 'wipe data' >> %s\n",
                        recoveryScript).getBytes());
            }
            if (mWipeCache) {
                o.write(String.format("echo 'wipe cache' >> %s\n",
                        recoveryScript).getBytes());
            }
            if (mWipeDalvik) {
                o.write(String.format("echo 'wipe dalvik' >> %s\n",
                        recoveryScript).getBytes());
            }

            if (!mZipList.isEmpty()) {
                for (Zip i: mZipList) {
                    /* Using local path should prevent fuckups from different recovery mount points */
                    o.write(String.format("echo 'install %s' >> %s\n",
                            i.getPath(),
                            recoveryScript).getBytes());
                }
            } else {
                throw new IOException("No Zips");
            }
            o.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean buildExtendedCommandScript() {
        try {
            String recoveryScript = "/cache/recovery/extendedcommand";
            String sdcardPath = "/sdcard/";
            if (Environment.isExternalStorageEmulated()) {
                sdcardPath += UserHandle.myUserId() + "/";
            }
            Process p = Runtime.getRuntime().exec("sh");
            OutputStream o = p.getOutputStream();
            o.write("mkdir -p /cache/recovery/\n".getBytes());
            o.write(String.format("echo -n > %s\n",
                    recoveryScript).getBytes());
            if (mBackup) {
                o.write(String.format("echo 'backup_rom(\"%sclockworkmod/backup/%s\"' >> %s\n",
                        sdcardPath,
                        Utils.getBackupDirectory(),
                        recoveryScript).getBytes());
            }
            if (mWipeData) {
                o.write(String.format("echo 'format(\"/data\")' >> %s\n",
                        recoveryScript).getBytes());
            }
            if (mWipeCache) {
                o.write(String.format("echo 'format(\"/cache\")' >> %s\n",
                        recoveryScript).getBytes());
            }
            if (mWipeDalvik) {
                //TODO: Wipe dalvik cache, do we need to mount cache first?
                //o.write(String.format("echo 'format(\"/data\")' >> %s\n",
                //        recoveryScript).getBytes());
            }
            if (!mZipList.isEmpty()) {
                for (Zip i: mZipList) {
                    o.write(String.format("echo 'install_zip(\"%s\")' >> %s\n",
                            sdcardPath + i.getPath(),
                            recoveryScript).getBytes());
                }
            } else {
                throw new IOException("No Zips");
            }
            o.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
