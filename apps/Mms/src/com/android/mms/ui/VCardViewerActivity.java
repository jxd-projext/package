/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.mms.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.R;
import com.android.mms.model.FileAttachmentModel;
import com.android.mms.model.VCardModel;
import com.android.mms.util.VCardUtils;
import com.android.mms.util.VCardUtils.MyToStringIterator;
import com.android.vcard.exception.VCardException;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntry.AndroidCustomData;
import com.android.vcard.VCardEntry.AnniversaryData;
import com.android.vcard.VCardEntry.BirthdayData;
import com.android.vcard.VCardEntry.EmailData;
import com.android.vcard.VCardEntry.EntryElement;
import com.android.vcard.VCardEntry.EntryElementIterator;
import com.android.vcard.VCardEntry.EntryLabel;
import com.android.vcard.VCardEntry.ImData;
import com.android.vcard.VCardEntry.NameData;
import com.android.vcard.VCardEntry.NicknameData;
import com.android.vcard.VCardEntry.NoteData;
import com.android.vcard.VCardEntry.OrganizationData;
import com.android.vcard.VCardEntry.PhoneData;
import com.android.vcard.VCardEntry.PostalData;
import com.android.vcard.VCardEntry.SipData;
import com.android.vcard.VCardEntry.WebsiteData;
import com.android.vcard.VCardEntry.PhotoData;
import com.google.android.mms.MmsException;
import com.mediatek.encapsulation.com.google.android.mms.EncapsulatedContentType;
import com.mediatek.encapsulation.MmsLog;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** M:
 * VCardViewerActivity
 */
public class VCardViewerActivity extends Activity {

    private static String TAG = "Mms/VCardViewer";
    private Drawable mDefaultContactImage;
    private ListView mListView;
    private VCardViewerAdapter mListAdapter;

    private List<VCardEntry> mVCardEntryList;
    private VCardModel mVCardModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vcard_viewer_activity);

        Uri dataUri = getIntent().getData();
        if (dataUri == null) {
            MmsLog.e(TAG, "onCreate(): dataUri is null!");
            VCardUtils.toastErrorHappened(this, R.string.file_attachment_import_vcard);
            finish();
            return;
        }
        try {
            String filename = getIntent().getStringExtra("file_name");
            mVCardModel = new VCardModel(this, EncapsulatedContentType.TEXT_VCARD,
                filename, dataUri);
        } catch (MmsException e) {
            MmsLog.e(TAG, "onCreate(): Exception happen in VCardModel construction!", e);
            VCardUtils.toastErrorHappened(this, R.string.file_attachment_import_vcard);
            finish();
            return;
        }
        if (mVCardModel == null) {
            MmsLog.e(TAG, "onCreate(): mVCardModel is null!");
            VCardUtils.toastErrorHappened(this, R.string.file_attachment_import_vcard);
            finish();
            return;
        }
        mVCardEntryList = VCardUtils.parserVCardFile(this, dataUri, new VCardEntryConstructor(), VCardUtils.PARSE_ALL);
        if (mVCardEntryList == null || mVCardEntryList.size() == 0) {
            finish();
            return;
        }
        initResourceRefs();
        mDefaultContactImage = getResources().getDrawable(R.drawable.ic_contact_picture);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        final MenuItem importMenu = menu.add(R.string.file_attachment_import_vcard);
//        importMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        importMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (mVCardModel != null) {
                    VCardUtils.importVCard(VCardViewerActivity.this, (FileAttachmentModel) mVCardModel);
                }
                return false;
            }
        });
        return true;
    }

    private void initResourceRefs() {
        mListView = (ListView) findViewById(R.id.vcard_contact_list);
        mListAdapter = new VCardViewerAdapter(this, mVCardEntryList);
        mListView.setAdapter(mListAdapter);
    }

    private String getVCardContentString(VCardEntry vCardEntry) {
        MyToStringIterator iterator = new MyToStringIterator();
        iterator.setContext(this);
        iterateAllData(iterator, vCardEntry);
        return iterator.toString();
    }

    public final void iterateAllData(EntryElementIterator iterator, VCardEntry vCardEntry) {
        iterator.onIterationStarted();
        iterator.onElementGroupStarted(vCardEntry.getNameData().getEntryLabel());
        iterator.onElement(vCardEntry.getNameData());
        iterator.onElementGroupEnded();

        iterateOneList(vCardEntry.getPhoneList(), iterator);
        iterateOneList(vCardEntry.getEmailList(), iterator);
        iterateOneList(vCardEntry.getImList(), iterator);
        iterateOneList(vCardEntry.getNickNameList(), iterator);
        iterateOneList(vCardEntry.getWebsiteList(), iterator);
        iterateOneList(vCardEntry.getPostalList(), iterator);
        iterateOneList(vCardEntry.getNotes(), iterator);
        iterateOneList(vCardEntry.getOrganizationList(), iterator);
//        iterateOneList(vCardEntry.getSipList(), iterator);
//        iterateOneList(vCardEntry.getPhotoList(), iterator);
//        iterateOneList(vCardEntry.getAndroidCustomDataList(), iterator);

        if (vCardEntry.getBirthday() != null) {
            VCardEntry.BirthdayData birthdayData = new VCardEntry.BirthdayData(vCardEntry.getBirthday());
            iterator.onElementGroupStarted(birthdayData.getEntryLabel());
            iterator.onElement(birthdayData);
            iterator.onElementGroupEnded();
        }
//        if (mAnniversary != null) {
//            iterator.onElementGroupStarted(mAnniversary.getEntryLabel());
//            iterator.onElement(mAnniversary);
//            iterator.onElementGroupEnded();
//        }
        iterator.onIterationEnded();
    }

    private void iterateOneList(List<? extends EntryElement> elemList,
            EntryElementIterator iterator) {
        if (elemList != null && elemList.size() > 0) {
            iterator.onElementGroupStarted(elemList.get(0).getEntryLabel());
            for (EntryElement elem : elemList) {
                iterator.onElement(elem);
            }
            iterator.onElementGroupEnded();
        }
    }

    private class VCardViewerAdapter extends ArrayAdapter<VCardEntry> {
        private Context mContext;
        private LayoutInflater mInflater;
        private static final int mLayout = R.layout.vcard_viewer_list_item;

        public VCardViewerAdapter(Context context, List<VCardEntry> vCardEntryList) {
            super(context, mLayout, vCardEntryList);
            mContext = context;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView avatarImage;
            TextView vCardText;

            View view;
            if (convertView == null) {
                view = mInflater.inflate(mLayout, parent, false);
            } else {
                view = convertView;
            }

            // Set avatar icon
            avatarImage = (ImageView) view.findViewById(R.id.vcard_contact_avatar);
            List<PhotoData> photoList = getItem(position).getPhotoList();
            if (photoList != null && photoList.size() > 0) {
                Bitmap b = BitmapFactory.decodeByteArray(photoList.get(0).getBytes(), 0, photoList.get(0).getBytes().length);
                BitmapDrawable mAvatar = new BitmapDrawable(mContext.getResources(), b);
                avatarImage.setImageDrawable(mAvatar);
            } else {
                avatarImage.setImageDrawable(mDefaultContactImage);
            }
            avatarImage.setVisibility(View.GONE);

            // Set text field
            vCardText = (TextView) view.findViewById(R.id.vcard_contact_text);
//            vCardText.setText(getItem(position).getTitle());
            vCardText.setText(getVCardContentString(getItem(position)));

            return view;
        }
    }


}