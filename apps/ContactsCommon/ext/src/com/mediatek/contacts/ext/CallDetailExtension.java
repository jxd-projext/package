package com.mediatek.contacts.ext;

import com.mediatek.dialer.PhoneCallDetailsEx;

import android.app.Activity;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class CallDetailExtension {
    private static final String TAG = "CallDetailExtension";

    public String getCommand() {
        return "";
    }

    public void setTextView(int callType, TextView durationView, String formatDuration, String commd) {
        if (callType == Calls.MISSED_TYPE || callType == Calls.VOICEMAIL_TYPE) {
            Log.i(TAG, "[setTextView] is gone");
            durationView.setVisibility(View.GONE);
        } else {
            Log.i(TAG, "[setTextView] is visible");
            durationView.setVisibility(View.VISIBLE);
            durationView.setText(formatDuration);
        }
    }

    public boolean isNeedAutoRejectedMenu(boolean isAutoRejectedFilterMode, String commd) {
        return false;
    }

    public String setChar(boolean notSPChar, String str, String spChar, int charType,
            boolean secondSelection, String commd) {
        return null;
    }

    /**
     * if plugin has special view can call this function to set visible
     */
    public void setViewVisible(View view, String commd1, String commd2, int rse1, int res2,
            int res3, int res4, int res5, int res6, int res7) {
        // do nothing
    }

    /**
     * if plugin has special view can call this function to set visible
     */
    public void setViewVisibleByActivity(Activity activity, String commd1, String commd2, int rse1,
            int res2, int res3, int res4, int res5, int res6, int res7, String commd) {
        // do nothing
    }

    public void onCreate(Activity activity, IPhoneNumberHelper phoneNumberHelper) {
    }

    public void onDestroy() {
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        return false;
    }

    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    /**
     * @param callUris
     * @param phoneCallDetails
     * @return
     */
    public PhoneCallDetailsEx[] doInBackgroundForUpdateData(Uri[] callUris, PhoneCallDetailsEx[] phoneCallDetails) {
        return phoneCallDetails;
    }

    public boolean setSimInfo(int simId, TextView simIndicator) {
        return false;
    }

}
