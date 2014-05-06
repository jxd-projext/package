package com.android.providers.media;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class used to set and check out which process can access DRM files.
 * 
 */
public class DrmHelper {
    private static final String TAG = "MediaProvider/DrmHelper";
    private static HashMap<Integer, Boolean> sCurrentProcesses;
    private static Set<String> sPermitedProcessNames;

    /**
     * Checks out whether the process can access DRM files or not.
     * 
     * @param pid process id.
     * @return true if the process can access DRM files.
     */
    public static synchronized boolean isPermitedAccessDrm(Context context, int pid) {
        if (!MediaFeatureOption.IS_SUPPORT_DRM) {
            return false;
        }
        Boolean result = null;// not set
        if (sCurrentProcesses == null) {
            sCurrentProcesses = new HashMap<Integer, Boolean>();
        } else {
            result = sCurrentProcesses.get(pid);
        }
        // no this process
        if (result == null) {
            if (sPermitedProcessNames == null) {
                // if not set permited process names, here set the default names.
                setDefaultProcessNames();
            }
            sCurrentProcesses.clear();// clear old map
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
            int size = list.size();
            for (int i = 0; i < size; i++) {
                RunningAppProcessInfo runInfo = list.get(i);
                boolean allow = sPermitedProcessNames.contains(runInfo.processName);
                sCurrentProcesses.put(runInfo.pid, allow);
            }
            result = sCurrentProcesses.get(pid);
            if (result == null) {
                return true;
            }
        }
        return result;
    }

    /**
     * Sets processes that can access DRM files.
     * 
     * @param permitedProcessNames the name of processes.
     */
    public static synchronized void setPermitedProcessNames(String[] permitedProcessNames) {
        if (sPermitedProcessNames == null) {
            sPermitedProcessNames = new HashSet<String>();
        } else {
            sPermitedProcessNames.clear();
        }
        if (permitedProcessNames == null) {
            MtkLog.w(TAG, "setPermitedProcessNames: none permited to access drm process!");
        } else {
            int length = permitedProcessNames.length;
            for (int i = 0; i < length; i++) {
                sPermitedProcessNames.add(permitedProcessNames[i]);
            }
        }
    }

    private static void setDefaultProcessNames() {
        String[] permitedProcessNames = new String[] {
                "com.android.music",
                "com.android.gallery",
                "com.android.gallery:CropImage",
                "com.cooliris.media",
                "android.process.media",
                "com.mediatek.videoplayer",
                "com.mediatek.videoplayer2",
                "com.android.settings",
                "com.android.gallery3d",
                "com.android.gallery3d:crop",
                "com.android.deskclock",
                "com.android.mms",
                "system"
              };
        setPermitedProcessNames(permitedProcessNames);
    }
}
