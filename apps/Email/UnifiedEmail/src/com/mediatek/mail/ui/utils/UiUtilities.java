package com.mediatek.mail.ui.utils;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.emailcommon.Logging;
import com.android.emailcommon.mail.Address;
import com.android.emailcommon.utility.Utility;
import com.android.mail.R;
import com.android.mail.ui.ConnectionAlertDialog;
import com.android.mail.utils.LogUtils;

public class UiUtilities {
    /// M: Tag for connection alert dialog @{
    public static final String TAG_CONNECTION_ALERT_DIALOG = "connection-alert-dialog";
    /// @}

    /// M: Safely start a activity for result, toast if catch ActivityNotFoundException.
    public static void startRemoteActivityForResult(Activity fromActivity,
            Intent intent, int requestCode, boolean showToast) {
        try {
            fromActivity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            if (showToast) {
                Utility.showToast(fromActivity,
                        R.string.no_application_response);
            }
            Logging.w("startRemoteActivityForResult ActivityNotFoundException "
                    + e.toString());
        }
    }

    /// M: Safely start a activity, toast if catch ActivityNotFoundException.
    public static void startRemoteActivity(Context fromContext,
            Intent intent, boolean showToast) {
        try {
            fromContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            if (showToast) {
                Utility.showToast(fromContext,
                        R.string.no_application_response);
            }
            Logging.w("startRemoteActivity ActivityNotFoundException "
                    + e.toString());
        }
    }

    /// M:Safely start Contacts, toast if catch ActivityNotFoundException.
    public static void showContacts(Context context, ImageView photoView,
            Uri quickContactLookupUri, Address address) {
        if (quickContactLookupUri != null) {
            try {
                QuickContact.showQuickContact(context, photoView, quickContactLookupUri,
                        QuickContact.MODE_MEDIUM, null);
            } catch (ActivityNotFoundException e) {
                Utility.showToast(context, R.string.no_application_response);
                Logging.w("ShowQuickContact ActivityNotFoundException "
                        + e.toString());
            }
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address.getAddress(), null);
            final Intent intent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT,
                    mailUri);

            // Only provide personal name hint if we have one
            final String senderPersonal = address.getPersonal();
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(ContactsContract.Intents.Insert.NAME, senderPersonal);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

            startRemoteActivity(context, intent, true);
        }
    }

    /// M: Use to constraint the max word number that user can input.
    public static void setupLengthFilter(EditText inputText, final Context context,
            final int maxLength , final boolean showToast) {
        // Create a new filter
        InputFilter.LengthFilter filter = new InputFilter.LengthFilter(
                maxLength) {
            public CharSequence filter(CharSequence source, int start, int end,
                    Spanned dest, int dstart, int dend) {
                if (source != null
                        && source.length() > 0
                        && (((dest == null ? 0 : dest.length()) + dstart - dend) == maxLength)) {
                    if (showToast) {
                        Toast.makeText(context,
                                context.getString(R.string.not_add_more_text),
                                Toast.LENGTH_SHORT).show();
                    }
                    return "";
                }
                return super.filter(source, start, end, dest, dstart, dend);
            }
        };

        // Find exist lenght filter.
        InputFilter[] filters = inputText.getFilters();
        int length = 0;
        for (int i = 0; i < filters.length; i++) {
            if (!(filters[i] instanceof InputFilter.LengthFilter)) {
                length++;
            }
        }

        //Only one length filter.
        InputFilter[] contentFilters = new InputFilter[length + 1];
        for (int i = 0; i < filters.length; i++) {
            if (!(filters[i] instanceof InputFilter.LengthFilter)) {
                contentFilters[i] = filters[i];
            }
        }
        contentFilters[length] = filter;
        inputText.setFilters(contentFilters);
    }

    /**
     * M: MTK if is Wifi Only
     * @param context Context
     * @return boolean
     */
    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return (!cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }


    /**
     * M: Display a connection alert dialog to prompt that no network available
     */
    public static void showConnectionAlertDialog(FragmentManager fragMagr) {
        if (fragMagr == null) {
            LogUtils.e(LogUtils.TAG, "Fragment manager is null");
            return;
        }
        final FragmentTransaction ft = fragMagr.beginTransaction();
        final Fragment prev = fragMagr.findFragmentByTag(TAG_CONNECTION_ALERT_DIALOG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

         // Create and show the dialog.
        final DialogFragment newFragment = ConnectionAlertDialog.newInstance();
        ft.add(newFragment, TAG_CONNECTION_ALERT_DIALOG).commitAllowingStateLoss();
    }
}
