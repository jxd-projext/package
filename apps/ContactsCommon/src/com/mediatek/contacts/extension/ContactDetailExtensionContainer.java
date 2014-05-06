/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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
package com.mediatek.contacts.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.mediatek.contacts.ext.ContactDetailExtension;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class ContactDetailExtensionContainer extends ContactDetailExtension {

    private static final String TAG = "ContactDetailExtensionContainer";

    private LinkedList<ContactDetailExtension> mSubExtensionList;

    public void add(ContactDetailExtension extension) {
        if (null == mSubExtensionList) {
            mSubExtensionList = new LinkedList<ContactDetailExtension>();
        }
        mSubExtensionList.add(extension);
    }

    public void remove(ContactDetailExtension extension) {
        if (null == mSubExtensionList) {
            return;
        }
        mSubExtensionList.remove(extension);
    }

    public void setMenu(ContextMenu menu, boolean isNotDirectoryEntry, int simId,
            boolean mOptionsMenuOptions, int delSim, int newSim, Activity activity,
            int removeAssociation, int menuAssociation, String commd) {
        Log.i(TAG, "[setMenu()]");
        if (null != mSubExtensionList) {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension extension = iterator.next();
                if (extension.getCommand().equals(commd)) {
                    extension.setMenu(menu, isNotDirectoryEntry, simId, mOptionsMenuOptions, delSim,
                                            newSim, activity, removeAssociation, menuAssociation, commd);
                    return ;
                }
            }
        }
        super.setMenu(menu, isNotDirectoryEntry, simId, mOptionsMenuOptions, delSim, newSim, activity,
                      removeAssociation, menuAssociation, commd);
    }

    public String setSPChar(String string, String commd) {
        Log.i(TAG, "[setSPChar()]");
        if (null == mSubExtensionList) {
            return string;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                String str = iterator.next().setSPChar(string, commd);
                if (null != str && !str.equals(string)) {
                    return str;
                } else if (null != string && !string.equals(str)) {
                    return str;
                }
            }
        }
        return string;
    }

    public void setViewKeyListener(EditText fieldView, String commd) {
        Log.i(TAG, "[setViewKeyListener]");
        if (null == mSubExtensionList) {
            return;
        }
        Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().setViewKeyListener(fieldView, commd);
        }
    }

    public String TextChanged(int inputType, Editable s, String phoneText, int location,
            String commd) {
        Log.i(TAG, "[TextChanged()]");
        if (null == mSubExtensionList) {
            return phoneText;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension extension = iterator.next();
                if (extension.getCommand().equals(commd)) {
                    final String result = extension.TextChanged(inputType, s, phoneText, location,
                            commd);
                    return result;
                }
            }
        }
        return super.TextChanged(inputType, s, phoneText, location, commd);
    }

    public boolean checkMenuItem(boolean mtkGeminiSupport, boolean hasPhoneEntry, boolean notMe,
            String commd) {
        Log.i(TAG, "[checkMenuItem()]");
        if (null != mSubExtensionList) {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension extension = iterator.next();
                if (extension.getCommand().equals(commd)) {
                    return extension.checkMenuItem(mtkGeminiSupport, hasPhoneEntry, notMe, commd);
                }
            }
        }
        return super.checkMenuItem(mtkGeminiSupport, hasPhoneEntry, notMe, commd);
    }

    public void setMenuVisible(MenuItem associationMenuItem, boolean mOptionsMenuOptions,
            boolean isEnabled, String commd) {
        Log.i(TAG, "[setMenuVisible()]");
        if (null != mSubExtensionList) {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension extension = iterator.next();
                if (extension.getCommand().equals(commd)) {
                    extension.setMenuVisible(associationMenuItem, mOptionsMenuOptions, isEnabled, commd);
                    return ;
                }
            }
        }
        super.setMenuVisible(associationMenuItem, mOptionsMenuOptions, isEnabled, commd);
    }

    public String repChar(String phoneNumber, char pause, char p, char wait, char w, String commd) {
        Log.i(TAG, "phoneNumber : " + phoneNumber);
        if (null == mSubExtensionList) {
            return phoneNumber;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension extension = iterator.next();
                if (extension.getCommand().equals(commd)) {
                    final String result = extension.repChar(phoneNumber, pause, p, wait, w, commd);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return phoneNumber;
    }

    public void onContactDetailOpen(Uri contactLookupUri, String commd) {
        Log.i(TAG, "[onContactDetailOpen()]");
        if (null == mSubExtensionList) {
            return;
        }
        Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().onContactDetailOpen(contactLookupUri, commd);
        }
    }

    public String getExtentionMimeType(String commd) {
        Log.i(TAG, "[getExtentionMimeType()]");
        if (null == mSubExtensionList) {
            return null;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                String str = iterator.next().getExtentionMimeType(commd);
                if (null != str) {
                    return str;
                }
            }
        }
        return null;
    }

    public int layoutExtentionIcon(int leftBound, int topBound, int bottomBound, int rightBound,
            int mGapBetweenImageAndText, ImageView mExtentionIcon, String commd) {
        Log.i(TAG, "[layoutExtentionIcon()]");
        if (null == mSubExtensionList) {
            return rightBound;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                int result = iterator.next().layoutExtentionIcon(leftBound, topBound, bottomBound,
                        rightBound, mGapBetweenImageAndText, mExtentionIcon, commd);
                if (result != rightBound) {
                    return result;
                }
            }
        }
        return rightBound;
    }

    
    /**
     * Layout Textview which show unread count of Joyn messages
     * 
     * 
     * @return rightBound
     */
    public int layoutExtentionText(int leftBound, int topBound, int bottomBound, int rightBound,
            int mGapBetweenImageAndText, TextView mExtentionText, String commd) {
        Log.i(TAG, "[layoutExtentionText()]");
        if (null == mSubExtensionList) {
        	Log.i(TAG, "[layoutExtentionText() mSubExtesionList is null] "+ rightBound);
            return rightBound;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                int result = iterator.next().layoutExtentionText(leftBound, topBound, bottomBound,
                        rightBound, mGapBetweenImageAndText, mExtentionText, commd);
                if (result != rightBound) {
                	Log.i(TAG, "[layoutExtentionText() result is not equal to rightBound] "+ result);
                    return result;
                }
            }
        }
        Log.i(TAG, "[layoutExtentionText()] end reached "+ rightBound);
        return rightBound;
    }

    public void measureExtentionIcon(ImageView mExtentionIcon, String commd) {
        Log.i(TAG, "[measureExtentionIcon()]");
        if (null == mSubExtensionList) {
            return;
        }
        Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().measureExtentionIcon(mExtentionIcon, commd);
        }
    }

    public void measureExtentionText(TextView mExtentionText, String commd) {
        Log.i(TAG, "[measureExtentionText()]");
        if (null == mSubExtensionList) {
            return;
        }
        Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().measureExtentionText(mExtentionText, commd);
        }
    }

    public Intent getExtentionIntent(int action01, int action02, String commd) {
        Log.i(TAG, "[getExtentionIntent()]");
        if (null == mSubExtensionList) {
            return null;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                Intent result = iterator.next().getExtentionIntent(action01, action02, commd);
                if (null != result) {
                    return result;
                }
            }
        }
        return null;
    }

    public boolean getExtentionKind(String mimeType, boolean needSetName, String name, String commd) {
        Log.i(TAG, "[getExtentionKind()]");
        if (null == mSubExtensionList) {
            return false;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                boolean result = iterator.next().getExtentionKind(mimeType, needSetName, name,
                        commd);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    public boolean collapsePhoneEntries(String commd) {
        Log.i(TAG, "[collapsePhoneEntries()]");
        if (null == mSubExtensionList) {
            return true;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                boolean result = iterator.next().collapsePhoneEntries(commd);
                if (!result) {
                    return result;
                }
            }
        }
        return true;
    }

    public boolean checkPluginSupport(String commd) {
        Log.i(TAG, "[checkPluginSupport()]");
        if (null == mSubExtensionList) {
            return false;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                boolean result = iterator.next().checkPluginSupport(commd);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    public String getExtensionTitles(String data, String mimeType, String kind,
            HashMap<String, String> mPhoneAndSubtitle, String commd) {
        Log.i(TAG, "[getExtensionTitles()]");
        if (null == mSubExtensionList) {
            return kind;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                String str = iterator.next().getExtensionTitles(data, mimeType, kind,
                        mPhoneAndSubtitle, commd);
                Log.i(TAG, "str ,data, kind: " + str + ", " + data + " , " + kind);
                if (!TextUtils.isEmpty(str) && !str.equals(kind)) {
                    Log.i(TAG, "str1 : " + str);
                    return str;
                }

            }
        }
        Log.i(TAG, "return null too");
        return kind;
    }

    public boolean canSetExtensionIcon(long contactId, String commd) {
        Log.i(TAG, "[canSetExtensionIcon()]");
        if (null == mSubExtensionList) {
            return false;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                boolean result = iterator.next().canSetExtensionIcon(contactId, commd);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    public void setExtensionImageView(ImageView view, long contactId, String commd) {
        Log.i(TAG, "[setExtensionImageView()]");
        if (null == mSubExtensionList) {
            return;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                iterator.next().setExtensionImageView(view, contactId, commd);
            }
        }
    }

    public void setExtensionTextView(TextView view, String commd) {
        Log.i(TAG, "[setExtensionTextView()]");
        if (null == mSubExtensionList) {
            return;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                iterator.next().setExtensionTextView(view,commd);
            }
        }
    }

    public void setViewVisible(View view, Activity activity, String data1, String data2,
            String data3, String commd, int res1, int res2, int res3, int res4, int res5, int res6, int res7, int res8, long contactId, Context context, int res9, int res10, int res11, int res12, int res13) {
        Log.i(TAG, "[setViewVisible()]");
        if (null == mSubExtensionList) {
            return;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                iterator.next().setViewVisible(view, activity, data1, data2, data3, commd, res1,
                        res2, res3, res4, res5, res6, res7, res8, contactId, context, res9, res10, res11, res12, res13);
            }
        }
    }

    public void setViewVisibleWithCharSequence(View view, Activity activity, String data1,
            String data2, CharSequence data3, String commd, int res1, int res2, int res3, int res4,
            int res5, int res6) {
        Log.i(TAG, "[setViewVisibleWithCharSequence()]");
        if (null == mSubExtensionList) {
            return;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                iterator.next().setViewVisibleWithCharSequence(view, activity, data1, data2, data3,
                        commd, res1, res2, res3, res4, res5, res6);
            }
        }
    }

    /** M:AAS @ { */

    public boolean updateView(View view, int type, int action, String commd) {
        Log.i(TAG, "[updateView()]");
        if (null != mSubExtensionList) {
            for (ContactDetailExtension subExtension : mSubExtensionList) {
                if (subExtension.updateView(view, type, action, commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getMaxEmptyEditors(String mimeType, String commd) {
        Log.i(TAG, "[getMaxEmptyEditors()]");
        if (null != mSubExtensionList) {
            for (ContactDetailExtension subExtension : mSubExtensionList) {
                int result = subExtension.getMaxEmptyEditors(mimeType, commd);
                if (1 != result) {
                    return result;
                }
            }
        }
        return 1;
    }

    public int getAdditionNumberCount(int slotId, String commd) {
        Log.i(TAG, "[getAdditionNumberCount()]");
        if (null != mSubExtensionList) {
            for (ContactDetailExtension subExtension : mSubExtensionList) {
                int result = subExtension.getAdditionNumberCount(slotId, commd);
                if (0 != result) {
                    return result;
                }
            }
        }
        return 0;
    }

    public boolean isDoublePhoneNumber(String[] buffer, String[] bufferName, String commd) {
        Log.i(TAG, "[isDoublePhoneNumber()]");
        boolean def = (!TextUtils.isEmpty(buffer[1])) || (!TextUtils.isEmpty(bufferName[1]));
        if (null != mSubExtensionList) {
            for (ContactDetailExtension subExtension : mSubExtensionList) {
                boolean result = subExtension.isDoublePhoneNumber(buffer, bufferName, commd);
                if (def != result) {
                    return result;
                }
            }
        }
        return def;
    }
    /** M: @ } */

    ///M: for SNS plugin @{
    @Override
    public boolean isMimeTypeSupported(String mimeType, String plugin,
            String commd) {
        Log.i(TAG, "[isMimeTypeSupported()]");
        if (null == mSubExtensionList) {
            return false;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList
                    .iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension cde = iterator.next();
                if (commd.equals(cde.getCommand())) {
                    return cde.isMimeTypeSupported(mimeType, plugin, commd);
                }
            }

            return false;
        }
    }

    @Override
    public void setViewVisibleWithBundle(View view, Activity activity,
            Bundle data, int res1, int res2, int res3, int res4, int res5,
            int res6, int res7, int res8, String commd) {
        Log.i(TAG, "[setViewVisibleWithBundle()]");
        if (null == mSubExtensionList) {
            return;
        } else {
            Iterator<ContactDetailExtension> iterator = mSubExtensionList
                    .iterator();
            while (iterator.hasNext()) {
                ContactDetailExtension cde = iterator.next();
                if (commd.equals(cde.getCommand())) {
                    cde.setViewVisibleWithBundle(view, activity, data, res1,
                            res2, res3, res4, res5, res6, res7, res8, commd);
                }
            }
        }
    }
    ///@}

    /**
     * get the rcs-e icon on the Detailt Actvitiy's action bar
     * @return if there isn't show rcs-e icon,return null.
     */
    @Override
    public Drawable getRCSIcon(long id) {
        Log.i(TAG, "[updateRCSIconWithActionBar()]");
        if (null == mSubExtensionList) {
            return null;
        }
        Iterator<ContactDetailExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            Drawable drawable = iterator.next().getRCSIcon(id);
            if (drawable != null) {
                return drawable;
            }
        }
        return null;
    }
}
