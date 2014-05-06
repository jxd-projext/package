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
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import com.mediatek.contacts.ext.ContactAccountExtension;
import com.mediatek.contacts.ext.ContactAccountExtension.OnGuideFinishListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

public class ContactAccountExtensionContainer extends ContactAccountExtension {

    private static final String TAG = "ContactAccountExtensionContainer";

    private LinkedList<ContactAccountExtension> mSubExtensionList;

    public void add(ContactAccountExtension extension) {
        if (null == mSubExtensionList) {
            mSubExtensionList = new LinkedList<ContactAccountExtension>();
        }
        mSubExtensionList.add(extension);
    }

    public void remove(ContactAccountExtension extension) {
        if (null == mSubExtensionList) {
            return;
        }
        mSubExtensionList.remove(extension);
    }

    public boolean needNewDataKind(String commd) {
        int i = 0;
        Log.i(TAG, "[needNewDataKind()]");
        if (null == mSubExtensionList) {
            return false;
        } else {
            Iterator<ContactAccountExtension> iterator = mSubExtensionList.iterator();
            while (iterator.hasNext()) {
                boolean result = iterator.next().needNewDataKind(commd);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    /** M:AAS & SNE @ { */

    public boolean isFeatureEnabled(String cmd) {
        Log.i(TAG, "[isFeatureEnabled()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.isFeatureEnabled(cmd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isFeatureAccount(String accountType, String cmd) {
        Log.i(TAG, "[isFeatureAccount()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.isFeatureAccount(accountType, cmd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setCurrentSlot(int slotId, String commd) {
        Log.i(TAG, "[setCurrentSlot()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                subExtension.setCurrentSlot(slotId, commd);
            }
        }
    }

    public int getCurrentSlot(String commd) {
        Log.i(TAG, "[getCurrentSlot()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                int result = subExtension.getCurrentSlot(commd);
                if (-1 != result) {
                    return result;
                }
            }
        }
        return -1;
    }

    public boolean hidePhoneLabel(String accountType, String mimeType, String value, String commd) {
        Log.i(TAG, "[hidePhoneLabel()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.hidePhoneLabel(accountType, mimeType, value, commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String[] getProjection(int type, String[] defaultProjection, String commd) {
        Log.i(TAG, "[getProjection()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                String[] result = subExtension.getProjection(type, defaultProjection, commd);
                if (!Arrays.equals(defaultProjection, result)) {
                    return result;
                }
            }
        }
        return defaultProjection;
    }

    public boolean isPhone(String mimeType, String commd) {
        Log.i(TAG, "[isPhone()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.isPhone(mimeType, commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public CharSequence getTypeLabel(Resources res, int type, CharSequence label, int slotId,
            String commd) {
        Log.i(TAG, "[getTypeLabel()]");
        CharSequence def = Phone.getTypeLabel(res, type, label);
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                CharSequence result = subExtension.getTypeLabel(res, type, label, slotId, commd);
                if (!def.equals(result)) {
                    return result;
                }
            }
        }
        return def;
    }

    public String getCustomTypeLabel(int type, String customColumn, String commd) {
        Log.i(TAG, "[getCustomTypeLabel()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                String result = subExtension.getCustomTypeLabel(type, customColumn, commd);
                if (null != result) {
                    return result;
                }
            }
        }
        return null;
    }

    public boolean updateContentValues(String accountType, ContentValues updatevalues,
            ArrayList anrsList, String text, int type, String commd) {
        Log.i(TAG, "[updateContentValues()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.updateContentValues(accountType, updatevalues, anrsList, text,
                        type, commd)) {
                    return true;
                }
            }
        }
        return super.updateContentValues(accountType, updatevalues, anrsList, text, type, commd);
    }

    public boolean updateDataToDb(String accountType, ContentResolver resolver, ArrayList newArr,
            ArrayList oldArr, long rawId, int type, String commd) {
        Log.i(TAG, "[updateDataToDb()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.updateDataToDb(accountType, resolver, newArr, oldArr, rawId, type,
                        commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isTextValid(String text, int slotId, int feature, String cmd) {
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (!subExtension.isTextValid(text, slotId, feature, cmd)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean buildOperation(String accountType,
            ArrayList<ContentProviderOperation> operationList, ArrayList anrList, String text,
            int backRef, int type, String commd) {
        Log.i(TAG, "[buildOperation()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.buildOperation(accountType, operationList, anrList, text, backRef,
                        type, commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkOperationBuilder(String accountType,
            ContentProviderOperation.Builder builder, Cursor cursor, int type, String commd) {
        Log.i(TAG, "[checkOperationBuilder()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.checkOperationBuilder(accountType, builder, cursor, type, commd)) {
                    return true;
                }
            }
        }
        return super.checkOperationBuilder(accountType, builder, cursor, type, commd);
    }

    public boolean buildValuesForSim(String accountType, Context context, ContentValues values,
            ArrayList<String> additionalNumberArray, ArrayList<Integer> phoneTypeArray,
            int maxAnrCount, int dstSlotId, ArrayList anrsList, String commd) {
        Log.i(TAG, "[buildValuesForSim()]");
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.buildValuesForSim(accountType, context, values,
                        additionalNumberArray, phoneTypeArray, maxAnrCount, dstSlotId, anrsList,
                        commd)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean buildOperationFromCursor(String accountType,
            ArrayList<ContentProviderOperation> operationList, final Cursor cursor, int index,
            String cmd) {
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                if (subExtension.buildOperationFromCursor(accountType, operationList, cursor,
                        index, cmd)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** M: @ } */

    /**
     * Called when the app want to show application guide
     * @param activity: The parent activity
     * @param type: The app type, such as "CONTACTS"
     */
    public void switchSimGuide(Activity activity, String type, String commd) {
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                subExtension.switchSimGuide(activity, type, commd);
            }
        }
    }

    /**
     * Called when the app want to show VCS application guide
     * @param activity The parent activity
     * @param commd The commd fotrwhich Plugin Implements will run
     */
    public boolean setVcsAppGuideVisibility(Activity activity, boolean visibility, OnGuideFinishListener listener,
            String commd) {
        if (null != mSubExtensionList) {
            for (ContactAccountExtension subExtension : mSubExtensionList) {
                return subExtension.setVcsAppGuideVisibility(activity, visibility, listener, commd);
            }
        }
        return false;
    }
}
