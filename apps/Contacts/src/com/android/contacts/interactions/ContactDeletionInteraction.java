/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.interactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mediatek.contacts.simservice.SIMProcessorService;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.simservice.SIMDeleteProcessor;

import java.util.HashSet;
// The following lines are provided and maintained by Mediatek Inc.
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
// The previous lines are provided and maintained by Mediatek Inc.

/**
 * An interaction invoked to delete a contact.
 */
public class ContactDeletionInteraction extends Fragment
        implements LoaderCallbacks<Cursor>, OnDismissListener, SIMDeleteProcessor.Listener {

    private static final String FRAGMENT_TAG = "deleteContact";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACT_URI = "contactUri";
    /** M: key to map sim_uri and sim_index to delete @{ */
    private static final String KEY_CONTACT_SIM_URI = "contactSimUri";
    private static final String KEY_CONTACT_SIM_WHERE = "contactSimWhere";
    /**@}*/
    private static final String KEY_FINISH_WHEN_DONE = "finishWhenDone";
    public static final String ARG_CONTACT_URI = "contactUri";

    private static final String[] ENTITY_PROJECTION = new String[] {
        Entity.RAW_CONTACT_ID, //0
        Entity.ACCOUNT_TYPE, //1
        Entity.DATA_SET, // 2
        Entity.CONTACT_ID, // 3
        Entity.LOOKUP_KEY, // 4
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 4;

    private boolean mActive;
    private Uri mContactUri;
    private boolean mFinishActivityWhenDone;
    private Context mContext;
    private AlertDialog mDialog;

    /** This is a wrapper around the fragment's loader manager to be used only during testing. */
    private TestLoaderManager mTestLoaderManager;

    @VisibleForTesting
    int mMessageId;

    /**
     * Starts the interaction.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @return the newly created interaction
     */
    public static ContactDeletionInteraction start(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone) {
        /** M: New Feature  @{ */
        /*
         * Original Code :
         * return startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);
         */
        Log.i(FRAGMENT_TAG,"[start] set mSimUri and mSimWhere are null");
        ContactDeletionInteraction deletion = startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);
        deletion.mSimUri = null;
        deletion.mSimWhere = null;
        /** @} */
        return deletion;
    }

    /**
     * Starts the interaction and optionally set up a {@link TestLoaderManager}.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @param testLoaderManager the {@link TestLoaderManager} to use to load the data, may be null
     *        in which case the default {@link LoaderManager} is used
     * @return the newly created interaction
     */
    @VisibleForTesting
    static ContactDeletionInteraction startWithTestLoaderManager(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone,
            TestLoaderManager testLoaderManager) {
        if (contactUri == null) {
            return null;
        }

        FragmentManager fragmentManager = activity.getFragmentManager();
        ContactDeletionInteraction fragment =
                (ContactDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactDeletionInteraction();
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
        }
        return fragment;
    }

    @Override
    public LoaderManager getLoaderManager() {
        // Return the TestLoaderManager if one is set up.
        LoaderManager loaderManager = super.getLoaderManager();
        if (mTestLoaderManager != null) {
            // Set the delegate: this operation is idempotent, so let's just do it every time.
            mTestLoaderManager.setDelegate(loaderManager);
            return mTestLoaderManager;
        } else {
            return loaderManager;
        }
    }

    /** Sets the TestLoaderManager that is used to wrap the actual LoaderManager in tests. */
    private void setTestLoaderManager(TestLoaderManager mockLoaderManager) {
        mTestLoaderManager = mockLoaderManager;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        /**M: initial progress handler to show delay progressDialog*/
        mProgressHandler = new ProgressHandler();

        /** M: Add for SIM Service refactory @{ */
        SIMDeleteProcessor.registerListener(this);
        /** @} */
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
        /**
         * M: Bug Fix for CR: ALPS00449485 <br>
         *    Description: To fix memory leakage issue 
         *    which this object could not be referenced. @{
         */
        if (mSIMStateChangedListener != null) {
            mContext.unregisterReceiver(mSIMStateChangedListener);
            mSIMStateChangedListener = null;
        }
        mContext = null;
        /** @} */

        /** M: Add for SIM Service refactory @{ */
        SIMDeleteProcessor.unregisterListener(this);
        /** @} */
    }

    public void setContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().restartLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
    }

    private void setFinishActivityWhenDone(boolean finishActivityWhenDone) {
        this.mFinishActivityWhenDone = finishActivityWhenDone;

    }

    /* Visible for testing */
    boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mActive) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().initLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
        // The following lines are provided and maintained by Mediatek Inc.
        if (mSIMStateChangedListener == null) {
            mSIMStateChangedListener = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String iccState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    Log.i(FRAGMENT_TAG, "onReceive intent.action = " + intent.getAction() + " and ICC_STATE = " + iccState);
                    if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)
                            && IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(iccState)) {
                        Log.i(FRAGMENT_TAG, "received SIM State not ready event...");
                        if (mDialog != null && mDialog.isShowing()) {
                            mDialog.dismiss();
                            getActivity().finish();
                        }
                    }
                }
            };
            mContext.registerReceiver(mSIMStateChangedListener, new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        }
        // The previous lines are provided and maintained by Mediatek Inc.
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
        // The following lines are provided and maintained by Mediatek Inc.
        if (mSIMStateChangedListener != null) {
            mContext.unregisterReceiver(mSIMStateChangedListener);
            mSIMStateChangedListener = null;
        }
        // The previous lines are provided and maintained by Mediatek Inc.
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
        return new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY), ENTITY_PROJECTION,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
      ///M:check whether the fragment still in Activity@{
        if(!isAdded()){
            Log.w(FRAGMENT_TAG, "onLoadFinished(),This Fragment is not add to the Activity now.data:" + cursor);
            if(cursor != null){
                cursor.close();
            }
            return;
        }
        ///@}

        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mActive) {
            LogUtils.w(FRAGMENT_TAG, "#onLoadFinished(),the mActive is false,Cancle execute!");
            return;
        }

        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long>  readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long>  writableRawContacts = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
            AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }

        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        if (readOnlyCount > 0 && writableCount > 0) {
            mMessageId = R.string.readOnlyContactDeleteConfirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            mMessageId = R.string.readOnlyContactWarning;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            mMessageId = R.string.multipleContactDeleteConfirmation;
        } else {
            mMessageId = R.string.deleteConfirmation;
        }

        final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
        showDialog(mMessageId, contactUri);

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_delete_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, final Uri contactUri) {
        mDialog = new AlertDialog.Builder(getActivity())
        /** M: Change Feature change dialog style @{ */
        .setTitle(R.string.deleteConfirmation_title)
        /** @} */
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setMessage(messageId).setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        doDeleteContact(contactUri);
                    }
                }).create();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActive = false;
        mDialog = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mActive);
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
        /** M: to save sim_uri and sim_index to delete @{ */
        outState.putParcelable(KEY_CONTACT_SIM_URI, mSimUri);
        outState.putString(KEY_CONTACT_SIM_WHERE, mSimWhere);
        /**@}*/
        outState.putBoolean(KEY_FINISH_WHEN_DONE, mFinishActivityWhenDone);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
            /** M: to get sim_uri and sim_index to delete @{ */
            mSimUri = savedInstanceState.getParcelable(KEY_CONTACT_SIM_URI);
            mSimWhere = savedInstanceState.getString(KEY_CONTACT_SIM_WHERE);
            /**@}*/
            mFinishActivityWhenDone = savedInstanceState.getBoolean(KEY_FINISH_WHEN_DONE);
        }
    }

    protected void doDeleteContact(final Uri contactUri) {
        // The following lines are provided and maintained by Mediatek Inc.
        if (!isAdded()) {
            LogUtils.w(FRAGMENT_TAG, "This Fragment is not add to the Activity.");
            return;
        }
        if (mSimUri != null) {
            Log.d(FRAGMENT_TAG, "[doDeleteContact] delete sim contact failed: mSimUri = " + mSimUri + "; mSimWhere = "
                    + mSimWhere);
            if (!isAdded()) {
                LogUtils.w(FRAGMENT_TAG, "This Fragment is not add to the Activity.");
                return;
            }

            /** M: Add for SIM Service refactory @{ */
            Intent intent = new Intent(mContext, SIMProcessorService.class);
            intent.setData(mSimUri);
            intent.putExtra(SIMDeleteProcessor.SIM_WHERE, mSimWhere);
            intent.putExtra(SIMServiceUtils.SERVICE_SLOT_KEY, mSlotId);
            intent.putExtra(SIMServiceUtils.SERVICE_WORK_TYPE, SIMServiceUtils.SERVICE_WORK_DELETE);
            intent.putExtra(SIMDeleteProcessor.LOCAL_CONTACT_URI, contactUri);
            mContext.startService(intent);
            /** @} */

            /**M: move database query to background thread
            origin code:
            if (getActivity().getContentResolver().delete(mSimUri, mSimWhere, null) <= 0) {
                getActivity().finish();
                return;
            }
              mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, contactUri));
              if (isAdded() && mFinishActivityWhenDone) {
                  getActivity().finish();
              }
            @{*/
        } else {
            mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, contactUri));
            if (isAdded() && mFinishActivityWhenDone) {
                getActivity().finish();
            }
        }
        /** @}*/
    }

    // The following lines are provided and maintained by Mediatek Inc. 
    private Uri mSimUri = null;
    private String mSimWhere = null;
    /// M: change for SIM Service refactoring
    private static int mSlotId = -1;
    private BroadcastReceiver mSIMStateChangedListener = null;

    public static ContactDeletionInteraction start(Activity activity, Uri contactUri,
                                boolean finishActivityWhenDone, Uri simUri, String simWhere, int slotId) {
        ContactDeletionInteraction deletion = startWithTestLoaderManager(activity, contactUri,
                finishActivityWhenDone, null);
        deletion.mSimUri = simUri;
        deletion.mSimWhere = simWhere;
        mSlotId = slotId;
        return deletion;
    }

    /** M: show loading when load data in back ground @{ */
    static final int SHOW_PROGRESS_DIALOG = 0;
    static final int CANCEL_PROGRESS_DIALOG = 1;
    static final long DELAY_MILLIS = 1000;

    private ProgressHandler mProgressHandler;
    private class ProgressHandler extends Handler{
        final ProgressDialog mDialog = new ProgressDialog(mContext);

        /**
         * M: clear the progress message, and send a message to cancel the dialog
         */
        public void cancelProgressDialogIfNeeded() {
            removeMessages(SHOW_PROGRESS_DIALOG);
            sendMessage(obtainMessage(CANCEL_PROGRESS_DIALOG));
        }

        @Override
        public void handleMessage(Message msg) {
            if (!isAdded()) {
                LogUtils.w(FRAGMENT_TAG, "fragment not added, ignore the message: " + msg.what);
                return;
            }
            switch (msg.what) {
            case SHOW_PROGRESS_DIALOG:
                mDialog.setMessage(getString(R.string.please_wait));
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
                break;
            case CANCEL_PROGRESS_DIALOG:
                if (mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                break;
            default:
                break;
            }
        };
    };
    /** @}*/

    /** M: Add for SIM Service refactory @{ */
    @Override
    public void onSIMDeleteFailed() {
        if (isAdded()) {
            getActivity().finish();
        }
        return;
    }

    @Override
    public void onSIMDeleteCompleted() {
        if (isAdded() && mFinishActivityWhenDone) {
            getActivity().finish();
        }
        return;
    }
    /** @} */
    // The previous lines are provided and maintained by Mediatek Inc.
}
