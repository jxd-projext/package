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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.content.BroadcastReceiver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorAccountsChangedActivity;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.activities.JoinContactActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.detail.PhotoSelectionHandler;
import com.android.contacts.editor.AggregationSuggestionEngine.Suggestion;
import com.android.contacts.editor.Editor.EditorListener;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.util.HelpUtils;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.contacts.util.UiClosables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/// The following lines are provided and maintained by Mediatek In
import android.text.TextUtils;
import android.view.WindowManager;

import com.android.contacts.activities.ContactDetailActivity;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.util.PhoneCapabilityTester;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.contacts.extension.aassne.SimUtils;
import com.mediatek.contacts.extension.aassne.SneExt;
import com.mediatek.contacts.simservice.SIMEditProcessor;
import com.mediatek.contacts.simservice.SIMProcessorManager;
import com.mediatek.contacts.simservice.SIMProcessorService;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.ContactsSettingsUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.ProgressHandler;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

//import com.mediatek.lowstorage.LowStorageHandle; /* TODO: NEEDTOCHECK*/
/// The previous lines are provided and maintained by Mediatek Inc.

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ContactEditorFragment extends Fragment implements
        SplitContactConfirmationDialogFragment.Listener,
        AggregationSuggestionEngine.Listener, AggregationSuggestionView.Listener,
        RawContactReadOnlyEditorView.Listener {

    private static final String TAG = ContactEditorFragment.class.getSimpleName();

    private static final int LOADER_DATA = 1;
    private static final int LOADER_GROUPS = 2;

    private static final String KEY_URI = "uri";
    private static final String KEY_ACTION = "action";
    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_RAW_CONTACT_ID_REQUESTING_PHOTO = "photorequester";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";
    private static final String KEY_CURRENT_PHOTO_URI = "currentphotouri";
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";
    private static final String KEY_CONTACT_WRITABLE_FOR_JOIN = "contactwritableforjoin";
    private static final String KEY_SHOW_JOIN_SUGGESTIONS = "showJoinSuggestions";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NEW_LOCAL_PROFILE = "newLocalProfile";
    private static final String KEY_IS_USER_PROFILE = "isUserProfile";
    private static final String KEY_UPDATED_PHOTOS = "updatedPhotos";
    private static final String KEY_IS_EDIT = "isEdit";
    private static final String KEY_HAS_NEW_CONTACT = "hasNewContact";
    private static final String KEY_NEW_CONTACT_READY = "newContactDataReady";
    private static final String KEY_EXISTING_CONTACT_READY = "existingContactDataReady";
    private static final String KEY_RAW_CONTACTS = "rawContacts";

    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";


    /**
     * An intent extra that forces the editor to add the edited contact
     * to the default group (e.g. "My Contacts").
     */
    public static final String INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY = "addToDefaultDirectory";

    public static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    /**
     * Modes that specify what the AsyncTask has to perform after saving
     */
    public interface SaveMode {
        /**
         * Close the editor after saving
         */
        public static final int CLOSE = 0;

        /**
         * Reload the data so that the user can continue editing
         */
        public static final int RELOAD = 1;

        /**
         * Split the contact after saving
         */
        public static final int SPLIT = 2;

        /**
         * Join another contact after saving
         */
        public static final int JOIN = 3;

        /**
         * Navigate to Contacts Home activity after saving.
         */
        public static final int HOME = 4;
    }

    private interface Status {
        /**
         * The loader is fetching data
         */
        public static final int LOADING = 0;

        /**
         * Not currently busy. We are waiting for the user to enter data
         */
        public static final int EDITING = 1;

        /**
         * The data is currently being saved. This is used to prevent more
         * auto-saves (they shouldn't overlap)
         */
        public static final int SAVING = 2;

        /**
         * Prevents any more saves. This is used if in the following cases:
         * - After Save/Close
         * - After Revert
         * - After the user has accepted an edit suggestion
         */
        public static final int CLOSING = 3;

        /**
         * Prevents saving while running a child activity.
         */
        public static final int SUB_ACTIVITY = 4;
    }

    private static final int REQUEST_CODE_JOIN = 0;
    private static final int REQUEST_CODE_ACCOUNTS_CHANGED = 1;

    /**
     * The raw contact for which we started "take photo" or "choose photo from gallery" most
     * recently.  Used to restore {@link #mCurrentPhotoHandler} after orientation change.
     */
    private long mRawContactIdRequestingPhoto;
    /**
     * The {@link PhotoHandler} for the photo editor for the {@link #mRawContactIdRequestingPhoto}
     * raw contact.
     *
     * A {@link PhotoHandler} is created for each photo editor in {@link #bindPhotoHandler}, but
     * the only "active" one should get the activity result.  This member represents the active
     * one.
     */
    private PhotoHandler mCurrentPhotoHandler;

    private final EntityDeltaComparator mComparator = new EntityDeltaComparator();

    private Cursor mGroupMetaData;

    private Uri mCurrentPhotoUri;
    private Bundle mUpdatedPhotos = new Bundle();

    private Context mContext;
    private String mAction;
    private Uri mLookupUri;
    private Bundle mIntentExtras;
    private Listener mListener;

    private long mContactIdForJoin;
    private boolean mContactWritableForJoin;

    private ContactEditorUtils mEditorUtils;

    private LinearLayout mContent;
    private RawContactDeltaList mState;

    private ViewIdGenerator mViewIdGenerator;

    private long mLoaderStartTime;

    private int mStatus;

    // Whether to show the new contact blank form and if it's corresponding delta is ready.
    private boolean mHasNewContact = false;
    private boolean mNewContactDataReady = false;

    // Whether it's an edit of existing contact and if it's corresponding delta is ready.
    private boolean mIsEdit = false;
    private boolean mExistingContactDataReady = false;

    // This is used to pre-populate the editor with a display name when a user edits a read-only
    // contact.
    private String mDefaultDisplayName;

    // Used to temporarily store existing contact data during a rebind call (i.e. account switch)
    private ImmutableList<RawContact> mRawContacts;

    private AggregationSuggestionEngine mAggregationSuggestionEngine;
    private long mAggregationSuggestionsRawContactId;
    private View mAggregationSuggestionView;

    private ListPopupWindow mAggregationSuggestionPopup;

    private static final class AggregationSuggestionAdapter extends BaseAdapter {
        private final Activity mActivity;
        private final boolean mSetNewContact;
        private final AggregationSuggestionView.Listener mListener;
        private final List<Suggestion> mSuggestions;

        public AggregationSuggestionAdapter(Activity activity, boolean setNewContact,
                AggregationSuggestionView.Listener listener, List<Suggestion> suggestions) {
            mActivity = activity;
            mSetNewContact = setNewContact;
            mListener = listener;
            mSuggestions = suggestions;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Suggestion suggestion = (Suggestion) getItem(position);
            LayoutInflater inflater = mActivity.getLayoutInflater();
            AggregationSuggestionView suggestionView =
                    (AggregationSuggestionView) inflater.inflate(
                            R.layout.aggregation_suggestions_item, null);
            suggestionView.setNewContact(mSetNewContact);
            suggestionView.setListener(mListener);
            suggestionView.bindSuggestion(suggestion);
            return suggestionView;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mSuggestions.get(position);
        }

        @Override
        public int getCount() {
            return mSuggestions.size();
        }
    }

    private OnItemClickListener mAggregationSuggestionItemClickListener =
            new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final AggregationSuggestionView suggestionView = (AggregationSuggestionView) view;
            suggestionView.handleItemClickEvent();
            UiClosables.closeQuietly(mAggregationSuggestionPopup);
            mAggregationSuggestionPopup = null;
        }
    };

    private boolean mAutoAddToDefaultGroup;

    private boolean mEnabled = true;
    private boolean mRequestFocus;
    private boolean mNewLocalProfile = false;
    private boolean mIsUserProfile = false;

    public ContactEditorFragment() {
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mContent != null) {
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    mContent.getChildAt(i).setEnabled(enabled);
                }
            }
            setAggregationSuggestionViewEnabled(enabled);
            final Activity activity = getActivity();
            if (activity != null) activity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mEditorUtils = ContactEditorUtils.getInstance(mContext);
    }

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();

        /** M:AAS @ { */
        SimUtils.updateLabelViews(mContext, mSlotId, mState, mContent);
        /** M: @ } */
    }
    
    @Override
    public void onStop() {
        super.onStop();

        UiClosables.closeQuietly(mAggregationSuggestionPopup);

        // If anything was left unsaved, save it now but keep the editor open.
        if (!getActivity().isChangingConfigurations() && mStatus == Status.EDITING) {
            // The following lines are provided and maintained by Mediatek inc.
            Log.i(TAG, "[onStop] and the mIsSaveToSim is " + mIsSaveToSim + " | isSimType() : "
                    + isSimType());
            if (mIsSaveToSim || isSimType()) {
                mIsSaveToSim = false;
                return;
            }
            /** M: New Feature */
            //remove popupmenu when the acticity is call onStop
            if (null != mContent && mContent.getChildCount() > 0) {
                RawContactEditorView editor = (RawContactEditorView) mContent.getChildAt(0);
                editor.dismissAddFieldPopupMenu();
            }
            ///M:fix ALPS01187562 remove dialog when the activity is call onStop@{
            ContactEditorActivity activity = (ContactEditorActivity) mContext;
            activity.getDialogManager().removeAllDialogs();
            ///@}

            /** @} */
            // The following lines are provided and maintained by Mediatek inc.
            save(SaveMode.RELOAD);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAggregationSuggestionEngine != null) {
            mAggregationSuggestionEngine.quit();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_editor_fragment, container, false);

        mContent = (LinearLayout) view.findViewById(R.id.editors);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        validateAction(mAction);

        if (mState.isEmpty()) {
            // The delta list may not have finished loading before orientation change happens.
            // In this case, there will be a saved state but deltas will be missing.  Reload from
            // database.
            if (Intent.ACTION_EDIT.equals(mAction)) {
                // Either...
                // 1) orientation change but load never finished.
                // or
                // 2) not an orientation change.  data needs to be loaded for first time.
                getLoaderManager().initLoader(LOADER_DATA, null, mDataLoaderListener);
            }
        } else {
            // Orientation change, we already have mState, it was loaded by onCreate
            bindEditors();
        }

        // Handle initial actions only when existing state missing
        if (savedInstanceState == null) {
            if (Intent.ACTION_EDIT.equals(mAction)) {
                mIsEdit = true;
            } else if (Intent.ACTION_INSERT.equals(mAction)) {
                mHasNewContact = true;
                final Account account = mIntentExtras == null ? null :
                        (Account) mIntentExtras.getParcelable(Intents.Insert.ACCOUNT);
                final String dataSet = mIntentExtras == null ? null :
                        mIntentExtras.getString(Intents.Insert.DATA_SET);

                if (account != null) {
                    // Account specified in Intent
                    createContact(new AccountWithDataSet(account.name, account.type, dataSet));
                } else {
                    // No Account specified. Let the user choose
                    // Load Accounts async so that we can present them
                    selectAccountAndCreateContact();
                }
            }
        }
        /** M:ALPS00403629 To show InputMetod @{ */
        setInputMethodVisible(mIsShowIME);
        /** @}*/
    }

    /**
     * Checks if the requested action is valid.
     *
     * @param action The action to test.
     * @throws IllegalArgumentException when the action is invalid.
     */
    private void validateAction(String action) {
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_INSERT.equals(action) ||
                ContactEditorActivity.ACTION_SAVE_COMPLETED.equals(action)) {
            return;
        }
        throw new IllegalArgumentException("Unknown Action String " + mAction +
                ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT + " or " +
                ContactEditorActivity.ACTION_SAVE_COMPLETED);
    }


    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        ///M:fix ALPS01020577,Original Google Code
        ///mAction = action;
        ///mLookupUri = lookupUri;
        //There only has 2 place will call this function,from:onSaveCompleted() or ContactEditorActivity
        //When from ContactEditorActivity:first enter EditorFragment or restart ContactEditorActivity
        //when from onSaveCompleted():when saved complete,the action will always be Intent.ACTION_EDIT
        //So here should check the mAction to avoid reset it to older value
        if (mAction == null || (action == Intent.ACTION_EDIT && mAction != null)) {
            mAction = action;
            mLookupUri = lookupUri;
            LogUtils.d(TAG, "mAction:" + mAction + ",mLookupUri:" + mLookupUri + ",action:" + action + ",lookupUri:"
                    + lookupUri);
        }
        ///@}
        mIntentExtras = intentExtras;
        mAutoAddToDefaultGroup = mIntentExtras != null
                && mIntentExtras.containsKey(INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY);
        mNewLocalProfile = mIntentExtras != null
                && mIntentExtras.getBoolean(INTENT_EXTRA_NEW_LOCAL_PROFILE);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            // Restore mUri before calling super.onCreate so that onInitializeLoaders
            // would already have a uri and an action to work with
            mLookupUri = savedState.getParcelable(KEY_URI);
            mAction = savedState.getString(KEY_ACTION);
        }

        super.onCreate(savedState);

        if (savedState == null) {
            // If savedState is non-null, onRestoreInstanceState() will restore the generator.
            mViewIdGenerator = new ViewIdGenerator();
        } else {
            // Read state from savedState. No loading involved here
            mState = savedState.<RawContactDeltaList> getParcelable(KEY_EDIT_STATE);
            mRawContactIdRequestingPhoto = savedState.getLong(
                    KEY_RAW_CONTACT_ID_REQUESTING_PHOTO);
            mViewIdGenerator = savedState.getParcelable(KEY_VIEW_ID_GENERATOR);
            mCurrentPhotoUri = savedState.getParcelable(KEY_CURRENT_PHOTO_URI);
            mContactIdForJoin = savedState.getLong(KEY_CONTACT_ID_FOR_JOIN);
            mContactWritableForJoin = savedState.getBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN);
            mAggregationSuggestionsRawContactId = savedState.getLong(KEY_SHOW_JOIN_SUGGESTIONS);
            mEnabled = savedState.getBoolean(KEY_ENABLED);
            mStatus = savedState.getInt(KEY_STATUS);
            mNewLocalProfile = savedState.getBoolean(KEY_NEW_LOCAL_PROFILE);
            mIsUserProfile = savedState.getBoolean(KEY_IS_USER_PROFILE);
            mUpdatedPhotos = savedState.getParcelable(KEY_UPDATED_PHOTOS);
            mIsEdit = savedState.getBoolean(KEY_IS_EDIT);
            mHasNewContact = savedState.getBoolean(KEY_HAS_NEW_CONTACT);
            mNewContactDataReady = savedState.getBoolean(KEY_NEW_CONTACT_READY);
            mExistingContactDataReady = savedState.getBoolean(KEY_EXISTING_CONTACT_READY);
            mRawContacts = ImmutableList.copyOf(savedState.<RawContact>getParcelableArrayList(
                    KEY_RAW_CONTACTS));

            /** M: Bug Fix for ALPS00393227 @{ */
            if (mState == null  && mStatus != Status.SUB_ACTIVITY) {
                Log.i(TAG, " Fragment mState is null");
                getActivity().finish();
            }
            /** @} */
            /*
             * Bug Fix by Mediatek Begin.
             *   Original Android's code:
             *     xxx
             *   CR ID: ALPS00267947
             *   Descriptions: add simid and slotid
             */
            mSlotId = savedState.getInt(KEY_SLOTID);
            /** M:AAS @ { */
            ExtensionManager.getInstance().getContactAccountExtension().setCurrentSlot(mSlotId,
                    ExtensionManager.COMMD_FOR_AAS);
            /** M: @ } */
            mSimId = savedState.getLong(KEY_SIMID);
            mSaveModeForSim = savedState.getInt(KEY_SAVEMODE_FOR_SIM);
            mIndicatePhoneOrSimContact = savedState.getInt(KEY_INDICATE_PHONE_OR_SIM);
            mOldState = savedState.<RawContactDeltaList> getParcelable(KEY_OLDSTATE);
            mSimIndex = savedState.getInt(KEY_SIM_INDEX);
            Log.i(TAG, "[onCreate] mSlotid : " + mSlotId + " | mSimId : " + mSimId
                    + " | mSaveModeForSim : " + mSaveModeForSim);
            if (null != mOldState && mOldState.size() > 3) {
                for (int i = 0; i < 3; i++) {
                    Log.i(TAG, "[onCreate] mOldState size = " + mOldState.size() + " , mOldState ("
                            + i + ") : " + mOldState.get(i));
                }
            } else {
                Log.i(TAG, "[onCreate] mOldState : " + mOldState);
            }
            /*
             * Bug Fix by Mediatek End.
             */

        }

        // mState can still be null because it may not have have finished loading before
        // onSaveInstanceState was called.
        if (mState == null) {
            mState = new RawContactDeltaList();
        }
    }

    public void setData(Contact contact) {

        // If we have already loaded data, we do not want to change it here to not confuse the user
        if (!mState.isEmpty()) {
            Log.v(TAG, "Ignoring background change. This will have to be rebased later");
            return;
        }

        // See if this edit operation needs to be redirected to a custom editor
        mRawContacts = contact.getRawContacts();
        if (mRawContacts.size() == 1) {
            RawContact rawContact = mRawContacts.get(0);
            String type = rawContact.getAccountTypeString();
            String dataSet = rawContact.getDataSet();
            AccountType accountType = rawContact.getAccountType(mContext);
            if (accountType.getEditContactActivityClassName() != null &&
                    !accountType.areContactsWritable()) {
                if (mListener != null) {
                    String name = rawContact.getAccountName();
                    long rawContactId = rawContact.getId();
                    mListener.onCustomEditContactActivityRequested(
                            new AccountWithDataSet(name, type, dataSet),
                            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                            mIntentExtras, true);
                }
                return;
            }
        }

        String displayName = null;
        // Check for writable raw contacts.  If there are none, then we need to create one so user
        // can edit.  For the user profile case, there is already an editable contact.
        if (!contact.isUserProfile() && !contact.isWritableContact(mContext)) {
            mHasNewContact = true;

            // This is potentially an asynchronous call and will add deltas to list.
            selectAccountAndCreateContact();
            displayName = contact.getDisplayName();
        }

        /**
         * M: init the fields for IccCard related features @{
         */
        mIndicatePhoneOrSimContact = contact.getIndicate();
        mSlotId = contact.getSlot();
        /** M:AAS @ { */
        ExtensionManager.getInstance().getContactAccountExtension().setCurrentSlot(mSlotId, ExtensionManager.COMMD_FOR_AAS);
        /** M: @ } */
        Log.i(TAG, "[bindEditorsForExistingContact]the indicate " + "is = "
                + mIndicatePhoneOrSimContact);
        Log.i(TAG, "[bindEditorsForExistingContact]the mSlotId is = " + mSlotId);
        /**
         * M: init the fields for IccCard related features @}
         */

        // This also adds deltas to list
        // If displayName is null at this point it is simply ignored later on by the editor.
        bindEditorsForExistingContact(displayName, contact.isUserProfile(),
                mRawContacts);
    }

    @Override
    public void onExternalEditorRequest(AccountWithDataSet account, Uri uri) {
        mListener.onCustomEditContactActivityRequested(account, uri, null, false);
    }

    private void bindEditorsForExistingContact(String displayName, boolean isUserProfile,
            ImmutableList<RawContact> rawContacts) {
        setEnabled(true);

        /**
         * M: init MTK fields for IccCard related features @{
         */
        Log.i(TAG, "call bindEditorsforExistingContact");
        /*
         * New Feature by Mediatek Begin. Original Android's code: CR
         * ID:ALPS00101852 Descriptions: insert data to SIM/USIM.
         */
        mSaveModeForSim = 2;
        mOldState = new RawContactDeltaList();
        mOldState.addAll(rawContacts.iterator());
        if (mOldState.size() > 3) {
            for (int i = 0; i < 3; i++) {
                Log.i(TAG, "[bindEditorsForExistingContact] mOldState size = " + mOldState.size()
                        + " , mOldState (" + i + ") : " + mOldState.get(i));
            }
        } else {
            Log.i(TAG, "[bindEditorsForExistingContact] mOldState : " + mOldState);
        }
        /**
         * M: init MTK fields for IccCard related features @}
         */

        mDefaultDisplayName = displayName;

        mState.addAll(rawContacts.iterator());

        /**
         * M: MTK start
         */
        String accountType = mState.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        if (accountType != null) {
            // UIM
            // if(accountType.equals(mSimAccountType)||accountType.equals(mUsimAccountType)){
            if (accountType.equals(mSimAccountType) || accountType.equals(mUsimAccountType)
                    || accountType.equals(mUimAccountType)) {
                // UIM
                mIsSimType = true;
            }
        }
        /*
         * Change Feature by Mediatek End.
         */
        /**
         * M: MTK end
         */


        setIntentExtras(mIntentExtras);
        mIntentExtras = null;

        // For user profile, change the contacts query URI
        mIsUserProfile = isUserProfile;
        boolean localProfileExists = false;

        if (mIsUserProfile) {
            for (RawContactDelta state : mState) {
                // For profile contacts, we need a different query URI
                state.setProfileQueryUri();
                // Try to find a local profile contact
                if (state.getValues().getAsString(RawContacts.ACCOUNT_TYPE) == null) {
                    localProfileExists = true;
                }
            }
            // Editor should always present a local profile for editing
            if (!localProfileExists) {
                final RawContact rawContact = new RawContact();
                rawContact.setAccountToLocal();

                RawContactDelta insert = new RawContactDelta(ValuesDelta.fromAfter(
                        rawContact.getValues()));
                insert.setProfileQueryUri();
                mState.add(insert);
            }
        }
        mRequestFocus = true;
        mExistingContactDataReady = true;
        bindEditors();
    }

    /**
     * Merges extras from the intent.
     */
    public void setIntentExtras(Bundle extras) {
        if (extras == null || extras.size() == 0) {
            return;
        }

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        /// change for CR ALPS00784408
        long raw_contactId = extras.getLong(ContactSaveService.EXTRA_RAW_CONTACTS_ID, -1);
        for (RawContactDelta state : mState) {
            final AccountType type = state.getAccountType(accountTypes);

            /** M: change for CR ALPS00784408 @{
             * goto the right rawcontact to parse extras.
             * rawContactId will not be "-1" if create new group from a RawContactEditorView.
             * */
            boolean skip = true;
            if (state.getRawContactId() == raw_contactId || raw_contactId == -1) {
                Log.i(TAG, "[setIntentExtras] state.getRawContactId()=" + state.getRawContactId() + " raw_contactId ="
                        + raw_contactId);
                skip = false;
            }
            if (skip) {
                continue;
            }
            /** @} */

            if (type.areContactsWritable()) {
                // Apply extras to the first writable raw contact only
                RawContactModifier.parseExtras(mContext, type, state, extras);
                /*
                 * Bug Fix by Mediatek Begin.
                 *   Original Android's code:
                 *     xxx
                 *   CR ID: ALPS00230431
                 *   Descriptions: 
                 */
                if (RawContactModifier.mIsSimType) {
                    RawContactModifier.mIsSimType = false;
                    Toast.makeText(getActivity(), R.string.add_SIP_to_sim,
                            Toast.LENGTH_LONG).show();
                    getActivity().finish();
                } else if (RawContactModifier.mHasSip) {
                    RawContactModifier.mHasSip = false;
                    Toast.makeText(getActivity(), R.string.already_has_SIP,
                            Toast.LENGTH_LONG).show();
                }
                /*
                 * Bug Fix by Mediatek End.
                 */
                break;
            }
        }
    }

    private void selectAccountAndCreateContact() {
        // If this is a local profile, then skip the logic about showing the accounts changed
        // activity and create a phone-local contact.
        if (mNewLocalProfile) {
            createContact(null);
            return;
        }

        // If there is no default account or the accounts have changed such that we need to
        // prompt the user again, then launch the account prompt.

        /*
         * Change Feature by Mediatek Begin. Original Android's code:
         * if(mEditorUtils.shouldShowAccountChangedNotification()) { CR
         * ID:ALPS00101852 Descriptions: Always popup create contact dialog.
         */
        final List<AccountWithDataSet> accountsNum = AccountTypeManager.getInstance(mContext)
                .getAccounts(true);
        Log.i(TAG, "call selectAccountAndCreateContact  mNewLocalProfile = " + mNewLocalProfile
                + "accountsNum.size() = " + accountsNum.size());
        if (mEditorUtils.shouldShowAccountChangedNotification() || accountsNum.size() > 1) {
            /*
             * Change Feature by Mediatek End.
             */
            Intent intent = new Intent(mContext, ContactEditorAccountsChangedActivity.class);

            //M:Add account type for handling special case when add new contactor@ {
            intent.putExtra(ContactsSettingsUtils.ACCOUNT_TYPE,
                        getActivity().getIntent().getIntExtra(ContactsSettingsUtils.ACCOUNT_TYPE,
                                                ContactsSettingsUtils.ALL_TYPE_ACCOUNT));
            //}
            mStatus = Status.SUB_ACTIVITY;
            /** M:ALPS00403629 To hide InputMetod @{ */
            mIsShowIME = false;
            /** @}*/
            startActivityForResult(intent, REQUEST_CODE_ACCOUNTS_CHANGED);
        } else {
            // Otherwise, there should be a default account. Then either create a local contact
            // (if default account is null) or create a contact with the specified account.
            AccountWithDataSet defaultAccount = mEditorUtils.getDefaultAccount();
            if (defaultAccount == null) {
                createContact(null);
            } else {
                createContact(defaultAccount);
            }
        }
    }

    /**
     * Create a contact by automatically selecting the first account. If there's no available
     * account, a device-local contact should be created.
     */
    private void createContact() {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(mContext).getAccounts(true);
        // No Accounts available. Create a phone-local contact.
        if (accounts.isEmpty()) {
            createContact(null);
            return;
        }

        // We have an account switcher in "create-account" screen, so don't need to ask a user to
        // select an account here.
        createContact(accounts.get(0));
    }

    /**
     * Shows account creation screen associated with a given account.
     *
     * @param account may be null to signal a device-local contact should be created.
     */
    private void createContact(AccountWithDataSet account) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        final AccountType accountType =
                accountTypes.getAccountType(account != null ? account.type : null,
                        account != null ? account.dataSet : null);

        if (accountType.getCreateContactActivityClassName() != null) {
            if (mListener != null) {
                mListener.onCustomCreateContactActivityRequested(account, mIntentExtras);
            }
        } else {
            bindEditorsForNewContact(account, accountType);
        }
    }

    /**
     * Removes a current editor ({@link #mState}) and rebinds new editor for a new account.
     * Some of old data are reused with new restriction enforced by the new account.
     *
     * @param oldState Old data being edited.
     * @param oldAccount Old account associated with oldState.
     * @param newAccount New account to be used.
     */
    /* Unused function
    private void rebindEditorsForNewContact(
            RawContactDelta oldState, AccountWithDataSet oldAccount,
            AccountWithDataSet newAccount) {
        AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        AccountType oldAccountType = accountTypes.getAccountType(
                oldAccount.type, oldAccount.dataSet);
        AccountType newAccountType = accountTypes.getAccountType(
                newAccount.type, newAccount.dataSet);

        if (newAccountType.getCreateContactActivityClassName() != null) {
            Log.w(TAG, "external activity called in rebind situation");
            if (mListener != null) {
                mListener.onCustomCreateContactActivityRequested(newAccount, mIntentExtras);
            }
        } else {
            mExistingContactDataReady = false;
            mNewContactDataReady = false;
            mState = new RawContactDeltaList();
            bindEditorsForNewContact(newAccount, newAccountType, oldState, oldAccountType);
            if (mIsEdit) {
                bindEditorsForExistingContact(mDefaultDisplayName, mIsUserProfile, mRawContacts);
            }
        }
    }
    */

    private void bindEditorsForNewContact(AccountWithDataSet account,
            final AccountType accountType) {
        bindEditorsForNewContact(account, accountType, null, null);
    }

    private void bindEditorsForNewContact(AccountWithDataSet newAccount,
            final AccountType newAccountType, RawContactDelta oldState,
            AccountType oldAccountType) {
        mStatus = Status.EDITING;
        Log.i(TAG, "call bindEditorsForNewContact  newAccount = " + newAccount
                + "=== newAccountType = " + newAccountType + "===oldState = " + oldState
                + "== oldAccountType = " + oldAccountType);
        /*
         * New Feature by Mediatek Begin. Original Android's code: CR
         * ID:ALPS00101852 Descriptions: insert data to SIM/USIM.
         */
        mSaveModeForSim = 1;
        if (newAccountType != null) {

            if (mSimAccountType.equals(newAccountType.accountType)
                    || mUsimAccountType.equals(newAccountType.accountType)
                    || mUimAccountType.equals(newAccountType.accountType)) {
                mIsSimType = true;
            }
        }
        /*
         * Change Feature by Mediatek End.
         */

        final RawContact rawContact = new RawContact();
        if (newAccount != null) {
            rawContact.setAccount(newAccount);
        } else {
            rawContact.setAccountToLocal();
        }

        final ValuesDelta valuesDelta = ValuesDelta.fromAfter(rawContact.getValues());
        final RawContactDelta insert = new RawContactDelta(valuesDelta);
        if (oldState == null) {
            // Parse any values from incoming intent
            RawContactModifier.parseExtras(mContext, newAccountType, insert, mIntentExtras);
            /*
             * Bug Fix by Mediatek Begin.
             *   Original Android's code:
             *     
             *   CR ID: ALPS00230412
             *   Descriptions: add toast when insert sip to sim/usim and finish activity
             */
            if (RawContactModifier.mIsSimType) {
                RawContactModifier.mIsSimType = false;
                Toast.makeText(getActivity(), R.string.add_SIP_to_sim,
                        Toast.LENGTH_LONG).show();
                getActivity().finish();
            } else if (RawContactModifier.mHasSip) {
                RawContactModifier.mHasSip = false;
                Toast.makeText(getActivity(), R.string.already_has_SIP,
                        Toast.LENGTH_LONG).show();
            }
            /*
             * Bug Fix by Mediatek End.
             */
        } else {
            RawContactModifier.migrateStateForNewContact(mContext, oldState, insert,
                    oldAccountType, newAccountType);
        }

        // Ensure we have some default fields (if the account type does not support a field,
        // ensureKind will not add it, so it is safe to add e.g. Event)
        RawContactModifier.ensureKindExists(insert, newAccountType, Phone.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Email.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Organization.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Event.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType,
                StructuredPostal.CONTENT_ITEM_TYPE);

        // Set the correct URI for saving the contact as a profile
        if (mNewLocalProfile) {
            insert.setProfileQueryUri();
        }

        mState.add(insert);

        mRequestFocus = true;

        mNewContactDataReady = true;
        bindEditors();
    }

    private void bindEditors() {
        // bindEditors() can only bind views if there is data in mState, so immediately return
        // if mState is null
        if (mState.isEmpty()) {
            return;
        }

        // Check if delta list is ready.  Delta list is populated from existing data and when
        // editing an read-only contact, it's also populated with newly created data for the
        // blank form.  When the data is not ready, skip. This method will be called multiple times.
        if ((mIsEdit && !mExistingContactDataReady) || (mHasNewContact && !mNewContactDataReady)) {
            return;
        }

        // Sort the editors
        Collections.sort(mState, mComparator);

        // Remove any existing editors and rebuild any visible
        mContent.removeAllViews();

        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        int numRawContacts = mState.size();
        // / The following lines are provided and maintained by Mediatek Inc.
        int simInfoColor = -1;
        // / The previous lines are provided and maintained by Mediatek Inc.
        for (int i = 0; i < numRawContacts; i++) {
            // TODO ensure proper ordering of entities in the list
            final RawContactDelta rawContactDelta = mState.get(i);
            if (!rawContactDelta.isVisible()) continue;

            final AccountType type = rawContactDelta.getAccountType(accountTypes);
            final long rawContactId = rawContactDelta.getRawContactId();
            final String accountType = type.accountType;
            final BaseRawContactEditorView editor;
            if (!type.areContactsWritable()) {
                editor = (BaseRawContactEditorView) inflater.inflate(
                        R.layout.raw_contact_readonly_editor_view, mContent, false);
                ((RawContactReadOnlyEditorView) editor).setListener(this);
            } else if (!TextUtils.isEmpty(accountType)) {
                /*
                 * New Feature by Mediatek Begin. Original Android's code: else{
                 * editor = (RawContactEditorView)
                 * inflater.inflate(R.layout.raw_contact_editor_view,mContent,
                 * false); } CR ID: ALPS00101852 Descriptions: SIM/USIM edit
                 * layout
                 */
                Log.i(TAG, "call R.layout.raw_contact_editor_view  accountType = " + accountType);

                if (mSimInfoWrapper == null) {
                    mSimInfoWrapper = SIMInfoWrapper.getDefault();
                }
                Log.i(TAG, "[bindEditors] mSlotId = " + mSlotId);

                SimInfoRecord simInfoForColor = mSimInfoWrapper.getSimInfoBySlot(mSlotId);
                if (simInfoForColor != null) {
                    simInfoColor = simInfoForColor.mColor;
                }
                Log.i(TAG, "[bindEditors] simInfoColor = " + simInfoColor);

                if (isSimType()) {
                    mIsSaveToSim = true;
                    boolean isCT = ExtensionManager.getInstance().getContactDetailEnhancementExtension()
                    .isUseOperation(ContactPluginDefault.COMMD_FOR_OP09);
                    if (isCT) {
                        editor = (RawContactEditorView) inflater.inflate(
                                R.layout.mtk_raw_sim_contact_editor_view, mContent, false);
                    } else {
                        if (simInfoColor == 0) {
                            editor = (RawContactEditorView) inflater.inflate(
                                    R.layout.mtk_raw_sim_contact_editor_view_blue, mContent, false);
                        } else if (simInfoColor == 1) {
                            editor = (RawContactEditorView) inflater.inflate(
                                    R.layout.mtk_raw_sim_contact_editor_view_orange, mContent, false);
                        } else if (simInfoColor == 2) {
                            editor = (RawContactEditorView) inflater.inflate(
                                    R.layout.mtk_raw_sim_contact_editor_view_green, mContent, false);
                        } else if (simInfoColor == 3) {
                            editor = (RawContactEditorView) inflater.inflate(
                                    R.layout.mtk_raw_sim_contact_editor_view_purple, mContent, false);
                        } else {
                            editor = (RawContactEditorView) inflater.inflate(
                                    R.layout.mtk_raw_sim_contact_editor_view, mContent, false);
                        }
                    }
                } else {
                    Log.i(TAG, "external account");
                    editor = (RawContactEditorView) inflater.inflate(
                            R.layout.raw_contact_editor_view, mContent, false);
                }
            } else {
                editor = (RawContactEditorView) inflater.inflate(R.layout.raw_contact_editor_view,
                        mContent, false);
                Log.i(TAG, " disableAccountSwitcher(editor)####;  editor = " + editor);
            }
            /*
               * Change Feature by Mediatek End.
               */
            /*
             * Change Feature by Mediatek Begin. 
             * Original Android's code: 
             * if(Intent.ACTION_INSERT.equals(mAction) && numRawContacts == 1) {
             * final List<AccountWithDataSet> accounts =
             * AccountTypeManager.getInstance(mContext).getAccounts(true); if
             * (accounts.size() > 1 && !mNewLocalProfile) {
             * addAccountSwitcher(mState.get(0), editor); } else {
             * disableAccountSwitcher(editor); } } else {
             * disableAccountSwitcher(editor);} 
             * CR ID: ALPS00101852
             * Descriptions: Remove feature which can save to other account
             */
            if (null != mState && mState.size() > 3) {
                for (int j = 0; j < 3; j++) {
                    Log.i(TAG, "[bindEditor] mState size = " + mState.size() + " , mState (" + j
                            + ") : " + mState.get(j));
                }
            } else {
                Log.i(TAG, "[bindEditor] mState : " + mState);
            }
            disableAccountSwitcher(editor);
            /*
             * Change Feature by Mediatek End.
             */
            editor.setEnabled(mEnabled);

            mContent.addView(editor);

            /** M:AAS&SNE ensure phone kind updated and exists @ { */
            SimUtils.ensurePhoneKindForEditorExt(type, mSlotId, rawContactDelta);
            SneExt.ensureNicknameKindForEditorExt(type, mSlotId, rawContactDelta);
            /** M: @ } */

            editor.setState(rawContactDelta, type, mViewIdGenerator, isEditingUserProfile());

            /*
             * New Feature by Mediatek Begin. 
             * Original Android's code: 
             * 
            // Set up the photo handler.
            bindPhotoHandler(editor, type, mState);
             * CR ID: ALPS00101852
             * Descriptions: Remove photoeditorlistener
             */
            if (!TextUtils.isEmpty(accountType)) {
                // UIM
                // if (!accountType.equals(mUsimAccountType) &&
                // !accountType.equals(mSimAccountType)) {
                if (!accountType.equals(mUsimAccountType) && !accountType.equals(mSimAccountType)
                        && !accountType.equals(mUimAccountType)) {
                    // UIM
                    // Set up the photo handler.
                    bindPhotoHandler(editor, type, mState);
                }
            } else {
                // Set up the photo handler.
                bindPhotoHandler(editor, type, mState);
            }
            /*
             * Change Feature by Mediatek End.
             */


            // If a new photo was chosen but not yet saved, we need to
            // update the thumbnail to reflect this.
            Bitmap bitmap = updatedBitmapForRawContact(rawContactId);
            if (bitmap != null) editor.setPhotoBitmap(bitmap);

            if (editor instanceof RawContactEditorView) {
                final Activity activity = getActivity();
                final RawContactEditorView rawContactEditor = (RawContactEditorView) editor;
                EditorListener listener = new EditorListener() {

                    @Override
                    public void onRequest(int request) {
                        if (activity.isFinishing()) { // Make sure activity is still running.
                            return;
                        }
                        if (request == EditorListener.FIELD_CHANGED && !isEditingUserProfile()) {
                            acquireAggregationSuggestions(activity, rawContactEditor);
                        }
                    }

                    @Override
                    public void onDeleteRequested(Editor removedEditor) {
                    }
                };

                final StructuredNameEditorView nameEditor = rawContactEditor.getNameEditor();
                if (mRequestFocus) {
                    nameEditor.requestFocus();
                    mRequestFocus = false;
                }
                nameEditor.setEditorListener(listener);
                if (!TextUtils.isEmpty(mDefaultDisplayName)) {
                    nameEditor.setDisplayName(mDefaultDisplayName);
                }

                final TextFieldsEditorView phoneticNameEditor =
                        rawContactEditor.getPhoneticNameEditor();
                phoneticNameEditor.setEditorListener(listener);
                rawContactEditor.setAutoAddToDefaultGroup(mAutoAddToDefaultGroup);

                if (rawContactId == mAggregationSuggestionsRawContactId) {
                    acquireAggregationSuggestions(activity, rawContactEditor);
                }
            }
        }

        mRequestFocus = false;

        bindGroupMetaData();

        // Show editor now that we've loaded state
        mContent.setVisibility(View.VISIBLE);

        // Refresh Action Bar as the visibility of the join command
        // Activity can be null if we have been detached from the Activity
        final Activity activity = getActivity();
        if (activity != null) activity.invalidateOptionsMenu();
    }

    /**
     * If we've stashed a temporary file containing a contact's new photo,
     * decode it and return the bitmap.
     * @param rawContactId identifies the raw-contact whose Bitmap we'll try to return.
     * @return Bitmap of photo for specified raw-contact, or null
    */
    private Bitmap updatedBitmapForRawContact(long rawContactId) {
        String path = mUpdatedPhotos.getString(String.valueOf(rawContactId));
        return BitmapFactory.decodeFile(path);
    }

    private void bindPhotoHandler(BaseRawContactEditorView editor, AccountType type,
            RawContactDeltaList state) {
        final int mode;
        if (type.areContactsWritable()) {
            if (editor.hasSetPhoto()) {
                if (hasMoreThanOnePhoto()) {
                    mode = PhotoActionPopup.Modes.PHOTO_ALLOW_PRIMARY;
                } else {
                    mode = PhotoActionPopup.Modes.PHOTO_DISALLOW_PRIMARY;
                }
            } else {
                mode = PhotoActionPopup.Modes.NO_PHOTO;
            }
        } else {
            if (editor.hasSetPhoto() && hasMoreThanOnePhoto()) {
                mode = PhotoActionPopup.Modes.READ_ONLY_ALLOW_PRIMARY;
            } else {
                // Read-only and either no photo or the only photo ==> no options
                editor.getPhotoEditor().setEditorListener(null);
                return;
            }
        }
        final PhotoHandler photoHandler = new PhotoHandler(mContext, editor, mode, state);
        editor.getPhotoEditor().setEditorListener(
                (PhotoHandler.PhotoEditorListener) photoHandler.getListener());

        // Note a newly created raw contact gets some random negative ID, so any value is valid
        // here. (i.e. don't check against -1 or anything.)
        if (mRawContactIdRequestingPhoto == editor.getRawContactId()) {
            mCurrentPhotoHandler = photoHandler;
        }
    }

    private void bindGroupMetaData() {
        if (mGroupMetaData == null) {
            LogUtils.w(TAG, "[bindGroupMetaData] mGroupMetaData is null! return.");
            return;
        }

        int editorCount = mContent.getChildCount();
        for (int i = 0; i < editorCount; i++) {
            BaseRawContactEditorView editor = (BaseRawContactEditorView) mContent.getChildAt(i);
            editor.setGroupMetaData(mGroupMetaData);
        }
    }

    private void saveDefaultAccountIfNecessary() {
        // Verify that this is a newly created contact, that the contact is composed of only
        // 1 raw contact, and that the contact is not a user profile.
        if (!Intent.ACTION_INSERT.equals(mAction) && mState.size() == 1 &&
                !isEditingUserProfile()) {
            return;
        }

        // Find the associated account for this contact (retrieve it here because there are
        // multiple paths to creating a contact and this ensures we always have the correct
        // account).
        final RawContactDelta rawContactDelta = mState.get(0);
        String name = rawContactDelta.getAccountName();
        String type = rawContactDelta.getAccountType();
        String dataSet = rawContactDelta.getDataSet();

        AccountWithDataSet account = (name == null || type == null) ? null :
                new AccountWithDataSet(name, type, dataSet);
        mEditorUtils.saveDefaultAndAllAccounts(account);
    }

    /* Unused function
    private void addAccountSwitcher(
            final RawContactDelta currentState, BaseRawContactEditorView editor) {
        final AccountWithDataSet currentAccount = new AccountWithDataSet(
                currentState.getAccountName(),
                currentState.getAccountType(),
                currentState.getDataSet());
        final View accountView = editor.findViewById(R.id.account);
        final View anchorView = editor.findViewById(R.id.account_container);
        accountView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(mContext, null);
                final AccountsListAdapter adapter =
                        new AccountsListAdapter(mContext,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, currentAccount);
                popup.setWidth(anchorView.getWidth());
                popup.setAnchorView(anchorView);
                popup.setAdapter(adapter);
                popup.setModal(true);
                popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        UiClosables.closeQuietly(popup);
                        AccountWithDataSet newAccount = adapter.getItem(position);
                        if (!newAccount.equals(currentAccount)) {
                            rebindEditorsForNewContact(currentState, currentAccount, newAccount);
                        }
                    }
                });
                popup.show();
            }
        });
    }
    */

    private void disableAccountSwitcher(BaseRawContactEditorView editor) {
        // Remove the pressed state from the account header because the user cannot switch accounts
        // on an existing contact
        final View accountView = editor.findViewById(R.id.account);
        accountView.setBackground(null);
        accountView.setEnabled(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        /** M: Change Feature @{ */
        if (PhoneCapabilityTester.isUsingTwoPanes(mContext)) {
            inflater.inflate(R.menu.edit_contact, menu);
        } else {
            inflater.inflate(R.menu.mtk_edit_contact_ext, menu);
        }
        /** @} */
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // This supports the keyboard shortcut to save changes to a contact but shouldn't be visible
        // because the custom action bar contains the "save" button now (not the overflow menu).
        // TODO: Find a better way to handle shortcuts, i.e. onKeyDown()?
        final MenuItem doneMenu = menu.findItem(R.id.menu_done);
        final MenuItem splitMenu = menu.findItem(R.id.menu_split);
        final MenuItem joinMenu = menu.findItem(R.id.menu_join);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);
        final MenuItem discardMenu = menu.findItem(R.id.menu_discard);

        // Set visibility of menus
        doneMenu.setVisible(false);

        // Split only if more than one raw profile and not a user profile
        splitMenu.setVisible(mState.size() > 1 && !isEditingUserProfile());

        // Cannot join a user profile
        // The following lines are provided and maintained by Mediatek inc.
        // Cannot join a user profile and sim & usim type
        Log.i(TAG, "[onPrepareOptionsMenu] isSimType() : " + (isSimType()));
        /* Original code joinMenu.setVisible(!isEditingUserProfile()); */
        joinMenu.setVisible(!isEditingUserProfile() && !isSimType());
        // The following lines are provided and maintained by Mediatek inc.

        // Discard menu is only available if at least one raw contact is editable
        discardMenu.setVisible(mState != null &&
                mState.getFirstWritableRawContact(mContext) != null);

        // help menu depending on whether this is inserting or editing
        if (Intent.ACTION_INSERT.equals(mAction)) {
            // inserting
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_add);
        } else if (Intent.ACTION_EDIT.equals(mAction)) {
            // editing
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_edit);
        } else {
            // something else, so don't show the help menu
            helpMenu.setVisible(false);
        }

        int size = menu.size();
        for (int i = 0; i < size; i++) {
            menu.getItem(i).setEnabled(mEnabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return save(SaveMode.CLOSE);
            case R.id.menu_discard:
                return revert();
            case R.id.menu_split:
                return doSplitContactAction();
            case R.id.menu_join:
                return doJoinContactAction();
        }
        /** @} */
        return false;
    }

    private boolean doSplitContactAction() {
        if (!hasValidState()) return false;

        final SplitContactConfirmationDialogFragment dialog =
                new SplitContactConfirmationDialogFragment();
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), SplitContactConfirmationDialogFragment.TAG);
        return true;
    }

    private boolean doJoinContactAction() {
        if (!hasValidState()) {
            return false;
        }

        // If we just started creating a new contact and haven't added any data, it's too
        // early to do a join
        if (mState.size() == 1 && mState.get(0).isContactInsert() && !hasPendingChanges()) {
            Toast.makeText(mContext, R.string.toast_join_with_empty_contact,
                            Toast.LENGTH_LONG).show();
            return true;
        }

        return save(SaveMode.JOIN);
    }

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    private boolean hasValidState() {
        return mState.size() > 0;
    }

    /**
     * Return true if there are any edits to the current contact which need to
     * be saved.
     */
    private boolean hasPendingChanges() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        return RawContactModifier.hasChanges(mState, accountTypes);
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    public boolean save(int saveMode) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            Log.i(TAG, "[save] !hasValidState() : " + (!hasValidState())
                    + " | mStatus != Status.EDITING : " + (mStatus != Status.EDITING)
                    + " , mStatus : " + mStatus);
            return false;
        }

        ///M:fix ALPS01211749,Show Progress Dialog here.So it will save
        // contact,then will call onSaveComplete to dismiss Dialog@{
        if (saveMode == SaveMode.CLOSE) {
            mProgressHandler.showDialog(getFragmentManager());
            LogUtils.i(TAG, "[save]saveMode == CLOSE,show ProgressDialog");
        }
        ///@}

        Log.i(TAG, "[save] saveMode : " + saveMode);
        // If we are about to close the editor - there is no need to refresh the data
        if (saveMode == SaveMode.CLOSE || saveMode == SaveMode.SPLIT) {
            getLoaderManager().destroyLoader(LOADER_DATA);
        }

        mStatus = Status.SAVING;

        if (!hasPendingChanges()) {
            if (mLookupUri == null && saveMode == SaveMode.RELOAD) {
                // We don't have anything to save and there isn't even an existing contact yet.
                // Nothing to do, simply go back to editing mode
                mStatus = Status.EDITING;
                /** M: Change Feature @{ */
                Log.i(TAG, " save reload and mLookupUri is null");
                if (mContent != null) {
                    int count = mContent.getChildCount();
                    for (int i = 0; i < count; i++) {
                        mContent.getChildAt(i).clearFocus();
                    }
                }
                /** @} */
                return true;
            }
            Log.i(TAG, "has changed but saveMode is not reload or mLookupUri is not null");
            onSaveCompleted(false, saveMode, mLookupUri != null, mLookupUri);
            return true;
        }

        setEnabled(false);

        // Store account as default account, only if this is a new contact
        saveDefaultAccountIfNecessary();

        // Save contact
        Intent intent = ContactSaveService.createSaveContactIntent(mContext, mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                ((Activity)mContext).getClass(), ContactEditorActivity.ACTION_SAVE_COMPLETED,
                mUpdatedPhotos);
        mContext.startService(intent);

        // Don't try to save the same photos twice.
        mUpdatedPhotos = new Bundle();

        return true;
    }

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(ContactEditorFragment fragment) {
            CancelEditDialogFragment dialog = new CancelEditDialogFragment();
            dialog.setTargetFragment(fragment, 0);
            dialog.show(fragment.getFragmentManager(), "cancelEditor");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.cancel_confirmation_dialog_message)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int whichButton) {
                                ((ContactEditorFragment)getTargetFragment()).doRevertAction();
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    private boolean revert() {
        if (mState.isEmpty() || !hasPendingChanges()) {
            doRevertAction();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    private void doRevertAction() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) mListener.onReverted();
    }

    public void doSaveAction() {
        ///M:fix ALPS01211749,don't show at here,it may not dismiss the dialog
        // when save(SaveMode.Close) function return @{
        // mProgressHandler.showDialog(getFragmentManager());
        ///@}
        /*
         * New Feature by Mediatek Begin. Original Android's code:
         * save(SaveMode.CLOSE);} CR ID: ALPS00101852 Descriptions: crete
         * sim/usim contact
         */
//        boolean storageState = LowStorageHandle.GetCurrentFlag(); //check storage state
        boolean storageState = false; /* TODO: NEEDTOCHECK */
        Log.i(TAG, " stroageState = " + storageState);
        if (!storageState) {
            if (!mState.isEmpty()) {
                String accountType = mState.get(0).getValues()
                        .getAsString(RawContacts.ACCOUNT_TYPE);
                if (null != mState && mState.size() > 3) {
                    for (int i = 0; i < 3; i++) {
                        Log.i(TAG, "[doSaveAction] mState size = " + mState.size() + " , mState ("
                                + i + ") : " + mState.get(i));
                    }
                } else {
                    Log.i(TAG, "[doSaveAction] mState : " + mState);
                }
                Log.w(TAG, "the accountType is " + accountType);
                if (!TextUtils.isEmpty(accountType)) {
                    // UIM
                    // if (accountType.equals(mSimAccountType) ||
                    // accountType.equals(mUsimAccountType)) {
                    if (accountType.equals(mSimAccountType) || accountType.equals(mUsimAccountType)
                            || accountType.equals(mUimAccountType)) {
                        // UIM

                        saveToSimCard(mState, SaveMode.CLOSE);

                    } else {
                        Log.i(TAG, "[doSaveAction]");
                        save(SaveMode.CLOSE);
                    }
                } else {
                    Log.i(TAG, "[doSaveAction]");
                    save(SaveMode.CLOSE);
                }
            } else {
                Log.i(TAG, "[doSaveAction]");
                save(SaveMode.CLOSE);
            }
        } else {
            /* when storage is full toast it and finish activity */
            if (mSaveModeForSim == 1) {
                Toast.makeText(mContext, R.string.phone_storage_full_create, Toast.LENGTH_SHORT)
                        .show();
                getActivity().finish();
            } else {
                Toast.makeText(mContext, R.string.phone_storage_full_edit, Toast.LENGTH_SHORT)
                        .show();
                getActivity().finish();
            }
        }
    }

    /*
     * Change Feature by Mediatek End.
     */
    public void onJoinCompleted(Uri uri) {
        onSaveCompleted(false, SaveMode.RELOAD, uri != null, uri);
    }

    /** M: Add for SIM Service refactory @{ */
    public void onEditSIMContactCompleted(Intent data) {
        Log.i(TAG, "[onInsertSIMCardCompleted] data is = " + data);
        mProgressHandler.dismissDialog(getFragmentManager());
        if (mStatus == Status.SUB_ACTIVITY) {
            mStatus = Status.EDITING;
        }
        if (data == null) {
            return;
        }
        int result = data.getIntExtra("result", -2);
        if (result == SIMEditProcessor.RESULT_CANCELED) {
            mStatus = Status.EDITING;
            Log.i(TAG, "[onEditSIMContactCompleted] insert result: RESULT_CANCELED ");
            if (data != null) {
                boolean mQuitEdit = data.getBooleanExtra("mQuitEdit", false);
                Log.i(TAG, "[onEditSIMContactCompleted] mQuitEdit : " + mQuitEdit);
                if (mQuitEdit) {
                    getActivity().finish();
                }
                ArrayList<RawContactDelta> simData = null;
                simData = data.getParcelableArrayListExtra("simData1");
                Log.i(TAG, "[onEditSIMContactCompleted] simData : " + simData);
                mState = (RawContactDeltaList) simData;
                mRequestFocus = true;
                mAggregationSuggestionsRawContactId = 0;
                mEnabled = true;
                bindEditors();
                mIsSaveToSim = true;
                Log.i(TAG, "[onEditSIMContactCompleted] bindEditors");
                return;
            }
        } else if (result == SIMEditProcessor.RESULT_OK /*&& result != RESULT_NO_DATA*/) {
            Log.i(TAG, "[onEditSIMContactCompleted] insert result: RESULT_OK");
            Uri lookupUri = data.getData();
            if (lookupUri != null) {
                if (!PhoneCapabilityTester.isUsingTwoPanes(mContext)) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(lookupUri);
                    LogUtils.i(TAG, "[onEditSIMContactCompleted] startViewActivity,lookupUri" + lookupUri + ",mListener:" + mListener);
                    if (mListener != null) {
                        mListener.onSaveFinished(intent);
                    }
                }
            }
        } else if (result != SIMEditProcessor.RESULT_NO_DATA) {
            Log.i(TAG, "[onEditSIMContactCompleted] insert result: " + result);
            return;
        }

        if (PhoneCapabilityTester.isUsingTwoPanes(this.getActivity())) {
            mStatus = Status.CLOSING;
            if (mListener != null) {
                data.setAction(Intent.ACTION_VIEW);
                mListener.onSaveFinished(data);
            }
        } else {
            getActivity().finish();
        }
    }

    /** @} */

    public void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
            Uri contactLookupUri) {
        Log.d(TAG, "onSaveCompleted(" + saveMode + ", contactLookupUri : " + contactLookupUri
                + " | hadChanges : " + hadChanges + " | saveSucceeded : " + saveSucceeded);
        mProgressHandler.dismissDialog(getFragmentManager());
        if (hadChanges) {
            if (saveSucceeded) {
                if (saveMode != SaveMode.JOIN) {
                    /** M: Bug Fix for CR ALPS00333617 @{ */
                    if (null != contactLookupUri) {
                        Toast.makeText(mContext, R.string.contactSavedToast, Toast.LENGTH_SHORT)
                                .show();
                    }
                    /** @} */
                }
            } else {
                Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }
        }
        switch (saveMode) {
            case SaveMode.CLOSE:
            case SaveMode.HOME:
                final Intent resultIntent;
                if (saveSucceeded && contactLookupUri != null) {
                    final String requestAuthority =
                            mLookupUri == null ? null : mLookupUri.getAuthority();

                    final String legacyAuthority = "contacts";

                    resultIntent = new Intent();
                    resultIntent.setAction(Intent.ACTION_VIEW);
                    if (legacyAuthority.equals(requestAuthority)) {
                        // Build legacy Uri when requested by caller
                        final long contactId = ContentUris.parseId(Contacts.lookupContact(
                                mContext.getContentResolver(), contactLookupUri));
                        final Uri legacyContentUri = Uri.parse("content://contacts/people");
                        final Uri legacyUri = ContentUris.withAppendedId(
                                legacyContentUri, contactId);
                        resultIntent.setData(legacyUri);
                    } else {
                        // Otherwise pass back a lookup-style Uri
                        resultIntent.setData(contactLookupUri);
                    }

                } else {
                    resultIntent = null;
                }
                // It is already saved, so prevent that it is saved again
                mStatus = Status.CLOSING;
                if (mListener != null) mListener.onSaveFinished(resultIntent);
                break;

            case SaveMode.RELOAD:
            case SaveMode.JOIN:
                if (saveSucceeded && contactLookupUri != null) {
                    // If it was a JOIN, we are now ready to bring up the join activity.
                    if (saveMode == SaveMode.JOIN && hasValidState()) {
                        showJoinAggregateActivity(contactLookupUri);
                    }

                    // If this was in INSERT, we are changing into an EDIT now.
                    // If it already was an EDIT, we are changing to the new Uri now
                    mState = new RawContactDeltaList();
                    load(Intent.ACTION_EDIT, contactLookupUri, null);
                    mStatus = Status.LOADING;
                    getLoaderManager().restartLoader(LOADER_DATA, null, mDataLoaderListener);
                }
                /*
                 * Change Feature by Mediatek Begin.
                 *   Original Android¡¯s code:
                 *     
                 *   CR ID: ALPS00113564
                 *   Descriptions: ¡­
                 */
                else if (contactLookupUri == null && !hadChanges && !saveSucceeded) {
                    Log.i(TAG, "[onSaveCompleted] saveMode is Reload and finish activity noew");
                    mStatus = Status.EDITING;
                    // getActivity().finish();
                }
                /** M: Bug Fix for CR ALPS00318983 @{ */
                else if (!PhoneCapabilityTester.isUsingTwoPanes(mContext)
                        && contactLookupUri == null && hadChanges && saveSucceeded) {
                    mNeedFinish = true;
                    Log.i(TAG, "[onSavecompleted] the contact is deleted");
                    Intent intent = new Intent(mContext, PeopleActivity.class);
                    startActivity(intent);
                    getActivity().finish();
                }
                /** @} */
                /*
                 * Change Feature by Mediatek End.
                 */
                break;

            case SaveMode.SPLIT:
                mStatus = Status.CLOSING;
                if (mListener != null) {
                    mListener.onContactSplit(contactLookupUri);
                    
                    
                    /*
                     * Bug Fix by Mediatek Begin.
                     *   Original Android's code:
                     *     xxx
                     *   CR ID: ALPS00273970
                     *   Descriptions: 
                     */
                    ContactDetailActivity.finishMyself(true);
                    /*
                     * Bug Fix by Mediatek End.
                     */
                    
                    
                } else {
                    Log.d(TAG, "No listener registered, can not call onSplitFinished");
                }
                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
    private void showJoinAggregateActivity(Uri contactLookupUri) {
        if (contactLookupUri == null || !isAdded()) {
            return;
        }

        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
        mContactWritableForJoin = isContactWritable();
        final Intent intent = new Intent(JoinContactActivity.JOIN_CONTACT);
        intent.putExtra(JoinContactActivity.EXTRA_TARGET_CONTACT_ID, mContactIdForJoin);
        startActivityForResult(intent, REQUEST_CODE_JOIN);
    }

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
    private void joinAggregate(final long contactId) {
        // / M: fix ALPS01019191, need to show dialog during joining contacts.
        mProgressHandler.showDialog(getFragmentManager());

        Intent intent = ContactSaveService.createJoinContactsIntent(mContext, mContactIdForJoin,
                contactId, mContactWritableForJoin,
                ContactEditorActivity.class, ContactEditorActivity.ACTION_JOIN_COMPLETED);
        mContext.startService(intent);
    }

    /**
     * Returns true if there is at least one writable raw contact in the current contact.
     */
    private boolean isContactWritable() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *     xxx
         *   CR ID: ALPS00299025
         *   Descriptions: 
         */
        if (!mState.isEmpty()) {
            int size = mState.size();
            for (int i = 0; i < size; i++) {
                RawContactDelta entity = mState.get(i);
                final AccountType type = entity.getAccountType(accountTypes);
                if (type.areContactsWritable()) {
                    return true;
                }
            }
        }
        /*
         * Bug Fix by Mediatek End.
         */
        return false;
    }

    private boolean isEditingUserProfile() {
        return mNewLocalProfile || mIsUserProfile;
    }

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete (unless it was a new contact)
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         * The editor tries best to chose the most natural contact here.
         */
        void onContactSplit(Uri newLookupUri);

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(Intent resultIntent);

        /**
         * User switched to editing a different contact (a suggestion from the
         * aggregation engine).
         */
        void onEditOtherContactRequested(
                Uri contactLookupUri, ArrayList<ContentValues> contentValues);

        /**
         * Contact is being created for an external account that provides its own
         * new contact activity.
         */
        void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                Bundle intentExtras);

        /**
         * The edited raw contact belongs to an external account that provides
         * its own edit activity.
         *
         * @param redirect indicates that the current editor should be closed
         *            before the custom editor is shown.
         */
        void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri,
                Bundle intentExtras, boolean redirect);
    }

    private class EntityDeltaComparator implements Comparator<RawContactDelta> {
        /**
         * Compare EntityDeltas for sorting the stack of editors.
         */
        @Override
        public int compare(RawContactDelta one, RawContactDelta two) {
            // Check direct equality
            if (one.equals(two)) {
                return 0;
            }

            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
            String accountType1 = one.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet1 = one.getValues().getAsString(RawContacts.DATA_SET);
            final AccountType type1 = accountTypes.getAccountType(accountType1, dataSet1);
            String accountType2 = two.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            String dataSet2 = two.getValues().getAsString(RawContacts.DATA_SET);
            final AccountType type2 = accountTypes.getAccountType(accountType2, dataSet2);
            
            // The following lines are provided and maintained by Mediatek Inc.
            Log.i(TAG, "[compare] type1 : " + type1);
            Log.i(TAG, "[compare] type2 : " + type2);

            if (type2 == null && type1 == null) {
                Log.i(TAG, "type2 and type1 also null");
                return 1;
            } else if (type2 == null || type1 == null) {
                Log.i(TAG, "type2 or type1 is null");
                return -1;
            }
            // The previous lines are provided and maintained by Mediatek Inc.
            
            // Check read-only
            if (!type1.areContactsWritable() && type2.areContactsWritable()) {
                return 1;
            } else if (type1.areContactsWritable() && !type2.areContactsWritable()) {
                return -1;
            }

            // Check account type
            boolean skipAccountTypeCheck = false;
            boolean isGoogleAccount1 = type1 instanceof GoogleAccountType;
            boolean isGoogleAccount2 = type2 instanceof GoogleAccountType;
            if (isGoogleAccount1 && !isGoogleAccount2) {
                return -1;
            } else if (!isGoogleAccount1 && isGoogleAccount2) {
                return 1;
            } else if (isGoogleAccount1 && isGoogleAccount2){
                skipAccountTypeCheck = true;
            }

            int value;
            if (!skipAccountTypeCheck) {
                
                /*
                 * Bug Fix by Mediatek Begin.
                 *   Original Android's code:
                 *     if (type1.accountType == null ) {
                    Log.i(TAG,"type.accountType is null");
                    return 1;
                }
                 *   CR ID: ALPS00243673
                 *   Descriptions: consider type2's accountType is null
                 */
                if (type1.accountType == null || type2.accountType == null) {
                    Log.i(TAG, "type.accountType is null");
                    return 1;
                }
                /*
                 * Bug Fix by Mediatek End.
                 */
                
                Log.i(TAG, "ype1.accountType : " + type1.accountType + " | type2.accountType : "
                        + type2.accountType);
                
                value = type1.accountType.compareTo(type2.accountType);
                if (value != 0) {
                    return value;
                } else {
                    // Fall back to data set.
                    if (type1.dataSet != null) {
                        value = type1.dataSet.compareTo(type2.dataSet);
                        if (value != 0) {
                            return value;
                        }
                    } else if (type2.dataSet != null) {
                        return 1;
                    }
                }
            }

            // Check account name
            String oneAccount = one.getAccountName();
            if (oneAccount == null) oneAccount = "";
            String twoAccount = two.getAccountName();
            if (twoAccount == null) twoAccount = "";
            value = oneAccount.compareTo(twoAccount);
            if (value != 0) {
                return value;
            }

            // Both are in the same account, fall back to contact ID
            Long oneId = one.getRawContactId();
            Long twoId = two.getRawContactId();
            if (oneId == null) {
                return -1;
            } else if (twoId == null) {
                return 1;
            }

            return (int)(oneId - twoId);
        }
    }

    /**
     * Returns the contact ID for the currently edited contact or 0 if the contact is new.
     */
    protected long getContactId() {
        for (RawContactDelta rawContact : mState) {
            Long contactId = rawContact.getValues().getAsLong(RawContacts.CONTACT_ID);
            if (contactId != null) {
                return contactId;
            }
        }
        return 0;
    }

    /**
     * Triggers an asynchronous search for aggregation suggestions.
     */
    private void acquireAggregationSuggestions(Context context,
            RawContactEditorView rawContactEditor) {
        /**
         * M: Bug Fix for CR: ALPS00449485 <br>
         *    Description: To disable the aggregation function
         *      for SIM editor. @{
         */
        if (isSimType()) {
            return;
        }
        /** @} */
        long rawContactId = rawContactEditor.getRawContactId();
        if (mAggregationSuggestionsRawContactId != rawContactId
                && mAggregationSuggestionView != null) {
            mAggregationSuggestionView.setVisibility(View.GONE);
            mAggregationSuggestionView = null;
            mAggregationSuggestionEngine.reset();
        }

        mAggregationSuggestionsRawContactId = rawContactId;

        if (mAggregationSuggestionEngine == null) {
            mAggregationSuggestionEngine = new AggregationSuggestionEngine(context);
            mAggregationSuggestionEngine.setListener(this);
            mAggregationSuggestionEngine.start();
        }

        mAggregationSuggestionEngine.setContactId(getContactId());

        LabeledEditorView nameEditor = rawContactEditor.getNameEditor();
        mAggregationSuggestionEngine.onNameChange(nameEditor.getValues());
    }

    @Override
    public void onAggregationSuggestionChange() {
        if (!isAdded() || mState.isEmpty() || mStatus != Status.EDITING) {
            return;
        }
        /* 
         * New Feature by Mediatek Begin.
         * Original Android's code:
         * 
         * 
         * Descriptions: Remove join function when edit name in SIM/USIM mode
         */
        if (isSimType()) {
            return;
        }
        /*
         * Change Feature by Mediatek End.
         */
        UiClosables.closeQuietly(mAggregationSuggestionPopup);

        if (mAggregationSuggestionEngine.getSuggestedContactCount() == 0) {
            return;
        }

        final RawContactEditorView rawContactView =
                (RawContactEditorView)getRawContactEditorView(mAggregationSuggestionsRawContactId);
        if (rawContactView == null) {
            return; // Raw contact deleted?
        }
        final View anchorView = rawContactView.findViewById(R.id.anchor_view);
        mAggregationSuggestionPopup = new ListPopupWindow(mContext, null);
        mAggregationSuggestionPopup.setAnchorView(anchorView);
        mAggregationSuggestionPopup.setWidth(anchorView.getWidth());
        mAggregationSuggestionPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        mAggregationSuggestionPopup.setAdapter(
                new AggregationSuggestionAdapter(getActivity(),
                        mState.size() == 1 && mState.get(0).isContactInsert(),
                        this, mAggregationSuggestionEngine.getSuggestions()));
        mAggregationSuggestionPopup.setOnItemClickListener(mAggregationSuggestionItemClickListener);
        mAggregationSuggestionPopup.show();
    }

    @Override
    public void onJoinAction(long contactId, List<Long> rawContactIdList) {
        long rawContactIds[] = new long[rawContactIdList.size()];
        for (int i = 0; i < rawContactIds.length; i++) {
            rawContactIds[i] = rawContactIdList.get(i);
        }
        JoinSuggestedContactDialogFragment dialog =
                new JoinSuggestedContactDialogFragment();
        Bundle args = new Bundle();
        args.putLongArray("rawContactIds", rawContactIds);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, 0);
        try {
            dialog.show(getFragmentManager(), "join");
        } catch (Exception ex) {
            // No problem - the activity is no longer available to display the dialog
        }
    }

    public static class JoinSuggestedContactDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.aggregation_suggestion_join_dialog_message)
                    .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ContactEditorFragment targetFragment =
                                        (ContactEditorFragment) getTargetFragment();
                                long rawContactIds[] =
                                        getArguments().getLongArray("rawContactIds");
                                targetFragment.doJoinSuggestedContact(rawContactIds);
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.no, null)
                    .create();
        }
    }

    /**
     * Joins the suggested contact (specified by the id's of constituent raw
     * contacts), save all changes, and stay in the editor.
     */
    protected void doJoinSuggestedContact(long[] rawContactIds) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return;
        }

        mState.setJoinWithRawContacts(rawContactIds);
        save(SaveMode.RELOAD);
    }

    @Override
    public void onEditAction(Uri contactLookupUri) {
        SuggestionEditConfirmationDialogFragment dialog =
                new SuggestionEditConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("contactUri", contactLookupUri);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "edit");
    }

    public static class SuggestionEditConfirmationDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.aggregation_suggestion_edit_dialog_message)
                    .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ContactEditorFragment targetFragment =
                                        (ContactEditorFragment) getTargetFragment();
                                Uri contactUri =
                                        getArguments().getParcelable("contactUri");
                                targetFragment.doEditSuggestedContact(contactUri);
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.no, null)
                    .create();
        }
    }

    /**
     * Abandons the currently edited contact and switches to editing the suggested
     * one, transferring all the data there
     */
    protected void doEditSuggestedContact(Uri contactUri) {
        if (mListener != null) {
            // make sure we don't save this contact when closing down
            mStatus = Status.CLOSING;
            mListener.onEditOtherContactRequested(
                    contactUri, mState.get(0).getContentValues());
        }
    }

    public void setAggregationSuggestionViewEnabled(boolean enabled) {
        if (mAggregationSuggestionView == null) {
            return;
        }

        LinearLayout itemList = (LinearLayout) mAggregationSuggestionView.findViewById(
                R.id.aggregation_suggestions);
        int count = itemList.getChildCount();
        for (int i = 0; i < count; i++) {
            itemList.getChildAt(i).setEnabled(enabled);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_URI, mLookupUri);
        outState.putString(KEY_ACTION, mAction);

        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }
        outState.putLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO, mRawContactIdRequestingPhoto);
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);
        outState.putParcelable(KEY_CURRENT_PHOTO_URI, mCurrentPhotoUri);
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);
        outState.putBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN, mContactWritableForJoin);
        outState.putLong(KEY_SHOW_JOIN_SUGGESTIONS, mAggregationSuggestionsRawContactId);
        outState.putBoolean(KEY_ENABLED, mEnabled);
        outState.putBoolean(KEY_NEW_LOCAL_PROFILE, mNewLocalProfile);
        outState.putBoolean(KEY_IS_USER_PROFILE, mIsUserProfile);
        outState.putInt(KEY_STATUS, mStatus);
        outState.putParcelable(KEY_UPDATED_PHOTOS, mUpdatedPhotos);
        outState.putBoolean(KEY_HAS_NEW_CONTACT, mHasNewContact);
        outState.putBoolean(KEY_IS_EDIT, mIsEdit);
        outState.putBoolean(KEY_NEW_CONTACT_READY, mNewContactDataReady);
        outState.putBoolean(KEY_EXISTING_CONTACT_READY, mExistingContactDataReady);
        outState.putParcelableArrayList(KEY_RAW_CONTACTS,
                mRawContacts == null ?
                Lists.<RawContact> newArrayList() :  Lists.newArrayList(mRawContacts));
        
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *     xxx
         *   CR ID: ALPS00267947
         *   Descriptions: add simid , slotid , savemodeforsim
         */
        outState.putInt(KEY_SLOTID, mSlotId);
        outState.putLong(KEY_SIMID, mSimId);
        outState.putInt(KEY_SAVEMODE_FOR_SIM, mSaveModeForSim);
        outState.putInt(KEY_INDICATE_PHONE_OR_SIM, mIndicatePhoneOrSimContact);
        outState.putInt(KEY_SIM_INDEX, mSimIndex);
        if (mOldState != null && mOldState.size() > 0) {
            outState.putParcelable(KEY_OLDSTATE, mOldState);
        }
        Log.i(TAG, "[onSaveInstanceState mSlotId : " + mSlotId + " | mSimId : " + mSimId
                + " | mSaveModeForSim : " + mSaveModeForSim);
        Log.i(TAG, "[onSaveInstanceState mOldState : " + mOldState);
        /*
         * Bug Fix by Mediatek End.
         */
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mStatus == Status.SUB_ACTIVITY) {
            mStatus = Status.EDITING;
        }

        // See if the photo selection handler handles this result.
        if (mCurrentPhotoHandler != null && mCurrentPhotoHandler.handlePhotoActivityResult(
                requestCode, resultCode, data)) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_JOIN: {
                // Ignore failed requests
                if (resultCode != Activity.RESULT_OK) return;
                if (data != null) {
                    final long contactId = ContentUris.parseId(data.getData());
                    joinAggregate(contactId);
                }
                break;
            }
            case REQUEST_CODE_ACCOUNTS_CHANGED: {
                // Bail if the account selector was not successful.
                if (resultCode != Activity.RESULT_OK) {
                    mListener.onReverted();
                    return;
                }
                // If there's an account specified, use it.
                if (data != null) {
                    /**
                     * M:ALPS00403629 to show the soft inputmethod after
                     * ContactEditorAccountsChangedActivity @{
                     */
                    mIsShowIME = true;
                    setInputMethodVisible(true);
                    /** @}*/
                    /*
                     * New Feature by Mediatek Begin. Original Android's code:
                     * CR ID: ALPS00101852 Descriptions: crete sim/usim contact
                     */
                    mSlotId = data.getIntExtra("mSlotId", -1);
                    /** M:AAS @ { */
                    ExtensionManager.getInstance().getContactAccountExtension().setCurrentSlot(
                            mSlotId, ExtensionManager.COMMD_FOR_AAS);
                    /** M: @ } */
                    mSimId = data.getLongExtra("mSimId", -1);
                    mNewSimType = data.getBooleanExtra("mIsSimType", false);
                    Log.i(TAG, "mslotid,msimid = " + mSlotId + "    " + mSimId);
                    /*
                     * Change Feature by Mediatek End.
                     */
                    AccountWithDataSet account = data.getParcelableExtra(Intents.Insert.ACCOUNT);
                    if (account != null) {
                        createContact(account);
                        return;
                    }
                }
                // If there isn't an account specified, then this is likely a phone-local
                // contact, so we should continue setting up the editor by automatically selecting
                // the most appropriate account.
                createContact();
                break;
            }
        }
    }

    /**
     * Sets the photo stored in mPhoto and writes it to the RawContact with the given id
     */
    private void setPhoto(long rawContact, Bitmap photo, Uri photoUri) {
        BaseRawContactEditorView requestingEditor = getRawContactEditorView(rawContact);

        if (photo == null || photo.getHeight() < 0 || photo.getWidth() < 0) {
            // This is unexpected.
            Log.w(TAG, "Invalid bitmap passed to setPhoto()");
        }

        if (requestingEditor != null) {
            requestingEditor.setPhotoBitmap(photo);
        } else {
            Log.w(TAG, "The contact that requested the photo is no longer present.");
        }

        mUpdatedPhotos.putParcelable(String.valueOf(rawContact), photoUri);
    }

    /**
     * Finds raw contact editor view for the given rawContactId.
     */
    public BaseRawContactEditorView getRawContactEditorView(long rawContactId) {
        for (int i = 0; i < mContent.getChildCount(); i++) {
            final View childView = mContent.getChildAt(i);
            if (childView instanceof BaseRawContactEditorView) {
                final BaseRawContactEditorView editor = (BaseRawContactEditorView) childView;
                if (editor.getRawContactId() == rawContactId) {
                    return editor;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if there is currently more than one photo on screen.
     */
    private boolean hasMoreThanOnePhoto() {
        int countWithPicture = 0;
        final int numEntities = mState.size();
        for (int i = 0; i < numEntities; i++) {
            final RawContactDelta entity = mState.get(i);
            if (entity.isVisible()) {
                final ValuesDelta primary = entity.getPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
                if (primary != null && primary.getPhoto() != null) {
                    countWithPicture++;
                } else {
                    final long rawContactId = entity.getRawContactId();
                    final Uri uri = mUpdatedPhotos.getParcelable(String.valueOf(rawContactId));
                    if (uri != null) {
                        try {
                            mContext.getContentResolver().openInputStream(uri);
                            countWithPicture++;
                        } catch (FileNotFoundException e) {
                        }
                    }
                }

                if (countWithPicture > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The listener for the data loader
     */
    private final LoaderManager.LoaderCallbacks<Contact> mDataLoaderListener =
            new LoaderCallbacks<Contact>() {
        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            mLoaderStartTime = SystemClock.elapsedRealtime();
            return new ContactLoader(mContext, mLookupUri, true);
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            ///M:check whether the fragment still in Activity@{
            if (!isAdded()) {
                Log.w(TAG, "onLoadFinished(),This Fragment is not add to the Activity now.");
                return;
            }
            ///@}

            final long loaderCurrentTime = SystemClock.elapsedRealtime();
            Log.v(TAG, "Time needed for loading: " + (loaderCurrentTime - mLoaderStartTime));
            Log.i(TAG, "data.isLoaded : " + data.isLoaded() + " |data.isNotFound() : "
                    + data.isNotFound() + " |data.getContactId() : " + data.getContactId()
                    + " | data.getUri() : " + data.getUri() + " | mNeedFinish : " + mNeedFinish);
            if (!data.isLoaded()) {
                // Item has been deleted
                Log.i(TAG, "No contact found. Closing activity");
                if (mListener != null) mListener.onContactNotFound();
                return;
            }
            /** M: New Feature xxx @{ */
            if (mNeedFinish) {
                LogUtils.i(TAG, "#onLoadFinished(),mNeedFinish is true,Cancle execute.");
                mNeedFinish = false;
                return;
            }
            /** @} */
            mStatus = Status.EDITING;
            mLookupUri = data.getLookupUri();

            /** M: Bug Fix for ALPS00338269 @{ */
            mIndicatePhoneOrSimContact = data.getIndicate();
            mSimIndex = data.getSimIndex();
            /** @} */

            final long setDataStartTime = SystemClock.elapsedRealtime();
            setData(data);
            final long setDataEndTime = SystemClock.elapsedRealtime();

            Log.v(TAG, "Time needed for setting UI: " + (setDataEndTime - setDataStartTime));
        }

        @Override
        public void onLoaderReset(Loader<Contact> loader) {
        }
    };

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, Groups.CONTENT_URI);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mGroupMetaData = data;
            bindGroupMetaData();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    @Override
    public void onSplitContactConfirmed() {
        if (mState.isEmpty()) {
            // This may happen when this Fragment is recreated by the system during users
            // confirming the split action (and thus this method is called just before onCreate()),
            // for example.
            Log.e(TAG, "mState became null during the user's confirming split action. " +
                    "Cannot perform the save action.");
            return;
        }

        mState.markRawContactsForSplitting();
        save(SaveMode.SPLIT);
    }

    /**
     * Custom photo handler for the editor.  The inner listener that this creates also has a
     * reference to the editor and acts as an {@link EditorListener}, and uses that editor to hold
     * state information in several of the listener methods.
     */
    private final class PhotoHandler extends PhotoSelectionHandler {

        final long mRawContactId;
        private final BaseRawContactEditorView mEditor;
        private final PhotoActionListener mPhotoEditorListener;

        public PhotoHandler(Context context, BaseRawContactEditorView editor, int photoMode,
                RawContactDeltaList state) {
            super(context, editor.getPhotoEditor(), photoMode, false, state);
            mEditor = editor;
            mRawContactId = editor.getRawContactId();
            mPhotoEditorListener = new PhotoEditorListener();
        }

        @Override
        public PhotoActionListener getListener() {
            return mPhotoEditorListener;
        }

        @Override
        public void startPhotoActivity(Intent intent, int requestCode, Uri photoUri) {
            mRawContactIdRequestingPhoto = mEditor.getRawContactId();
            mCurrentPhotoHandler = this;
            mStatus = Status.SUB_ACTIVITY;
            mCurrentPhotoUri = photoUri;
            ContactEditorFragment.this.startActivityForResult(intent, requestCode);
        }

        private final class PhotoEditorListener extends PhotoSelectionHandler.PhotoActionListener
                implements EditorListener {

            @Override
            public void onRequest(int request) {
                if (!hasValidState()) return;

                if (request == EditorListener.REQUEST_PICK_PHOTO) {
                    onClick(mEditor.getPhotoEditor());
                }
            }

            @Override
            public void onDeleteRequested(Editor removedEditor) {
                // The picture cannot be deleted, it can only be removed, which is handled by
                // onRemovePictureChosen()
            }

            /**
             * User has chosen to set the selected photo as the (super) primary photo
             */
            @Override
            public void onUseAsPrimaryChosen() {
                // Set the IsSuperPrimary for each editor
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    final View childView = mContent.getChildAt(i);
                    if (childView instanceof BaseRawContactEditorView) {
                        final BaseRawContactEditorView editor =
                                (BaseRawContactEditorView) childView;
                        final PhotoEditorView photoEditor = editor.getPhotoEditor();
                        photoEditor.setSuperPrimary(editor == mEditor);
                    }
                }
                bindEditors();
            }

            /**
             * User has chosen to remove a picture
             */
            @Override
            public void onRemovePictureChosen() {
                mEditor.setPhotoBitmap(null);

                // Prevent bitmap from being restored if rotate the device.
                // (only if we first chose a new photo before removing it)
                mUpdatedPhotos.remove(String.valueOf(mRawContactId));
                bindEditors();
            }

            @Override
            public void onPhotoSelected(Uri uri) throws FileNotFoundException {
                final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(mContext, uri);
                setPhoto(mRawContactId, bitmap, uri);
                mCurrentPhotoHandler = null;
                /** M: Bug Fix for CR ALPS00334415 @{ */
                if (!mState.isEmpty()) {
                    bindEditors();
                } else {
                    Log.e(TAG, "mState is null");
                }
                /** @} */
            }

            @Override
            public Uri getCurrentPhotoUri() {
                return mCurrentPhotoUri;
            }

            @Override
            public void onPhotoSelectionDismissed() {
                // Nothing to do.
            }
        }
    }

    /*
     * New Feature by Mediatek Begin. Original Android's code: CR ID:
     * ALPS00101852 Descriptions: crete sim/usim contact
     */
    public boolean saveToSimCard(RawContactDeltaList mState, int saveMode) {
        /** M: Add for SIM Service refactory @{ */
        Intent intent = new Intent(mContext, SIMProcessorService.class);
        /** @} */
        intent.putParcelableArrayListExtra("simData", mState);
        intent.putParcelableArrayListExtra("simOldData", mOldState);
        if (null != mOldState && mOldState.size() > 3) {
            for (int i = 0; i < 3; i++) {
                Log.i(TAG, "[saveToSimCard] mOldState size = " + mOldState.size()
                        + " , mOldState (" + i + ") : " + mOldState.get(i));
            }
        } else {
            Log.i(TAG, "[saveToSimCard] mOldState : " + mOldState);
        }
        // slotid

        if (!hasValidState() || mStatus != Status.EDITING) {
            return false;
        }
        
        ///M:fix ALPS01211749,Show Progress Dialog here.So it will save
        // contact,then will call onSaveComplete to dismiss Dialog@{
        if (saveMode == SaveMode.CLOSE) {
            mProgressHandler.showDialog(getFragmentManager());
            LogUtils.i(TAG, "[saveToSimCard]saveMode == CLOSE,show ProgressDialog");
        }
        ///@}

        /// M: fixed cr ALPS00929895.
        if (!hasPendingChanges()) {
            Log.i(TAG, "[saveToSimCard] hasPendingChanges is false");
            onSaveCompleted(false, saveMode, mLookupUri != null, mLookupUri);
            return false;
        }

        mStatus = Status.SAVING;

        setEnabled(false);
        if (mSaveModeForSim == 1) {
            intent.putExtra("slotId", mSlotId);
            intent.putExtra(RawContacts.INDICATE_PHONE_SIM, mSimId);
        } else if (mSaveModeForSim == 2) {
            ///M:fix CR ALPS01065879,sim Info Manager API Remove
            SimInfoRecord simInfo = SimInfoManager.getSimInfoById(mContext, (long) mIndicatePhoneOrSimContact);
            if (simInfo == null) {
                mSlotId = -1;
            } else {
                mSlotId = simInfo.mSimSlotId;
            }
            intent.putExtra("slotId", mSlotId);
            intent.putExtra(RawContacts.INDICATE_PHONE_SIM, (long) mIndicatePhoneOrSimContact);
            intent.putExtra("simIndex", mSimIndex);
            Log.i(TAG, "[saveToSimCard] mIndicatePhoneOrSimContact , mSlotId , mSimIndex: "
                    + mIndicatePhoneOrSimContact + " , " + mSlotId + " , " + mSimIndex);
        }
        Log.i(TAG, "[saveToSimCard] mSaveModeForSim : " + mSaveModeForSim);

        intent.putExtra("simSaveMode", mSaveModeForSim);
        intent.setData(mLookupUri);

        if (null != mState && mState.size() > 3) {
            for (int i = 0; i < 3; i++) {
                Log.i(TAG, "[saveToSimCard] mState size = " + mState.size() + " , mState ("
                        + i + ") : " + mState.get(i));
            }
        } else {
            Log.i(TAG, "[saveToSimCard] mState : " + mState);
        }

        Log.i(TAG, "THE mLookupUri is = " + mLookupUri);
        int i = 0;
        if (mGroupMetaData != null) {
            int groupNum = mGroupMetaData.getPosition();
            String accountType = mState.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            if (accountType.equals(mUsimAccountType) && groupNum > 0) {
                String groupName[] = new String[groupNum];
                long groupId[] = new long[groupNum];
                mGroupMetaData.moveToPosition(-1);
                while (mGroupMetaData.moveToNext()) {
                    Log.i(TAG, "THE ACCOUNT_NAME is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_NAME));
                    Log.i(TAG, "THE DATA_SET is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.DATA_SET));
                    Log.i(TAG, "THE GROUP_ID is = "
                            + mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID));
                    Log.i(TAG, "THE TITLE is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.TITLE));
                    groupName[i] = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                    groupId[i] = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
                    i++;
                    Log.i(TAG, "[saveToSimCard] I : " + i);
                }
                intent.putExtra("groupName", groupName);
                intent.putExtra("groupNum", groupNum);
                intent.putExtra("groupId", groupId);
                Log.i(TAG, "[saveToSimCard] groupNum : " + groupNum);

            }
        }

       /** M: Add for SIM Service refactory @{ */
        intent.putExtra(SIMServiceUtils.SERVICE_SLOT_KEY, mSlotId);
        intent.putExtra(SIMServiceUtils.SERVICE_WORK_TYPE, SIMServiceUtils.SERVICE_WORK_EDIT);
        mContext.startService(intent);
       /** @} */
        mIsSaveToSim = true;

        return true;
    }

    private boolean isSimType() {
        boolean checkAccount = false;
        if (!mState.isEmpty()) {
            String accountType = mState.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            // UIM
            // if (accountType != null
            // && (accountType.equals(mSimAccountType) ||
            // accountType.equals(mUsimAccountType))) {
            if (accountType != null
                    && (accountType.equals(mSimAccountType) || accountType.equals(mUsimAccountType) || accountType
                            .equals(mUimAccountType))) {
                // UIM
                checkAccount = true;
            }
        }
        Log.i(TAG, "[isSimType] mIsSimType : " + mIsSimType + " | mNewSimType : " + mNewSimType
                + " | checkAccount : " + checkAccount);
        return mIsSimType || mNewSimType || checkAccount;
    }
    private final String KEY_SLOTID = "key_slotid";
    private final String KEY_SIMID = "key_simid";
    private final String KEY_SAVEMODE_FOR_SIM = "key_savemode_for_sim";
    private final String KEY_OLDSTATE = "key_oldstate";
    private final String KEY_INDICATE_PHONE_OR_SIM = "key_indicate_phone_or_sim";
    private final String KEY_SIM_INDEX = "key_sim_index";
    private String mSimAccountType =  AccountType.ACCOUNT_TYPE_SIM;
    private String mUsimAccountType =  AccountType.ACCOUNT_TYPE_USIM;
    //UIM
    private String mUimAccountType =  AccountType.ACCOUNT_TYPE_UIM;
    //UIM
    private String mLocalPhoneAccountType =  AccountType.ACCOUNT_TYPE_LOCAL_PHONE;
    private int mIndicatePhoneOrSimContact;

    private static final int REQUEST_CODE_SAVE_TO_SIM = 4;
    private boolean mIsSaveToSim = false;
    private int mSaveModeForSim;
    private int mSlotId = -1;
    private long mSimId = -1;
    private RawContactDeltaList mOldState;
    private boolean mIsSimType = false;
    private boolean mNewSimType = false;
    public static final String KEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    private SIMInfoWrapper mSimInfoWrapper;
    private boolean mNeedFinish = false;
    private int mSimIndex = -1;
    
    /** M:ALPS00403629 add to show inputmethod at necessary @} */
    private boolean mIsShowIME = true;

    public void setInputMethodVisible(boolean visible) {
        int mode = visible ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        mode = mode | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        getActivity().getWindow().setSoftInputMode(mode);
    }
    /** @}*/

    /** M: Bug Fix for ALPS00416628 @{ */
    @Override
    public void onPause() {
        if (null != mAggregationSuggestionPopup) {
            mAggregationSuggestionPopup.dismiss();
            mAggregationSuggestionPopup = null;
        }
        super.onPause();
    }
    /** @} */

    public void doDiscard() {
        revert();
    }

    ProgressHandler mProgressHandler = new ProgressHandler();
    private static final int SHOW_PROGRESS_DELAY = 500;

}
