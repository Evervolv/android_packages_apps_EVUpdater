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

    public static final int BACKUP = 0x00000001;
    public static final int WIPE   = 0x00000010;

    private int mType;
    private List<Zip> mZipList;
    private boolean mBackup = false;
    private boolean mWipe = false;

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
        if ((flags & WIPE) == WIPE) {
            mWipe = true;
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
            if (mWipe) {
                o.write(String.format("echo 'wipe cache' >> %s\n",
                        recoveryScript).getBytes());
                o.write(String.format("echo 'wipe data' >> %s\n",
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
                o.write(String.format("echo 'backup_rom(\"/sdcard/clockworkmod/backup/%s\"' >> %s\n",
                        Utils.getBackupDirectory(),
                        recoveryScript).getBytes());
            }
            if (mWipe) {
                o.write(String.format("echo 'format(\"/cache\")' >> %s\n",
                        recoveryScript).getBytes());
                o.write(String.format("echo 'format(\"/data\")' >> %s\n",
                        recoveryScript).getBytes());
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
