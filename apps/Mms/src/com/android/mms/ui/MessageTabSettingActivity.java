/*
 * Copyright Statement:
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/*
 * MediaTek Inc. (C) 2010. All rights reserved.
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
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mms.ui;

import com.android.mms.R;
import android.app.ActionBar;
import android.app.ActionBar.TabListener;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import com.android.mms.MmsConfig;
import com.mediatek.encapsulation.android.content.EncapsulatedAction;
import com.mediatek.encapsulation.android.telephony.EncapsulatedSimInfoManager;
import com.mediatek.encapsulation.com.android.internal.telephony.EncapsulatedTelephonyIntents;
import com.mediatek.encapsulation.com.mediatek.common.featureoption.EncapsulatedFeatureOption;

import java.util.List;

public class MessageTabSettingActivity extends Activity implements TabListener {
    private static final String TAG = "MessageTabSettingActivity";

    private static final int MENU_RESTORE_DEFAULT = 1;

    private ActionBar mActionBar = null;

    // when there is only one sim card inserted.
    private int mSlotId = 0;

    private int mTabCount = 0;

    private boolean isSimChanged = false;

    private boolean isFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate called");
        setContentView(R.layout.message_tab_setting);
        mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setTitle(getResources().getString(R.string.menu_preferences));
        IntentFilter filter = new IntentFilter(EncapsulatedTelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        filter.addAction(EncapsulatedAction.SIM_SETTINGS_INFO_CHANGED);
        registerReceiver(mSimReceiver, filter);
        addFragment();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.v(TAG, "onStart()");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isSimChanged) {
            addFragment();
            isSimChanged = false;
        }
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy()");
        if (mSimReceiver != null) {
            unregisterReceiver(mSimReceiver);
        }
        super.onDestroy();
    }

    public void addFragment() {
        Log.d(TAG, "addFragment");
        int simCount = EncapsulatedSimInfoManager.getInsertedSimCount(this);
        Log.d(TAG, "simCount = " + simCount);
        ClassifyGeneralFragment generalFragment = ClassifyGeneralFragment.newInstance();
        switch (simCount) {
        case 0:
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.tab, generalFragment);
            ft.commit();
            break;
        case 1:
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            mActionBar.removeAllTabs();
            mSlotId = EncapsulatedSimInfoManager.getInsertedSimInfoList(this).get(0).getSlot();
            String singleSlot = getDisplayNameBySlotId(mSlotId);
            if (singleSlot != null) {
                String sim = mSlotId == 0 ? getResources().getString(R.string.menu_item_sim1) + ": " : getResources()
                        .getString(R.string.menu_item_sim2)
                    + ": ";
                addTabToActionBar(sim + singleSlot);
            }
            setInitialTab();
            break;
        case 2:
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            mActionBar.removeAllTabs();
            String firstSlot = getDisplayNameBySlotId(0);
            String secondSlot = getDisplayNameBySlotId(1);
            if (firstSlot != null) {
                addTabToActionBar(getResources().getString(R.string.menu_item_sim1) + ": " + firstSlot);
            }
            if (secondSlot != null) {
                addTabToActionBar(getResources().getString(R.string.menu_item_sim2) + ": " + secondSlot);
            }
            setInitialTab();
            break;
        default:
            break;
        }
        mTabCount = mActionBar.getTabCount();
    }

    private String getDisplayNameBySlotId(int slotId) {
        Log.d(TAG, "getDisplayNameBySlotId : " + slotId);
        EncapsulatedSimInfoManager simInfoManager = EncapsulatedSimInfoManager.getSimInfoBySlot(this, slotId);
        return simInfoManager.getDisplayName();
    }

    private Tab addTabToActionBar(String displayName) {
        Log.d(TAG, "addTabToActionBar : " + displayName);
        Tab tab = mActionBar.newTab();
        tab.setText(displayName);
        tab.setTabListener(this);
        mActionBar.addTab(tab);
        return tab;
    }

    private void setInitialTab() {
        ClassifyGeneralFragment generalFragment = ClassifyGeneralFragment.newInstance();
        Tab generalTab = addTabToActionBar(getResources().getString(R.string.pref_setting_general));
        mActionBar.selectTab(generalTab);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.tab, generalFragment);
        ft.commit();
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        Log.d(TAG, "onTabSelected");
        ClassifyGeneralFragment generalFragment = ClassifyGeneralFragment.newInstance();
        switch (mTabCount) {
        case 0:
            break;
        case 1:
            break;
        case 2:
            if (tab.getPosition() == 0) {
                ClassifySlotFragment singleSlotFragement = ClassifySlotFragment.newInstance(mSlotId);
                ft.replace(R.id.tab, singleSlotFragement);
            } else if (tab.getPosition() == 1) {
                ft.replace(R.id.tab, generalFragment);
            }
            break;
        case 3:
            if (tab.getPosition() == 0) {
                ClassifySlotFragment slot1Fragement = ClassifySlotFragment.newInstance(0);
                ft.replace(R.id.tab, slot1Fragement);
            } else if (tab.getPosition() == 1) {
                ClassifySlotFragment slot2Fragement = ClassifySlotFragment.newInstance(1);
                ft.replace(R.id.tab, slot2Fragement);
            } else if (tab.getPosition() == 2) {
                ft.replace(R.id.tab, generalFragment);
            }
            break;
        default:
            break;
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        menu.add(0, MENU_RESTORE_DEFAULT, 0, R.string.restore_default);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case MENU_RESTORE_DEFAULT:
            restoreDefault();
            addFragment();
            break;
        default:
            break;
        }
        return false;
    }

    private void restoreDefault() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MessageTabSettingActivity.this)
                .edit();
        List<EncapsulatedSimInfoManager> listSimInfo = EncapsulatedSimInfoManager.getInsertedSimInfoList(MessageTabSettingActivity.this);
        if (EncapsulatedFeatureOption.MTK_GEMINI_SUPPORT) {
            if (listSimInfo != null) {
                int simCount = listSimInfo.size();
                if (simCount > 0) {
                    for (int i = 0; i < simCount; i++) {
                        Long simId = listSimInfo.get(i).getSimId();
                        // sms default.
                        editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.SMS_DELIVERY_REPORT_MODE, false);
                        int slotId = listSimInfo.get(i).getSlot();
                        if (MmsConfig.getSmsMultiSaveLocationEnabled()) {
                            if (!getResources().getBoolean(R.bool.isTablet)) {
                                editor
                                        .putString(Long.toString(slotId) + "_" + ClassifySlotFragment.SMS_SAVE_LOCATION,
                                            ClassifySlotFragment.SETTING_SAVE_LOCATION);
                            } else {
                                editor.putString(Long.toString(slotId) + "_" + ClassifySlotFragment.SMS_SAVE_LOCATION,
                                    ClassifySlotFragment.SETTING_SAVE_LOCATION_TABLET);
                            }
                        }
                        // mms default.
                        editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.MMS_DELIVERY_REPORT_MODE, false);
                        if (!MessageUtils.isUSimType(slotId)) {
                            editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.READ_REPORT_MODE, false);
                            editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.READ_REPORT_AUTO_REPLY, false);
                        }
                        editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.AUTO_RETRIEVAL, true);
                        editor.putBoolean(Long.toString(simId) + "_" + ClassifySlotFragment.RETRIEVAL_DURING_ROAMING, false);
                    }
                }
            }
        } else {
            // sms default.
            editor.putBoolean(ClassifySlotFragment.SMS_DELIVERY_REPORT_MODE, false);
            if (!getResources().getBoolean(R.bool.isTablet)) {
                editor.putString(ClassifySlotFragment.SMS_SAVE_LOCATION, ClassifySlotFragment.SETTING_SAVE_LOCATION);
            } else {
                editor.putString(ClassifySlotFragment.SMS_SAVE_LOCATION, ClassifySlotFragment.SETTING_SAVE_LOCATION_TABLET);
            }
            // mms default.
            editor.putBoolean(ClassifySlotFragment.MMS_DELIVERY_REPORT_MODE, false);
            editor.putBoolean(ClassifySlotFragment.READ_REPORT_MODE, false);
            editor.putBoolean(ClassifySlotFragment.READ_REPORT_AUTO_REPLY, false);
            editor.putBoolean(ClassifySlotFragment.AUTO_RETRIEVAL, true);
            editor.putBoolean(ClassifySlotFragment.RETRIEVAL_DURING_ROAMING, false);
        }
        // mms default.
        editor.putString(ClassifyGeneralFragment.CREATION_MODE, ClassifyGeneralFragment.CREATION_MODE_FREE);
        editor.putString(ClassifyGeneralFragment.MMS_SIZE_LIMIT, ClassifyGeneralFragment.SIZE_LIMIT_300);
        editor.putString(ClassifyGeneralFragment.PRIORITY, ClassifyGeneralFragment.PRIORITY_NORMAL);
        editor.putBoolean(ClassifyGeneralFragment.GROUP_MMS_MODE, false);
        // notification default.
        editor.putBoolean(ClassifyGeneralFragment.NOTIFICATION_ENABLED,true);
        editor.putString(ClassifyGeneralFragment.NOTIFICATION_MUTE, Integer.toString(0));
        editor.putString(ClassifyGeneralFragment.NOTIFICATION_RINGTONE, ClassifyGeneralFragment.DEFAULT_RINGTONE);
        editor.putBoolean(ClassifyGeneralFragment.NOTIFICATION_VIBRATE, true);
        editor.putBoolean(ClassifyGeneralFragment.POPUP_NOTIFICATION, true);
        //general default.
        editor.putInt(ClassifyGeneralFragment.FONT_SIZE_SETTING, 0);
        String[] fontSizeValues = getResources().getStringArray(R.array.pref_message_font_size_values);
        editor.putFloat(ClassifyGeneralFragment.TEXT_SIZE, Float.parseFloat(fontSizeValues[0]));
        editor.putBoolean(ClassifyGeneralFragment.AUTO_DELETE, false);
        editor.putInt(ClassifyGeneralFragment.MAX_SMS_PER_THREAD, ClassifyGeneralFragment.SMS_SIZE_LIMIT_DEFAULT);
        editor.putInt(ClassifyGeneralFragment.MAX_MMS_PER_THREAD, ClassifyGeneralFragment.MMS_SIZE_LIMIT_DEFAULT);
        editor.putBoolean(ClassifyGeneralFragment.WAPPUSH_ENABLED, true);
        editor.putBoolean(ClassifyGeneralFragment.SHOW_EMAIL_ADDRESS, true);
        final ClassifyGeneralFragment generalFragment = ClassifyGeneralFragment.newInstance();
        new Thread() {
            public void run() {
                generalFragment.clearWallpaperAll(MessageTabSettingActivity.this);
            }
        }.start();
        editor.apply();
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    // update sim state dynamically. @{
    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isFirst) {
                isFirst = false;
                return;
            }
            String action = intent.getAction();
            if (action.equals(EncapsulatedTelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)
                    || action.equals(EncapsulatedAction.SIM_SETTINGS_INFO_CHANGED)) {
                Log.d(TAG, "receive sim info update");
                if (MessageTabSettingActivity.this.hasWindowFocus()) {
                    addFragment();
                } else {
                    isSimChanged = true;
                }
            }
        }
    };
}
