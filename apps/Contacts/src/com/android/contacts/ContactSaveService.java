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

package com.android.contacts;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.PinnedPositions;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.group.GroupEditorFragment;
import com.android.contacts.common.database.ContactUpdateUtils;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.util.CallerInfoCacheUtils;
import com.android.contacts.util.ContactPhotoUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/// The following lines are provided and maintained by Mediatek Inc.
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.util.ContactsGroupUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.ContactsGroupUtils.USIMGroupException;
/// The previous lines are provided and maintained by Mediatek Inc.

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A service responsible for saving changes to the content provider.
 */
public class ContactSaveService extends IntentService {
    private static final String TAG = "ContactSaveService";

    /** Set to true in order to view logs on content provider operations */
    private static final boolean DEBUG = false;

    public static final String ACTION_NEW_RAW_CONTACT = "newRawContact";

    public static final String EXTRA_ACCOUNT_NAME = "accountName";
    public static final String EXTRA_ACCOUNT_TYPE = "accountType";
    public static final String EXTRA_DATA_SET = "dataSet";
    public static final String EXTRA_CONTENT_VALUES = "contentValues";
    public static final String EXTRA_CALLBACK_INTENT = "callbackIntent";

    public static final String ACTION_SAVE_CONTACT = "saveContact";
    public static final String EXTRA_CONTACT_STATE = "state";
    public static final String EXTRA_SAVE_MODE = "saveMode";
    public static final String EXTRA_SAVE_IS_PROFILE = "saveIsProfile";
    public static final String EXTRA_SAVE_SUCCEEDED = "saveSucceeded";
    public static final String EXTRA_UPDATED_PHOTOS = "updatedPhotos";

    public static final String ACTION_CREATE_GROUP = "createGroup";
    public static final String ACTION_RENAME_GROUP = "renameGroup";
    public static final String ACTION_DELETE_GROUP = "deleteGroup";
    public static final String ACTION_UPDATE_GROUP = "updateGroup";
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_GROUP_LABEL = "groupLabel";
    public static final String EXTRA_RAW_CONTACTS_TO_ADD = "rawContactsToAdd";
    public static final String EXTRA_RAW_CONTACTS_TO_REMOVE = "rawContactsToRemove";
    public static final String EXTRA_RAW_CONTACTS_ID = "rawContactsId";

    public static final String ACTION_SET_STARRED = "setStarred";
    public static final String ACTION_DELETE_CONTACT = "delete";
    public static final String EXTRA_CONTACT_URI = "contactUri";
    public static final String EXTRA_STARRED_FLAG = "starred";

    public static final String ACTION_SET_SUPER_PRIMARY = "setSuperPrimary";
    public static final String ACTION_CLEAR_PRIMARY = "clearPrimary";
    public static final String EXTRA_DATA_ID = "dataId";

    public static final String ACTION_JOIN_CONTACTS = "joinContacts";
    public static final String EXTRA_CONTACT_ID1 = "contactId1";
    public static final String EXTRA_CONTACT_ID2 = "contactId2";
    public static final String EXTRA_CONTACT_WRITABLE = "contactWritable";

    public static final String ACTION_SET_SEND_TO_VOICEMAIL = "sendToVoicemail";
    public static final String EXTRA_SEND_TO_VOICEMAIL_FLAG = "sendToVoicemailFlag";

    /*
     * New Feature by Mediatek Begin.            
     * using by block video call     
     */
    public static final String ACTION_SET_BLOCK_VIDEO_CALL = "blockVideoCall";
    public static final String EXTRA_BLOCK_VIDEO_CALL_FLAG = "blockVideoCallFlag";
    /*
     * New Feature  by Mediatek End.
    */
    /*
     * add for contact detail group new feature
     */
    public static final String CREATE_GROUP_COMP = "create_group_successful";
    
    public static final String ACTION_SET_RINGTONE = "setRingtone";
    public static final String EXTRA_CUSTOM_RINGTONE = "customRingtone";

    private static final HashSet<String> ALLOWED_DATA_COLUMNS = Sets.newHashSet(
        Data.MIMETYPE,
        Data.IS_PRIMARY,
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.DATA5,
        Data.DATA6,
        Data.DATA7,
        Data.DATA8,
        Data.DATA9,
        Data.DATA10,
        Data.DATA11,
        Data.DATA12,
        Data.DATA13,
        Data.DATA14,
        Data.DATA15
    );

    private static final int PERSIST_TRIES = 3;
    private static final int GROUP_SIM_ABSENT = 4;

    public interface Listener {
        public void onServiceCompleted(Intent callbackIntent);
    }

    private static final CopyOnWriteArrayList<Listener> sListeners =
            new CopyOnWriteArrayList<Listener>();

    private Handler mMainHandler;

    public ContactSaveService() {
        super(TAG);
        setIntentRedelivery(true);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static void registerListener(Listener listener) {
        if (!(listener instanceof Activity)) {
            throw new ClassCastException("Only activities can be registered to"
                    + " receive callback from " + ContactSaveService.class.getName());
        }
        LogUtils.d(TAG, "listener added to SaveService: " + listener);
        if (listener instanceof ContactEditorActivity) {
            for (Listener currentlistener : sListeners) {
                if (currentlistener instanceof ContactEditorActivity) {
                    LogUtils.w(TAG, "only one ContactEditorActivity instance allowed, finish old one: " + currentlistener);
                    ((ContactEditorActivity) currentlistener).finish();
                }
            }
        }
        sListeners.add(0, listener);
    }

    public static void unregisterListener(Listener listener) {
        LogUtils.d(TAG, "listener removed from SaveService: " + listener);
        sListeners.remove(listener);
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }

    /** M: change for low_memory kill Contacts process CR:ALPS00571956 @{
     * use startForeground to set service adj.
     * */
    @Override
    public void onCreate() {
        super.onCreate();
//        startForeground(1, new Notification());
    }

    @Override
    public void onDestroy() {
//        stopForeground(true);
        super.onDestroy();
    }
    /** @} */

    @Override
    protected void onHandleIntent(Intent intent) {
        // Call an appropriate method. If we're sure it affects how incoming phone calls are
        // handled, then notify the fact to in-call screen.
        String action = intent.getAction();
        if (ACTION_NEW_RAW_CONTACT.equals(action)) {
            createRawContact(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SAVE_CONTACT.equals(action)) {
            try {
                saveContact(intent);

            /**
             * M: fixed CR ALPS00783221 @{
             */
            } catch (IllegalStateException e) {
                Log.w(TAG, "IllegalStateException:" + e.toString());
                Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
                if (callbackIntent != null) {
                    callbackIntent.putExtra(EXTRA_SAVE_SUCCEEDED, false);
                    callbackIntent.setData(null);
                    deliverCallback(callbackIntent);
                } else {
                    Log.w(TAG, "IllegalStateException: callbackIntent == NULL!");
                }
            }
            /** @} */

            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_CREATE_GROUP.equals(action)) {
            /// fixed CR ALPS00542175
            mIsTransactionProcessing = true;
            createGroup(intent);
        } else if (ACTION_RENAME_GROUP.equals(action)) {
            renameGroup(intent);
        } else if (ACTION_DELETE_GROUP.equals(action)) {
            deleteGroup(intent);
        } else if (ACTION_UPDATE_GROUP.equals(action)) {
            /// fixed CR ALPS00542175
            mIsTransactionProcessing = true;
            updateGroup(intent);
        } else if (ACTION_SAVE_GROUP_TO_CONTACT.equals(action)) {
            // /M: fixed CR ALPS00542175
            mIsTransactionProcessing = true;
            /** M: add to save groupid for contact. */
            saveGroupIdsToContact(intent);
        } else if (ACTION_SET_STARRED.equals(action)) {
            setStarred(intent);
        } else if (ACTION_SET_SUPER_PRIMARY.equals(action)) {
            setSuperPrimary(intent);
        } else if (ACTION_CLEAR_PRIMARY.equals(action)) {
            clearPrimary(intent);
        } else if (ACTION_DELETE_CONTACT.equals(action)) {
            deleteContact(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_JOIN_CONTACTS.equals(action)) {
            joinContacts(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SET_SEND_TO_VOICEMAIL.equals(action)) {
            setSendToVoicemail(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SET_RINGTONE.equals(action)) {
            setRingtone(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        /*
         * New Feature by Mediatek Begin.            
         * using by block video call, defaults to false        
         */    
        } else if (ACTION_SET_BLOCK_VIDEO_CALL.equals(action)) {
            setBlockVideoCall(intent);
        }
        /*
         * New Feature  by Mediatek End.
        */
        
        /// fixed CR ALPS00542175
        mIsTransactionProcessing = false;;
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     */
    public static Intent createNewRawContactIntent(Context context,
            ArrayList<ContentValues> values, AccountWithDataSet account,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(
                context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_NEW_RAW_CONTACT);
        if (account != null) {
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
            serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);
        }
        serviceIntent.putParcelableArrayListExtra(
                ContactSaveService.EXTRA_CONTENT_VALUES, values);

        // Callback intent will be invoked by the service once the new contact is
        // created.  The service will put the URI of the new contact as "data" on
        // the callback intent.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        return serviceIntent;
    }

    private void createRawContact(Intent intent) {
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        String dataSet = intent.getStringExtra(EXTRA_DATA_SET);
        List<ContentValues> valueList = intent.getParcelableArrayListExtra(EXTRA_CONTENT_VALUES);
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        operations.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.DATA_SET, dataSet)
                .build());

        int size = valueList.size();
        for (int i = 0; i < size; i++) {
            ContentValues values = valueList.get(i);
            values.keySet().retainAll(ALLOWED_DATA_COLUMNS);
            operations.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValues(values)
                    .build());
        }

        ContentResolver resolver = getContentResolver();
        ContentProviderResult[] results;
        try {
            results = resolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store new contact", e);
        }

        Uri rawContactUri = results[0].uri;
        callbackIntent.setData(RawContacts.getContactLookupUri(resolver, rawContactUri));

        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     * This variant is more convenient to use when there is only one photo that can
     * possibly be updated, as in the Contact Details screen.
     * @param rawContactId identifies a writable raw-contact whose photo is to be updated.
     * @param updatedPhotoPath denotes a temporary file containing the contact's new photo.
     */
    public static Intent createSaveContactIntent(Context context, RawContactDeltaList state,
            String saveModeExtraKey, int saveMode, boolean isProfile,
            Class<? extends Activity> callbackActivity, String callbackAction, long rawContactId,
            Uri updatedPhotoPath) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(String.valueOf(rawContactId), updatedPhotoPath);
        return createSaveContactIntent(context, state, saveModeExtraKey, saveMode, isProfile,
                callbackActivity, callbackAction, bundle);
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     * This variant is used when multiple contacts' photos may be updated, as in the
     * Contact Editor.
     * @param updatedPhotos maps each raw-contact's ID to the file-path of the new photo.
     */
    public static Intent createSaveContactIntent(Context context, RawContactDeltaList state,
            String saveModeExtraKey, int saveMode, boolean isProfile,
            Class<? extends Activity> callbackActivity, String callbackAction,
            Bundle updatedPhotos) {
        Intent serviceIntent = new Intent(
                context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SAVE_CONTACT);
        serviceIntent.putExtra(EXTRA_CONTACT_STATE, (Parcelable) state);
        serviceIntent.putExtra(EXTRA_SAVE_IS_PROFILE, isProfile);
        if (updatedPhotos != null) {
            serviceIntent.putExtra(EXTRA_UPDATED_PHOTOS, (Parcelable) updatedPhotos);
        }

        if (callbackActivity != null) {
            // Callback intent will be invoked by the service once the contact is
            // saved.  The service will put the URI of the new contact as "data" on
            // the callback intent.
            Intent callbackIntent = new Intent(context, callbackActivity);
            callbackIntent.putExtra(saveModeExtraKey, saveMode);
            callbackIntent.setAction(callbackAction);
            serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        }
        return serviceIntent;
    }

    private void saveContact(Intent intent) {
        RawContactDeltaList state = intent.getParcelableExtra(EXTRA_CONTACT_STATE);
        boolean isProfile = intent.getBooleanExtra(EXTRA_SAVE_IS_PROFILE, false);
        Bundle updatedPhotos = intent.getParcelableExtra(EXTRA_UPDATED_PHOTOS);

        // Trim any empty fields, and RawContacts, before persisting
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        RawContactModifier.trimEmpty(state, accountTypes);

        Uri lookupUri = null;

        final ContentResolver resolver = getContentResolver();
        boolean succeeded = false;

        // Keep track of the id of a newly raw-contact (if any... there can be at most one).
        long insertedRawContactId = -1;

        // Attempt to persist changes
        int tries = 0;
        while (tries++ < PERSIST_TRIES) {
            try {
                // Build operations and try applying
                final ArrayList<ContentProviderOperation> diff = state.buildDiff();
                if (DEBUG) {
                    Log.v(TAG, "Content Provider Operations:");
                    for (ContentProviderOperation operation : diff) {
                        Log.v(TAG, operation.toString());
                    }
                }

                ContentProviderResult[] results = null;
                if (!diff.isEmpty()) {
                    results = resolver.applyBatch(ContactsContract.AUTHORITY, diff);
                }

                final long rawContactId = getRawContactId(state, diff, results);
                if (rawContactId == -1) {
                    throw new IllegalStateException("Could not determine RawContact ID after save");
                }
                // We don't have to check to see if the value is still -1.  If we reach here,
                // the previous loop iteration didn't succeed, so any ID that we obtained is bogus.
                insertedRawContactId = getInsertedRawContactId(diff, results);
                if (isProfile) {
                    // Since the profile supports local raw contacts, which may have been completely
                    // removed if all information was removed, we need to do a special query to
                    // get the lookup URI for the profile contact (if it still exists).
                    Cursor c = resolver.query(Profile.CONTENT_URI,
                            new String[] {Contacts._ID, Contacts.LOOKUP_KEY},
                            null, null, null);
                    try {
                        if (c.moveToFirst()) {
                            final long contactId = c.getLong(0);
                            final String lookupKey = c.getString(1);
                            lookupUri = Contacts.getLookupUri(contactId, lookupKey);
                        }
                    } finally {
                        c.close();
                    }
                } else {
                    final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                    rawContactId);
                    lookupUri = RawContacts.getContactLookupUri(resolver, rawContactUri);
                }
                Log.v(TAG, "Saved contact. New URI: " + lookupUri);

                // We can change this back to false later, if we fail to save the contact photo.
                succeeded = true;
                break;

            } catch (RemoteException e) {
                // Something went wrong, bail without success
                Log.e(TAG, "Problem persisting user edits", e);
                break;

            } catch (OperationApplicationException e) {
                // Version consistency failed, re-parent change and try again
                Log.w(TAG, "Version consistency failed, re-parenting: " + e.toString());
                final StringBuilder sb = new StringBuilder(RawContacts._ID + " IN(");
                boolean first = true;
                final int count = state.size();
                for (int i = 0; i < count; i++) {
                    Long rawContactId = state.getRawContactId(i);
                    if (rawContactId != null && rawContactId != -1) {
                        if (!first) {
                            sb.append(',');
                        }
                        sb.append(rawContactId);
                        first = false;
                    }
                }
                sb.append(")");

                if (first) {
                    throw new IllegalStateException("Version consistency failed for a new contact");
                }

                final RawContactDeltaList newState = RawContactDeltaList.fromQuery(
                        isProfile
                                ? RawContactsEntity.PROFILE_CONTENT_URI
                                : RawContactsEntity.CONTENT_URI,
                        resolver, sb.toString(), null, null);
                state = RawContactDeltaList.mergeAfter(newState, state);
                /** M: Bug Fix for ALPS00420719 @{ */
                //work round, check the deleted item. if it is 1 break.
                if (null != state && state.size() < 2) {
                    int deleted = state.get(0).getValues().getAsInteger(RawContacts.DELETED);
                    Log.i(TAG, "deleted : " + deleted);
                    if (deleted == 1) {
                        succeeded = false;
                        lookupUri = null;
                        break;
                    }
                }
                /** @} */
                // Update the new state to use profile URIs if appropriate.
                if (isProfile) {
                    for (RawContactDelta delta : state) {
                        delta.setProfileQueryUri();
                    }
                }
            }
        }

        // Now save any updated photos.  We do this at the end to ensure that
        // the ContactProvider already knows about newly-created contacts.
        if (updatedPhotos != null) {
            for (String key : updatedPhotos.keySet()) {
                Uri photoUri = updatedPhotos.getParcelable(key);
                long rawContactId = Long.parseLong(key);

                // If the raw-contact ID is negative, we are saving a new raw-contact;
                // replace the bogus ID with the new one that we actually saved the contact at.
                if (rawContactId < 0) {
                    rawContactId = insertedRawContactId;
                    if (rawContactId == -1) {
                        throw new IllegalStateException(
                                "Could not determine RawContact ID for image insertion");
                    }
                }

                if (!saveUpdatedPhoto(rawContactId, photoUri)) succeeded = false;
            }
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        if (callbackIntent != null) {
            if (succeeded) {
                // Mark the intent to indicate that the save was successful (even if the lookup URI
                // is now null).  For local contacts or the local profile, it's possible that the
                // save triggered removal of the contact, so no lookup URI would exist..
                callbackIntent.putExtra(EXTRA_SAVE_SUCCEEDED, true);
            }
            callbackIntent.setData(lookupUri);
            deliverCallback(callbackIntent);
        }
    }

    /**
     * Save updated photo for the specified raw-contact.
     * @return true for success, false for failure
     */
    private boolean saveUpdatedPhoto(long rawContactId, Uri photoUri) {
        final Uri outputUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);

        return ContactPhotoUtils.savePhotoFromUriToUri(this, photoUri, outputUri, true);
    }

    /**
     * Find the ID of an existing or newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getRawContactId(RawContactDeltaList state,
            final ArrayList<ContentProviderOperation> diff,
            final ContentProviderResult[] results) {
        long existingRawContactId = state.findRawContactId();
        if (existingRawContactId != -1) {
            return existingRawContactId;
        }

        return getInsertedRawContactId(diff, results);
    }

    /**
     * Find the ID of a newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getInsertedRawContactId(
            final ArrayList<ContentProviderOperation> diff,
            final ContentProviderResult[] results) {
        final int diffSize = diff.size();
        for (int i = 0; i < diffSize; i++) {
            ContentProviderOperation operation = diff.get(i);
            if (operation.getType() == ContentProviderOperation.TYPE_INSERT
                    && operation.getUri().getEncodedPath().contains(
                            RawContacts.CONTENT_URI.getEncodedPath())) {
                return ContentUris.parseId(results[i].uri);
            }
        }
        return -1;
    }

    /**
     * Creates an intent that can be sent to this service to create a new group as
     * well as add new members at the same time.
     *
     * @param context of the application
     * @param account in which the group should be created
     * @param label is the name of the group (cannot be null)
     * @param rawContactsToAdd is an array of raw contact IDs for contacts that
     *            should be added to the group
     * @param callbackActivity is the activity to send the callback intent to
     * @param callbackAction is the intent action for the callback intent
     */
    public static Intent createNewGroupIntent(Context context, AccountWithDataSet account,
            String label, long[] rawContactsToAdd, Class<? extends Activity> callbackActivity,
            String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_CREATE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, label);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_ADD, rawContactsToAdd);

        // Callback intent will be invoked by the service once the new group is
        // created.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    private void createGroup(Intent intent) {
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        String dataSet = intent.getStringExtra(EXTRA_DATA_SET);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);
        /// change for CR ALPS00784408
        long rawContactId = intent.getLongExtra(EXTRA_RAW_CONTACTS_ID, -1);
        final long[] rawContactsToAdd = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_ADD);

        // the following lines are provided and maintained by Mediatek Inc.
        int[] simIndexArray = intent.getIntArrayExtra(ContactSaveService.EXTRA_SIM_INDEX_ARRAY);
        int slotId = intent.getIntExtra(ContactSaveService.EXTRA_SLOT_ID, -1);
        Log.i(TAG, "[createGroup]groupName:" + label + " |accountName:" + accountName + 
                "|AccountType:" + accountType + " |slotId:" + slotId);
        
        if (!checkGroupNameExist(label, accountName, accountType, true)) {
            Log.d(TAG, "[createGroup] Group Name exist!");
            Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
            callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.RELOAD);
            deliverCallback(callbackIntent);
            return;
        }

        int ugrpId = -1;
        if (slotId >= 0) {
            try {
                ugrpId = ContactsGroupUtils.USIMGroup.syncUSIMGroupNewIfMissing(slotId, label);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (USIMGroupException e) {
                Log.d(TAG, "[SyncUSIMGroup] catched USIMGroupException." 
                        + " ErrorType: " + e.getErrorType());
                Log.d(TAG, "[SyncUSIMGroup] catched USIMGroupException." 
                        + " getErrorSlotId: " + e.getErrorSlotId());
                mSlotError.put(e.getErrorSlotId(), e.getErrorType());
                checkAllSlotErrors();
                Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
                if (e.getErrorType() == USIMGroupException.GROUP_NAME_OUT_OF_BOUND) {
                    callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.RELOAD);
                }
                Log.i(TAG, EXTRA_CALLBACK_INTENT);
                deliverCallback(callbackIntent);
                return;
            }
        }
        // the previous lines are provided and maintained by Mediatek Inc.

        ContentValues values = new ContentValues();
        values.put(Groups.ACCOUNT_TYPE, accountType);
        values.put(Groups.ACCOUNT_NAME, accountName);
        values.put(Groups.DATA_SET, dataSet);
        values.put(Groups.TITLE, label);

        final ContentResolver resolver = getContentResolver();

        // Create the new group
        final Uri groupUri = resolver.insert(Groups.CONTENT_URI, values);

        // If there's no URI, then the insertion failed. Abort early because group members can't be
        // added if the group doesn't exist
        if (groupUri == null) {
            Log.e(TAG, "Couldn't create group with label " + label);
            return;
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        if (groupUri != null) {
            boolean isSuccess = addMembersToGroup(resolver, rawContactsToAdd, ContentUris.parseId(groupUri),
                    simIndexArray, slotId, ugrpId);
            LogUtils.i(TAG, "createGroup(),isSuccess:" + isSuccess + ",groupUri:" + groupUri);
            /** M: ALPS921231 when usim group action done, check if sim removed @{ */
            if (!SimCardUtils.isSimInserted(slotId) && slotId >= 0) {
                Log.d(TAG, "[createGroup] Find sim removed");
                showMoveUSIMGroupErrorToast(GROUP_SIM_ABSENT, slotId);
                deliverCallback(callbackIntent);
                return;
            }
            /**@}*/
            // TODO: Move this into the contact editor where it belongs. This needs to be integrated
            // with the way other intent extras that are passed to the {@link ContactEditorActivity}.
            values.clear();
            values.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
            values.put(GroupMembership.GROUP_ROW_ID, ContentUris.parseId(groupUri));

            if (isSuccess) {
                callbackIntent.setData(groupUri);
            }else{
                callbackIntent.setData(null);
                LogUtils.w(TAG, "createGroup(),setData == nul");
            }
            // TODO: This can be taken out when the above TODO is addressed

            /// change for CR ALPS00784408
            callbackIntent.putExtra(EXTRA_RAW_CONTACTS_ID, rawContactId);
            callbackIntent.putExtra(ContactsContract.Intents.Insert.DATA, Lists.newArrayList(values));
            
            /// M: add for group new feature @{
            // Original code:
            callbackIntent.putExtra(CREATE_GROUP_COMP, true);
            /// @}
        }
        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to rename a group.
     */
    public static Intent createGroupRenameIntent(Context context, long groupId, String newLabel,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_RENAME_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, newLabel);

        // Callback intent will be invoked by the service once the group is renamed.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    private void renameGroup(Intent intent) {
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);

        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for renameGroup request");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, label);
        final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
        getContentResolver().update(groupUri, values, null, null);

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.setData(groupUri);
        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to delete a group.
     */
    public static Intent createGroupDeletionIntent(Context context, long groupId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_DELETE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        return serviceIntent;
    }

    private void deleteGroup(Intent intent) {
        /** M: Bug Fix for CR ALPS00463033 @{ */
        if (sDeleteEndListener != null) {
            sDeleteEndListener.onDeleteStart();
        }
        /** @} */
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);

        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for deleteGroup request");
            return;
        }

        // The following lines are provided and maintained by Mediatek Inc.
        String groupLabel = intent.getStringExtra(EXTRA_GROUP_LABEL);
        Log.i(TAG, "[deleteGroup]groupLabel:" + groupLabel);
        
        int slotId = intent.getIntExtra(ContactSaveService.EXTRA_SLOT_ID, -1);
        if (slotId >= 0 && !TextUtils.isEmpty(groupLabel)) {
            //check whether group exists
            int ugrpId = -1;
            try {
                ugrpId = ContactsGroupUtils.USIMGroup.hasExistGroup(slotId, groupLabel);
                Log.i(TAG, "[deleteGroup]ugrpId:" + ugrpId);
            } catch (RemoteException e) {
                e.printStackTrace();
                ugrpId = -1;
            }
            if (ugrpId > 0) {
                // check group members
                int simId = intent.getIntExtra(ContactSaveService.EXTRA_SIM_ID, -1);
                // fix ALPS01002380. should not use groupLabel for groupuri,because groupname "/"
                // will lead to SQLite exception.
                Uri groupUri = ContentUris.withAppendedId(Contacts.CONTENT_GROUP_URI, groupId);
                Cursor c = getContentResolver().query(groupUri, new String[] {
                        Contacts._ID, Contacts.INDEX_IN_SIM}, Contacts.INDICATE_PHONE_SIM + " = " + simId, null, null);
                Log.i(TAG, "[deleteGroup]simId:" + simId + "|member count:"
                        + (c == null ? "null" : c.getCount()));
                try {
                    while (c != null && c.moveToNext()) {
                        int indexInSim = c.getInt(1);
                        boolean ret = ContactsGroupUtils.USIMGroup.deleteUSIMGroupMember(slotId,
                                indexInSim, ugrpId);
                        Log.i(TAG, "[deleteGroup]slotId:" + slotId + "ugrpId:" + ugrpId
                                + "|simIndex:" + indexInSim + "|Result:" + ret + " | contactid : " + c.getLong(0));
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                // Delete USIM group
                int error = ContactsGroupUtils.USIMGroup.deleteUSIMGroup(slotId, groupLabel);
                Log.i(TAG, "[deleteGroup]error:" + error);
                if (error != 0) {
                    showToast(R.string.delete_group_failure);
                    return;
                }   
            }
        }
        // The previous lines are provided and maintained by Mediatek Inc.

        getContentResolver().delete(
                ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), null, null);

        /** M: Bug Fix for CR ALPS00463033 @{ */
        if (sDeleteEndListener != null) {
            sDeleteEndListener.onDeleteEnd();
        }
        /** @} */
    }

    /**
     * Creates an intent that can be sent to this service to rename a group as
     * well as add and remove members from the group.
     *
     * @param context of the application
     * @param groupId of the group that should be modified
     * @param newLabel is the updated name of the group (can be null if the name
     *            should not be updated)
     * @param rawContactsToAdd is an array of raw contact IDs for contacts that
     *            should be added to the group
     * @param rawContactsToRemove is an array of raw contact IDs for contacts
     *            that should be removed from the group
     * @param callbackActivity is the activity to send the callback intent to
     * @param callbackAction is the intent action for the callback intent
     */
    public static Intent createGroupUpdateIntent(Context context, long groupId, String newLabel,
            long[] rawContactsToAdd, long[] rawContactsToRemove,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_UPDATE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, newLabel);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_ADD, rawContactsToAdd);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_REMOVE,
                rawContactsToRemove);

        // Callback intent will be invoked by the service once the group is updated
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    /**
     * M: need operate USIM contacts data if it is USIM contact.
     * 
     * @param mGroupMemberShipName
     * @param mSelectedGroupsNameList
     * @param simIndex
     * @param simOrPhone
     * @param slotId
     */
    private boolean operateUSIMGroupContacts(ArrayList<String> mGroupMemberShipName,
            ArrayList<String> mSelectedGroupsNameList, int simIndex, int slotId) {
        boolean ret = false;
        boolean isSuccess = true;
        try {
            for (Iterator iterator = mGroupMemberShipName.iterator(); iterator.hasNext();) {
                String grpName = (String) iterator.next();
                int ugrpId = ContactsGroupUtils.USIMGroup.hasExistGroup(slotId, grpName);
                ret = ContactsGroupUtils.USIMGroup.deleteUSIMGroupMember(slotId, simIndex, ugrpId);
                Log.i("GroupMemberChange", "DELETE ret == " + ret + " | groupId == " + ugrpId);
                if (!ret) {
                    isSuccess = false;
                    continue;
                }
            }

            for (Iterator iterator = mSelectedGroupsNameList.iterator(); iterator.hasNext();) {
                String grpName = (String) iterator.next();
                int ugrpId = ContactsGroupUtils.USIMGroup.hasExistGroup(slotId, grpName);
                ret = ContactsGroupUtils.USIMGroup.addUSIMGroupMember(slotId, simIndex, ugrpId);
                Log.i("GroupMemberChange", "ADD ret == " + ret + " | groupId == " + ugrpId + " | SimIndex == "
                        + simIndex);
                if (!ret) {
                    isSuccess = false;
                    continue;
                }
            }
        } catch (RemoteException e) {
            isSuccess = false;
            e.printStackTrace();
        }

        return isSuccess;
    }

    /**
     * M: delete contact and its group info in database
     * @param rawContactIds
     * @param mRawContactId
     */
    private void deleteDataByContactInfo(long[] rawContactIds, long mRawContactId) {
        if (rawContactIds != null && rawContactIds.length > 1) {
            for (long rawContactId : rawContactIds) {
                getContentResolver().delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                        new String[] { String.valueOf(rawContactId), GroupMembership.CONTENT_ITEM_TYPE });
            }
        } else {
            StringBuilder selection = new StringBuilder("");
            selection.append(Data.RAW_CONTACT_ID);
            selection.append("=? AND ");
            selection.append(Data.MIMETYPE);
            selection.append("=?");
            getContentResolver().delete(Data.CONTENT_URI, selection.toString(),
                    new String[] { String.valueOf(mRawContactId), GroupMembership.CONTENT_ITEM_TYPE });

        }
    }

    /**
     * M: insert new group id list into contactsprovider
     * @param mSelectedGroupsIdList
     * @param rawContactIds
     * @param mRawContactId
     */
    private boolean insertDataByNewGroupId(List<Integer> mSelectedGroupsIdList, long[] rawContactIds, long mRawContactId) {
        boolean isSuccess = true;
        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder;
        for (Iterator iterator = mSelectedGroupsIdList.iterator(); iterator.hasNext();) {
            Integer groupId = (Integer) iterator.next();
            Log.i(TAG, "[insertDataByNewGroupId] groupId: " + groupId);
            if (rawContactIds != null && rawContactIds.length > 1) {
                for (long rawContactId : rawContactIds) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValue(Phone.RAW_CONTACT_ID, ((Long) rawContactId).intValue());
                    builder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                    builder.withValue(GroupMembership.GROUP_ROW_ID, (long) groupId);
                    operationList.add(builder.build());
                }
            } else {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValue(Phone.RAW_CONTACT_ID, ((Long) mRawContactId).intValue());
                builder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                builder.withValue(GroupMembership.GROUP_ROW_ID, (long) groupId);
                operationList.add(builder.build());
            }
        }

        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            isSuccess = false;
            operationList.clear();
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            isSuccess = false;
            operationList.clear();
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }

        return isSuccess;
    }

    /**
     * M: used to update those selected groups info for the pointed raw_contact.@{
     * */
    private void saveGroupIdsToContact(Intent intent) {
        boolean isSuccess = true;
        int simIndex = intent.getIntExtra(ContactSaveService.EXTRA_SIM_INDEX, -1);
        int slotId = intent.getIntExtra(ContactSaveService.EXTRA_SLOT_ID, -1);
        LogUtils.d(TAG, "[saveGroupIdsToContact] slotId: " + slotId + " simIndex: " + simIndex);
        ArrayList<String> mGroupMemberShipName = intent
                .getStringArrayListExtra(ContactSaveService.EXTRA_GROUP_MEMBERSHIP_NAME);
        ArrayList<String> mSelectedGroupsNameList = intent
                .getStringArrayListExtra(ContactSaveService.EXTRA_GROUP_NAME_LIST);

        // /operation in USIM card
        if (slotId >= 0 && SimCardUtils.isSimUsimType(slotId)) {
            isSuccess = operateUSIMGroupContacts(mGroupMemberShipName, mSelectedGroupsNameList, simIndex, slotId);
            if (!isSuccess) {
                LogUtils.i(TAG, "[saveGroupIdsToContact]" + EXTRA_CALLBACK_INTENT);
                Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
                callbackIntent.putExtra(EXTRA_RAW_CONTACTS_ID, -1);
                deliverCallback(callbackIntent);
                return;
            }
        }

        // /delete RawContact group info in DB
        isSuccess = true;
        long[] rawContactIds = intent.getLongArrayExtra(ContactSaveService.EXTRA_RAW_CONTACT_IDS);
        long mRawContactId = intent.getLongExtra(ContactSaveService.EXTRA_RAW_CONTACTS_ID, -1);
        deleteDataByContactInfo(rawContactIds, mRawContactId);

        // /insert RawContact group into DB
        ArrayList<Integer> mSelectedGroupsIdList = intent
                .getIntegerArrayListExtra(ContactSaveService.EXTRA_GROUP_ID_LIST);
        isSuccess = insertDataByNewGroupId(mSelectedGroupsIdList, rawContactIds, mRawContactId);

        // /check SIM Card status
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        LogUtils.i(TAG, "[saveGroupIdsToContact],isSuccess:" + isSuccess);
        if (!SimCardUtils.isSimInserted(slotId) && slotId >= 0) {
            LogUtils.d(TAG, "[saveGroupIdsToContact] Find sim removed");
            showMoveUSIMGroupErrorToast(GROUP_SIM_ABSENT, slotId);
            callbackIntent.putExtra(EXTRA_RAW_CONTACTS_ID, -1);
            deliverCallback(callbackIntent);
            return;
        }

        // / set RawContactId to call back intent if save groups success.
        if (isSuccess) {
            callbackIntent.putExtra(EXTRA_RAW_CONTACTS_ID, mRawContactId);
        } else {
            callbackIntent.putExtra(EXTRA_RAW_CONTACTS_ID, -1);
        }

        deliverCallback(callbackIntent);
    }
    /**@}*/

    private void updateGroup(Intent intent) {
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);
        long[] rawContactsToAdd = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_ADD);
        long[] rawContactsToRemove = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_REMOVE);

        // the following lines are provided and maintained by Mediatek Inc.
        int[] simIndexToAddArray = intent.getIntArrayExtra(ContactSaveService.EXTRA_SIM_INDEX_TO_ADD);
        int[] simIndexToRemoveArray = intent.getIntArrayExtra(ContactSaveService.EXTRA_SIM_INDEX_TO_REMOVE);
        int slotId = intent.getIntExtra(ContactSaveService.EXTRA_SLOT_ID, -1);
        String originalName = intent.getStringExtra(ContactSaveService.EXTRA_ORIGINAL_GROUP_NAME);
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        
        Log.i(TAG, "[updateGroup]groupName:" + label + " |groupId:" + groupId
                + "|originalName:" + originalName + " |slotId:" + slotId
                + " |accountName:" + accountName + " |accountType:"
                + accountType);
        
        if (groupId > 0 && label != null && !checkGroupNameExist(label, accountName, accountType, true)) {
            Log.d(TAG, "[updateGroup] Group Name exist!");
            Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
            callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.RELOAD);
            deliverCallback(callbackIntent);
            return;
        }
        
        int ugrpId = -1;
        if (slotId >= 0) {
            try {
                ugrpId = ContactsGroupUtils.USIMGroup.syncUSIMGroupUpdate(slotId, originalName,
                        label);
                Log.i(TAG, ugrpId + "---------ugrpId[updateGroup]");
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (USIMGroupException e) {
                Log.d(TAG, "[SyncUSIMGroup] catched USIMGroupException." + " ErrorType: "
                        + e.getErrorType());
                mSlotError.put(e.getErrorSlotId(), e.getErrorType());
                checkAllSlotErrors();
                Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
                if (e.getErrorType() == USIMGroupException.GROUP_NAME_OUT_OF_BOUND) {
                    callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY,
                            SaveMode.RELOAD);
                }
                Log.i(TAG, EXTRA_CALLBACK_INTENT);
                deliverCallback(callbackIntent);
                return;
            }
            if (ugrpId < 1) {
                Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
                Log.i(TAG, EXTRA_CALLBACK_INTENT);
                deliverCallback(callbackIntent);
                return;
            }
        }
        // the previous lines are provided and maintained by Mediatek Inc.

        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for updateGroup request");
            return;
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        
        if (groupId > 0) {
            final ContentResolver resolver = getContentResolver();
            final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
    
            // Update group name if necessary
            if (label != null) {
                ContentValues values = new ContentValues();
                values.put(Groups.TITLE, label);
                resolver.update(groupUri, values, null, null);
            }

            // Add and remove members if necessary
            boolean isRemoveSuccess = removeMembersFromGroup(resolver, rawContactsToRemove, groupId,
                    simIndexToRemoveArray, slotId, ugrpId);
            LogUtils.i(TAG, "isRemoveSuccess:" + isRemoveSuccess + ",groupUri:" + groupUri);

            boolean isAddSuccess = addMembersToGroup(resolver, rawContactsToAdd, groupId, simIndexToAddArray, slotId,
                    ugrpId);
            /** M: ALPS921231 when usim group action done, check if sim removed @{ */
            if (!SimCardUtils.isSimInserted(slotId) && slotId >= 0) {
                Log.d(TAG, "[updateGroup] Find sim removed");
                showMoveUSIMGroupErrorToast(GROUP_SIM_ABSENT, slotId);
                deliverCallback(callbackIntent);
                return;
            }
            /**@}*/
            LogUtils.i(TAG, "isAddSuccess:" + isAddSuccess + ",groupUri:" + groupUri);

            /** M: make sure both remove and add are successful! */
            if (isRemoveSuccess && isAddSuccess) {
                callbackIntent.setData(groupUri);
            } else {
                callbackIntent.setData(null);
                LogUtils.w(TAG, "createGroup(),setData == nul");
            }
        }

        deliverCallback(callbackIntent);
    }

    /** M:  delete :unused funcition@ { */
    /**
     * 
    private static void addMembersToGroup(ContentResolver resolver, long[] rawContactsToAdd,
            long groupId) {
        if (rawContactsToAdd == null) {
            return;
        }
        for (long rawContactId : rawContactsToAdd) {
            try {
                final ArrayList<ContentProviderOperation> rawContactOperations =
                        new ArrayList<ContentProviderOperation>();

                // Build an assert operation to ensure the contact is not already in the group
                final ContentProviderOperation.Builder assertBuilder = ContentProviderOperation
                        .newAssertQuery(Data.CONTENT_URI);
                assertBuilder.withSelection(Data.RAW_CONTACT_ID + "=? AND " +
                        Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                        new String[] { String.valueOf(rawContactId),
                        GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
                assertBuilder.withExpectedCount(0);
                rawContactOperations.add(assertBuilder.build());

                // Build an insert operation to add the contact to the group
                final ContentProviderOperation.Builder insertBuilder = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI);
                insertBuilder.withValue(Data.RAW_CONTACT_ID, rawContactId);
                insertBuilder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                insertBuilder.withValue(GroupMembership.GROUP_ROW_ID, groupId);
                rawContactOperations.add(insertBuilder.build());

                if (DEBUG) {
                    for (ContentProviderOperation operation : rawContactOperations) {
                        Log.v(TAG, operation.toString());
                    }
                }

                // Apply batch
                if (!rawContactOperations.isEmpty()) {
                    resolver.applyBatch(ContactsContract.AUTHORITY, rawContactOperations);
                }
            } catch (RemoteException e) {
                // Something went wrong, bail without success
                Log.e(TAG, "Problem persisting user edits for raw contact ID " +
                        String.valueOf(rawContactId), e);
            } catch (OperationApplicationException e) {
                // The assert could have failed because the contact is already in the group,
                // just continue to the next contact
                Log.w(TAG, "Assert failed in adding raw contact ID " +
                        String.valueOf(rawContactId) + ". Already exists in group " +
                        String.valueOf(groupId), e);
            }
        }
    }
     */
    /** @ } */

    /**
     * M: not used now, only for reference.
     * */
    /** google design method.
    private static void removeMembersFromGroup(ContentResolver resolver, long[] rawContactsToRemove,
            long groupId) {
        if (rawContactsToRemove == null) {
            return;
        }
        for (long rawContactId : rawContactsToRemove) {
            // Apply the delete operation on the data row for the given raw contact's
            // membership in the given group. If no contact matches the provided selection, then
            // nothing will be done. Just continue to the next contact.
            resolver.delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=? AND " +
                    Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                    new String[] { String.valueOf(rawContactId),
                    GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
        }
    }
    */

    /**
     * Creates an intent that can be sent to this service to star or un-star a contact.
     */
    public static Intent createSetStarredIntent(Context context, Uri contactUri, boolean value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_STARRED);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_STARRED_FLAG, value);

        return serviceIntent;
    }

    private void setStarred(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        boolean value = intent.getBooleanExtra(EXTRA_STARRED_FLAG, false);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setStarred request");
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(Contacts.STARRED, value);
        getContentResolver().update(contactUri, values, null, null);

        // Undemote the contact if necessary
        final Cursor c = getContentResolver().query(contactUri, new String[] {Contacts._ID},
                null, null, null);
        try {
            if (c.moveToFirst()) {
                final long id = c.getLong(0);

                // Don't bother undemoting if this contact is the user's profile.
                if (id < Profile.MIN_ID) {
                    values.clear();
                    values.put(String.valueOf(id), PinnedPositions.UNDEMOTE);
                    getContentResolver().update(PinnedPositions.UPDATE_URI, values, null, null);
                }
            }
        } finally {
            c.close();
        }
    }

    /**
     * Creates an intent that can be sent to this service to set the redirect to voicemail.
     */
    public static Intent createSetSendToVoicemail(Context context, Uri contactUri,
            boolean value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_SEND_TO_VOICEMAIL);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SEND_TO_VOICEMAIL_FLAG, value);

        return serviceIntent;
    }

    /**
     * Creates an intent that can be sent to this service to set the redirect to voicemail.
     */
    
    /*
     * New Feature by Mediatek Begin.            
     * Creates an intent that can be sent to this service to set the value of block video call        
     */
    public static Intent createSetBlockVideoCall(Context context, Uri contactUri, boolean value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_BLOCK_VIDEO_CALL);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_BLOCK_VIDEO_CALL_FLAG, value);

        return serviceIntent;
    }
    /*
     * New Feature  by Mediatek End.
    */
    
    private void setSendToVoicemail(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        boolean value = intent.getBooleanExtra(EXTRA_SEND_TO_VOICEMAIL_FLAG, false);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setRedirectToVoicemail");
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(Contacts.SEND_TO_VOICEMAIL, value);
        getContentResolver().update(contactUri, values, null, null);
    }

    /*
     * New Feature by Mediatek Begin.            
     * save the value of block video call to db        
     */
    private void setBlockVideoCall(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        boolean value = intent.getBooleanExtra(EXTRA_BLOCK_VIDEO_CALL_FLAG, false);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setBlockVideoCall");
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(Contacts.SEND_TO_VOICEMAIL_VT, value);
        getContentResolver().update(contactUri, values, null, null);
    }
    /*
     * New Feature  by Mediatek End.
    */
    
    /**
     * Creates an intent that can be sent to this service to save the contact's ringtone.
     */
    public static Intent createSetRingtone(Context context, Uri contactUri,
            String value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_RINGTONE);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CUSTOM_RINGTONE, value);

        return serviceIntent;
    }

    private void setRingtone(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        String value = intent.getStringExtra(EXTRA_CUSTOM_RINGTONE);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setRingtone");
            return;
        }
        ContentValues values = new ContentValues(1);
        values.put(Contacts.CUSTOM_RINGTONE, value);
        getContentResolver().update(contactUri, values, null, null);
    }

    /**
     * Creates an intent that sets the selected data item as super primary (default)
     */
    public static Intent createSetSuperPrimaryIntent(Context context, long dataId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_SUPER_PRIMARY);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_ID, dataId);
        return serviceIntent;
    }

    private void setSuperPrimary(Intent intent) {
        long dataId = intent.getLongExtra(EXTRA_DATA_ID, -1);
        if (dataId == -1) {
            Log.e(TAG, "Invalid arguments for setSuperPrimary request");
            return;
        }

        ContactUpdateUtils.setSuperPrimary(this, dataId);
    }

    /**
     * Creates an intent that clears the primary flag of all data items that belong to the same
     * raw_contact as the given data item. Will only clear, if the data item was primary before
     * this call
     */
    public static Intent createClearPrimaryIntent(Context context, long dataId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_CLEAR_PRIMARY);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_ID, dataId);
        return serviceIntent;
    }

    private void clearPrimary(Intent intent) {
        long dataId = intent.getLongExtra(EXTRA_DATA_ID, -1);
        if (dataId == -1) {
            Log.e(TAG, "Invalid arguments for clearPrimary request");
            return;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(Data.IS_SUPER_PRIMARY, 0);
        values.put(Data.IS_PRIMARY, 0);

        getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                values, null, null);
    }

    /**
     * Creates an intent that can be sent to this service to delete a contact.
     */
    public static Intent createDeleteContactIntent(Context context, Uri contactUri) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_DELETE_CONTACT);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        return serviceIntent;
    }

    private void deleteContact(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for deleteContact request");
            return;
        }

        getContentResolver().delete(contactUri, null, null);
    }

    /**
     * Creates an intent that can be sent to this service to join two contacts.
     */
    public static Intent createJoinContactsIntent(Context context, long contactId1,
            long contactId2, boolean contactWritable,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_JOIN_CONTACTS);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID1, contactId1);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID2, contactId2);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_WRITABLE, contactWritable);

        // Callback intent will be invoked by the service once the contacts are joined.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }


    private interface JoinContactQuery {
        String[] PROJECTION = {
                RawContacts._ID,
                RawContacts.CONTACT_ID,
                RawContacts.NAME_VERIFIED,
                RawContacts.DISPLAY_NAME_SOURCE,
        };

        String SELECTION = RawContacts.CONTACT_ID + "=? OR " + RawContacts.CONTACT_ID + "=?";

        int _ID = 0;
        int CONTACT_ID = 1;
        int NAME_VERIFIED = 2;
        int DISPLAY_NAME_SOURCE = 3;
    }

    private void joinContacts(Intent intent) {
        long contactId1 = intent.getLongExtra(EXTRA_CONTACT_ID1, -1);
        long contactId2 = intent.getLongExtra(EXTRA_CONTACT_ID2, -1);
        boolean writable = intent.getBooleanExtra(EXTRA_CONTACT_WRITABLE, false);
        if (contactId1 == -1 || contactId2 == -1) {
            Log.e(TAG, "Invalid arguments for joinContacts request");
            return;
        }

        final ContentResolver resolver = getContentResolver();

        // Load raw contact IDs for all raw contacts involved - currently edited and selected
        // in the join UIs
        Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                JoinContactQuery.SELECTION,
                new String[]{String.valueOf(contactId1), String.valueOf(contactId2)}, null);

        long rawContactIds[];
        long verifiedNameRawContactId = -1;
        try {
            if (c.getCount() == 0) {
                return;
            }
            int maxDisplayNameSource = -1;
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToPosition(i);
                long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
                int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
                if (nameSource > maxDisplayNameSource) {
                    maxDisplayNameSource = nameSource;
                }
            }

            // Find an appropriate display name for the joined contact:
            // if should have a higher DisplayNameSource or be the name
            // of the original contact that we are joining with another.
            if (writable) {
                for (int i = 0; i < rawContactIds.length; i++) {
                    c.moveToPosition(i);
                    if (c.getLong(JoinContactQuery.CONTACT_ID) == contactId1) {
                        int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
                        if (nameSource == maxDisplayNameSource
                                && (verifiedNameRawContactId == -1
                                        || c.getInt(JoinContactQuery.NAME_VERIFIED) != 0)) {
                            verifiedNameRawContactId = c.getLong(JoinContactQuery._ID);
                        }
                    }
                }
            }
        } finally {
            c.close();
        }

        // For each pair of raw contacts, insert an aggregation exception
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
                
                /*
                 * Bug Fix by Mediatek Begin.
                 *   Original Android's code:
                 *     xxx
                 *   CR ID: ALPS00272729
                 *   Descriptions: 
                 */
                if (operations.size() > 400) {
                    bufferOperations(operations, resolver);
                }
                /*
                 * Bug Fix by Mediatek End.
                 */
                
            }
        }

        // Mark the original contact as "name verified" to make sure that the contact
        // display name does not change as a result of the join
        if (verifiedNameRawContactId != -1) {
            Builder builder = ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, verifiedNameRawContactId));
            builder.withValue(RawContacts.NAME_VERIFIED, 1);
            operations.add(builder.build());
        }

        boolean success = false;
        // Apply all aggregation exceptions as one batch
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
            showToast(R.string.contactsJoinedMessage);
            success = true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        if (success) {
            Uri uri = RawContacts.getContactLookupUri(resolver,
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactIds[0]));
            callbackIntent.setData(uri);
        }
        deliverCallback(callbackIntent);
    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_TOGETHER} ContentProviderOperation.
     */
    private void buildJoinContactDiff(ArrayList<ContentProviderOperation> operations,
            long rawContactId1, long rawContactId2) {
        Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        operations.add(builder.build());
    }

    /**
     * Shows a toast on the UI thread.
     */
    private void showToast(final int message) {
        mMainHandler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(ContactSaveService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deliverCallback(final Intent callbackIntent) {
        mMainHandler.post(new Runnable() {

            @Override
            public void run() {
                deliverCallbackOnUiThread(callbackIntent);
            }
        });
    }

    void deliverCallbackOnUiThread(final Intent callbackIntent) {
        // TODO: this assumes that if there are multiple instances of the same
        // activity registered, the last one registered is the one waiting for
        // the callback. Validity of this assumption needs to be verified.
        LogUtils.d(TAG, "[deliverCallbackOnUiThread] entry.");
        for (Listener listener : sListeners) {
            if (callbackIntent.getComponent().equals(
                    ((Activity) listener).getIntent().getComponent())) {
                LogUtils.d(TAG, "service complete, notify listener: " + listener);
                listener.onServiceCompleted(callbackIntent);
                return;
            }
        }
    }

    /**
     * M: [Gemini+] all possible slot error can be safely put in this sparse int array.
     */
    private SparseIntArray mSlotError = new SparseIntArray();
    /** 
     * add two parm base on the function createNewGroupIntent
     * @param simIndex  
     * @param simSlotId
     * @return
     */
    public static Intent createNewGroupIntent(Context context, AccountWithDataSet account,
            String label, final long[] rawContactsToAdd, Class<? extends Activity> callbackActivity,
            String callbackAction, final int[] simIndexArray, int slotId) { 
        Intent serviceIntent = createNewGroupIntent(context, account, label,
                rawContactsToAdd, callbackActivity, callbackAction);

        serviceIntent.putExtra(ContactSaveService.EXTRA_SIM_INDEX_ARRAY, simIndexArray);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        
        Intent callbackIntent = serviceIntent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        callbackIntent.putExtra(EXTRA_NEW_GROUP_NAME, label);
        
        return serviceIntent;
    }
    
    /** M: change for CR ALPS00784408 @{ */
    /**
     * Creates an intent that can be sent to this service to create a new group as
     * well as add new members at the same time.
     *
     * @param currentRawContactId record this group created from which rawcontact.
     */
    public static Intent createNewGroupIntentFromRawContactEditor(Context context, AccountWithDataSet account,
            String label, final long[] rawContactsToAdd, Class<? extends Activity> callbackActivity,
            String callbackAction, long currentRawContactId, int slotId) {
        Intent intent = createNewGroupIntent(context, account, label, 
                rawContactsToAdd, callbackActivity, callbackAction);
        
        intent.putExtra(EXTRA_RAW_CONTACTS_ID, currentRawContactId);
        // add for contact detail group feature
        // when save group to USIM card it should add slotId, and should return the group name
        intent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        callbackIntent.putExtra(EXTRA_NEW_GROUP_NAME, label);
        
        return intent;
    }
    /** @} */

    /** 
     * update the group intent add four params then to ReName the group add 
     * addMembersToGroup or removeMembersFromGroup
     * @param OriginalGroupName 
     * @param slotId 
     * @param simIndexToAddArray  
     * @param simIndexToRemoveArray
     * @return
     */
    public static Intent createGroupUpdateIntent(Context context, long groupId, String newLabel,
            long[] rawContactsToAdd, long[] rawContactsToRemove,
            Class<? extends Activity> callbackActivity, String callbackAction, String OriginalGroupName, int slotId, 
            int[] simIndexToAddArray,int[] simIndexToRemoveArray, AccountWithDataSet account) {
        Intent serviceIntent = createGroupUpdateIntent(context, groupId, newLabel,
                rawContactsToAdd, rawContactsToRemove,
                 callbackActivity, callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SIM_INDEX_TO_ADD, simIndexToAddArray);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SIM_INDEX_TO_REMOVE, simIndexToRemoveArray);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ORIGINAL_GROUP_NAME, OriginalGroupName);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);

        Intent callbackIntent = serviceIntent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        
        return serviceIntent;
    }

    /**
     * M: update these groups intent including groupidlist,groupnamelist and
     * groupmembershipnamelist so on, then to add/remove the rawcontact to/from
     * these selected/unselected groups.
     * 
     * @param context
     * @param currentRawContactId
     * @param rawContactIds
     * @param isFromContactDetail
     * @param simIndex
     * @param callbackActivity
     * @param callbackAction
     * @param mSelectedGroupsIdList
     * @param mSelectedGroupsNameList
     * @param mGroupMemberShipName
     * @param simOrPhone
     * @param slotId
     * @return Intent to update groups info
     */
    public static Intent createContactDetailGroupUpdateIntent(Context context, long currentRawContactId,
            long[] rawContactIds, int simIndex,Class<? extends Activity> callbackActivity, 
            String callbackAction,ArrayList<Integer> mSelectedGroupsIdList, 
            ArrayList<String> mSelectedGroupsNameList,ArrayList<String> mGroupMemberShipName, 
            int slotId) {
        Intent saveIntent = createContactDetailGroupUpdateIntent(context, currentRawContactId, 
                rawContactIds, slotId, simIndex,callbackActivity, callbackAction);

        saveIntent.putIntegerArrayListExtra(ContactSaveService.EXTRA_GROUP_ID_LIST, mSelectedGroupsIdList);
        saveIntent.putStringArrayListExtra(ContactSaveService.EXTRA_GROUP_NAME_LIST, mSelectedGroupsNameList);
        saveIntent.putStringArrayListExtra(ContactSaveService.EXTRA_GROUP_MEMBERSHIP_NAME, mGroupMemberShipName);

        Intent callbackIntent = saveIntent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        //callbackIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);

        return saveIntent;
    }

    /**
     * M: to build an update groups list intent with rawcontactid, simindex and.
     * operation source flag.
     * 
     * @param context
     * @param currentRawContactId
     * @param rawContactIds
     * @param slotId
     * @param simIndex
     * @param callbackActivity
     * @param callbackAction
     * @return a internal service intent with callbackaction.
     */
    public static Intent createContactDetailGroupUpdateIntent(Context context, long currentRawContactId,
            long[] rawContactIds, int slotId, int simIndex, Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SAVE_GROUP_TO_CONTACT);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_ID, currentRawContactId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACT_IDS, rawContactIds);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SIM_INDEX, simIndex);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);

        // Callback intent will be invoked by the service once the group is
        // updated
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    /**
     * 
     * @param resolver
     * @param rawContactsToAdd
     * @param groupId
     * @param simIndexArry
     * @param slotId
     * @param ugrpId
     * @return true if the rawContactsToAdd are insert into IccProvider and ContactsProvider,false if some operation failed
     */
    private boolean addMembersToGroup(ContentResolver resolver, long[] rawContactsToAdd,
            long groupId, int[] simIndexArry, int slotId, int ugrpId) {
        boolean isSuccess  = true;
        if (rawContactsToAdd == null) {
            LogUtils.w(TAG, "addMembersToGroup(),the is null.");
            return false;
        }
        long rawContactId = -1;
        int simIndex = -1;
        int mCount = 0;
        final int MAX_OP_COUNT_IN_ONE_BATCH = 50;
        ArrayList<ContentProviderOperation> rawContactOperations = new ArrayList<ContentProviderOperation>();
        for (int i = 0, count = rawContactsToAdd.length; i < count; i++) {
            rawContactId = rawContactsToAdd[i];
            simIndex = simIndexArry[i];
            LogUtils.i(TAG, "[addMembersToGroup] slotId:" + slotId);
            LogUtils.i(TAG, "[addMembersToGroup] simIndex:" + simIndex);
            LogUtils.i(TAG, "[addMembersToGroup] ugrpId:" + ugrpId);
            boolean ret = false;
            if (slotId >= 0 && simIndex >= 0 && ugrpId >= 0) {
                ret = ContactsGroupUtils.USIMGroup.addUSIMGroupMember(slotId, simIndex, ugrpId);
                LogUtils.i(TAG, "[addMembersToGroup] ret " + ret);
                if (!ret){
                    LogUtils.w(TAG, "addMembersToGroup(),insert into iccProvider failed.");
                    isSuccess = false;
                    continue;
                }
            }

            // Build an assert operation to ensure the contact is not already in
            // the group
            final ContentProviderOperation.Builder assertBuilder = ContentProviderOperation
                    .newAssertQuery(Data.CONTENT_URI);
            assertBuilder.withSelection(Data.RAW_CONTACT_ID + "=? AND "
                    + Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?", 
                    new String[] { String.valueOf(rawContactId),
                            GroupMembership.CONTENT_ITEM_TYPE,
                            String.valueOf(groupId) });
            assertBuilder.withExpectedCount(0);
            rawContactOperations.add(assertBuilder.build());

            // Build an insert operation to add the contact to the group
            final ContentProviderOperation.Builder insertBuilder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            insertBuilder.withValue(Data.RAW_CONTACT_ID, rawContactId);
            insertBuilder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
            insertBuilder.withValue(GroupMembership.GROUP_ROW_ID, groupId);
            rawContactOperations.add(insertBuilder.build());

            if (DEBUG) {
                for (ContentProviderOperation operation : rawContactOperations) {
                    Log.v(TAG, operation.toString());
                }
            }
            mCount++;
            if (mCount > MAX_OP_COUNT_IN_ONE_BATCH) {
                Log.i(TAG, "mCount >MAX_OP_COUNT_IN_ONE_BATCH");
                try {
                    // Apply batch
                    ContentProviderResult[] results = null;
                    if (!rawContactOperations.isEmpty()) {
                        results = resolver.applyBatch(ContactsContract.AUTHORITY, rawContactOperations);
                    }
                } catch (RemoteException e) {
                    // Something went wrong, bail without success
                    Log.e(TAG, "Problem persisting user edits for raw contact ID "
                                    + String.valueOf(rawContactId), e);
                    isSuccess = false;
                } catch (OperationApplicationException e) {
                    // The assert could have failed because the contact is
                    // already in the group,
                    // just continue to the next contact
                    Log.w(TAG, "Assert failed in adding raw contact ID " + String.valueOf(rawContactId)
                            + ". Already exists in group " + String.valueOf(groupId), e);
                    isSuccess = false;
                }
                rawContactOperations.clear();
                mCount = 0;
            }
        }
        try {
            Log.i(TAG, "mCount<MAX_OP_COUNT_IN_ONE_BATCH");
            // Apply batch
            ContentProviderResult[] results = null;
            if (rawContactOperations.size() > 0) {
                if (DEBUG) {
                    for (ContentProviderOperation operation : rawContactOperations) {
                        Log.v(TAG, operation.toString());
                    }
                }
                results = resolver.applyBatch(ContactsContract.AUTHORITY, rawContactOperations);
            }
        } catch (RemoteException e) {
            // Something went wrong, bail without success
            Log.e(TAG, "Problem persisting user edits for raw contact ID ", e);
            isSuccess = false;
        } catch (OperationApplicationException e) {
            // The assert could have failed because the contact is already in
            // the group, just continue to the next contact
            Log.w(TAG, "Assert failed in adding raw contact ID " + ". Already exists in group ", e);
            isSuccess = false;
        }

        return isSuccess;
    }
    
    /**
     * M: To remove USIM group members and contactsprovider if necessary.
     * @param resolver
     * @param rawContactsToRemove
     * @param groupId
     * @param simIndexArray
     * @param slotId
     * @param ugrpId
     */
    private boolean removeMembersFromGroup(ContentResolver resolver, long[] rawContactsToRemove,
            long groupId, int[] simIndexArray, int slotId, int ugrpId) {

        boolean isRemoveSuccess = true;
        if (rawContactsToRemove == null) {
            LogUtils.d(TAG, "[removeMembersFromGroup]RawContacts to be removed is empty!");

            return isRemoveSuccess;
        }

        long rawContactId;
        int simIndex;
        for (int i = 0, count = rawContactsToRemove.length; i < count; i++) {
            rawContactId = rawContactsToRemove[i];
            simIndex = simIndexArray[i];
            boolean ret = false;
            if (slotId >= 0 && simIndex >= 0 && ugrpId >= 0) {
                ret = ContactsGroupUtils.USIMGroup.deleteUSIMGroupMember(slotId, simIndex, ugrpId);
                if (!ret) {
                    isRemoveSuccess = false;
                    LogUtils.i(TAG, "[removeMembersFromGroup]Remove failed RawContactid: " + rawContactId);
                    continue;
                }
            }

            /**
             * according to google method:
             * removeMembersFromGroup(resolver,rawContactsToRemove,groupId). @{
             */
            // Apply the delete operation on the data row for the given raw contact's
            // membership in the given group. If no contact matches the provided selection, then
            // nothing will be done. Just continue to the next contact.
            removeOneMemberFromGroup(resolver, rawContactId, groupId);
            /**@}*/
         }

        return isRemoveSuccess;
    }

    /**
     * M: fix ALPS00838002 can not delete the last contact in group.
     * 
     * @param resolver
     * @param rawContactId
     * @param groupId
     */
    private void removeOneMemberFromGroup(ContentResolver resolver, long rawContactId, long groupId) {
        /**
         * M: selection: mimetype=? AND _id=? AND (raw_contact_id IN (SELECT _id
         * FROM raw_contacts WHERE contact_id IN (SELECT contact_id FROM
         * raw_contacts WHERE _id=?)))
         * */
//        final String slection = Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=? AND ("
//                + Data.RAW_CONTACT_ID + " IN (SELECT " + RawContacts._ID + " FROM " + RAW_CONTACTS_TABLE + " WHERE "
//                + RawContacts.CONTACT_ID + " IN " + "(SELECT " + RawContacts.CONTACT_ID + " FROM " + RAW_CONTACTS_TABLE
//                + " WHERE " + RawContacts._ID + "=?)))";
//        final String[] args = new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId),
//                String.valueOf(rawContactId) };
//        resolver.delete(Data.CONTENT_URI, slection, args);

        /// roll back to original plan before ALPS00838002 fixed.
        resolver.delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=? AND " +
                Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                new String[] { String.valueOf(rawContactId),
                GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
    }

    /**
     * Creates an intent that can be sent to this service to delete a group.
     */
    public static Intent createGroupDeletionIntent(Context context, long groupId, int simId, int slotId, String groupLabel) {
        Intent serviceIntent = createGroupDeletionIntent(context, groupId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SIM_ID, simId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SLOT_ID, slotId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, groupLabel);
        return serviceIntent;
    }

    private void showMoveUSIMGroupErrorToast(int errCode, int slot) {
        Log.i(TAG, "[showMoveUSIMGroupErrorToast]errCode:" + errCode + "|slot:" + slot);
        /** M: Bug Fix for CR ALPS00451441 @{ */
        String toastMsg;
        boolean isSetRadioOn = SimCardUtils.isSetRadioOn(this.getContentResolver(), slot);
        if (errCode == USIMGroupException.GROUP_GENERIC_ERROR && !isSetRadioOn) {
            toastMsg = getString(R.string.save_group_fail);
        } else {
            toastMsg = getString(ContactsGroupUtils.USIMGroupException.getErrorToastId(errCode));
        }
        /** @} */
        final String msg = toastMsg;
        if (toastMsg != null) {
            Log.i(TAG, "[showMoveUSIMGroupErrorToast]toastMsg:" + toastMsg);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ContactSaveService.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /*
     * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
     * ALPS00272729 Descriptions:
     */
    public void bufferOperations(ArrayList<ContentProviderOperation> operations, ContentResolver resolver) {
        try {
            Log.i(TAG, "[bufferOperatation] begin applyBatch ");
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
            Log.i(TAG, "[bufferOperatation] end applyBatch");
            operations.clear();
        } catch (RemoteException e) {
            Log.e(TAG, "[bufferOperatation]Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        } catch (OperationApplicationException e) {
            Log.e(TAG, "[bufferOperatation]Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        }
    }

    /*
     * Bug Fix by Mediatek End.
     */

    private boolean checkGroupNameExist(String groupName, String accountName, String accountType, boolean showTips) {
        boolean nameExists = false;

        if (TextUtils.isEmpty(groupName)) {
            if (showTips) {
                showToast(R.string.name_needed);
            }
            return false;
        }
        Cursor cursor = getContentResolver().query(
                Groups.CONTENT_SUMMARY_URI,
                new String[] { Groups._ID },
                Groups.TITLE + "=? AND " + Groups.ACCOUNT_NAME + " =? AND " + Groups.ACCOUNT_TYPE + "=? AND "
                        + Groups.DELETED + "=0", new String[] { groupName, accountName, accountType }, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                nameExists = true;
            }
            cursor.close();
        }
        //If group name exists, make a toast and return false.
        if (nameExists) {
            if (showTips) {
                showToast(R.string.group_name_exists);
            }
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * M:[Gemini+] check all slot to find whether is there any error happened
     */
    private void checkAllSlotErrors() {
        for (int i = 0; i < mSlotError.size(); i++) {
            int slotId = mSlotError.keyAt(i);
            int errorCode = mSlotError.valueAt(i);
            Log.d(TAG, "[showToast] slot " + slotId + " encounter a problem: " + errorCode);
            showMoveUSIMGroupErrorToast(errorCode, slotId);
        }
    }
    /** M: Bug Fix for CR ALPS00463033 about after delete group successful display wrong@{ */
    public static interface DeleteEndListener {
        public void onDeleteEnd();

        public void onDeleteStart();
    }

    private static DeleteEndListener sDeleteEndListener;

    public static void setDeleteEndListener(DeleteEndListener listener) {

        sDeleteEndListener = listener;
    }

    public static void removeDeleteEndListener(DeleteEndListener listener) {
        sDeleteEndListener = null;
    }
    /** @} */
    
    /**
     * M: fixed CR ALPS00542175 @ {
     */
    private static boolean mIsTransactionProcessing = false;
    public static synchronized boolean isGroupTransactionProcessing() {
        return mIsTransactionProcessing;
    }
    /** @} */
    
    /**
     * M:the following lines are provided and maintained by Mediatek Inc.@{
     * */
    // / used to save groupid for the contact.
    public static final String ACTION_SAVE_GROUP_TO_CONTACT = "saveGroupToContact";
    
    // / add to update group infos intent keys from groupeditorfragment.
    public static final String EXTRA_SIM_INDEX_TO_ADD = "simIndexToAdd";
    public static final String EXTRA_SIM_INDEX_TO_REMOVE = "simIndexToRemove";
    public static final String EXTRA_ORIGINAL_GROUP_NAME = "originalGroupName";

    public static final String EXTRA_SIM_INDEX_ARRAY = "simIndexArray";
    public static final String EXTRA_SLOT_ID = "slotId";
    public static final String EXTRA_SIM_ID = "simId";

    // / add new group name as back item of GroupCreationDialogFragment
    public static final String EXTRA_NEW_GROUP_NAME = "addGroupName";

    // / add update group info from contactDetailfragment.
    public static final String EXTRA_SIM_OR_PHONE = "simOrPhone";
    public static final String EXTRA_SIM_INDEX = "simIndex";
    public static final String EXTRA_RAW_CONTACT_IDS = "rawContactIds";
    public static final String EXTRA_GROUP_ID_LIST = "groupIdList";
    public static final String EXTRA_GROUP_NAME_LIST = "groupNameList";
    public static final String EXTRA_GROUP_MEMBERSHIP_NAME = "groupMembershipName";
    /**@}*/

    // The following lines are provided and maintained by Mediatek Inc.
    public static final String RAW_CONTACTS_TABLE = "raw_contacts";
    // The previous lines are provided and maintained by Mediatek Inc.
}
