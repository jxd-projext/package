/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.data.Conversation;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.data.ContactList;

/// M: add for gemini
import com.android.mms.MmsApp;
import com.google.android.mms.MmsException;
import com.mediatek.encapsulation.android.provider.EncapsulatedSettings;
import com.mediatek.encapsulation.android.telephony.EncapsulatedSimInfoManager;
import com.mediatek.encapsulation.com.android.internal.telephony.EncapsulatedPhone;
import com.mediatek.encapsulation.com.mediatek.common.featureoption.EncapsulatedFeatureOption;
import com.mediatek.encapsulation.MmsLog;
import com.android.mms.util.Recycler;

import java.util.List;

/**
 * Respond to a special intent and send an SMS message without the user's intervention.
 */
public class NoConfirmationSendService extends IntentService {
    public NoConfirmationSendService() {
        // Class name will be the thread name.
        super(NoConfirmationSendService.class.getName());

        // Intent should be redelivered if the process gets killed before completing the job.
        setIntentRedelivery(true);
    }

    private static final String TAG = "Mms/NoConfirmationSendService";

    @Override
    protected void onHandleIntent(Intent intent) {
        ComposeMessageActivity.log("NoConfirmationSendService onHandleIntent");

        String action = intent.getAction();
        if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)) {
            ComposeMessageActivity.log("NoConfirmationSendService onHandleIntent wrong action: " +
                    action);
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            ComposeMessageActivity.log("Called to send SMS but no extras");
            return;
        }

        String message = extras.getString(Intent.EXTRA_TEXT);

        Uri intentUri = intent.getData();
        String recipients = Conversation.getRecipients(intentUri);

        if (TextUtils.isEmpty(recipients)) {
            ComposeMessageActivity.log("Recipient(s) cannot be empty");
            return;
        }
        /// M: fix bug ALPS00444712, can't create Custom Msg when on the airplane mode
        int airplaneMode = EncapsulatedSettings.System.getInt(this.getContentResolver(),
                EncapsulatedSettings.System.AIRPLANE_MODE_ON, 0);
        if (extras.getBoolean("showUI", false) && airplaneMode != 1) {
            intent.setClassName(this, "com.android.mms.ui.ComposeMessageActivityNoLockScreen");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            if (TextUtils.isEmpty(message)) {
                ComposeMessageActivity.log("Message cannot be empty");
                return;
            }
            String[] dests = TextUtils.split(recipients, ";");
            // Using invalid threadId 0 here. When the message is inserted into
            // the db, the
            // provider looks up the threadId based on the recipient(s).
            long threadId = 0;
            SmsMessageSender smsMessageSender = new SmsMessageSender(this, dests, message, threadId);
            Log.e(TAG, "Slot Id =" + extras.getInt(EncapsulatedPhone.GEMINI_SIM_ID_KEY, -1));
            int simId = 0;

            // / M:Code analyze 001, add simId info for gemini @{
            if (EncapsulatedFeatureOption.MTK_GEMINI_SUPPORT) {
                simId = (int)(EncapsulatedSimInfoManager.getIdBySlot(this,extras.getInt(EncapsulatedPhone.GEMINI_SIM_ID_KEY, -1)));
                Log.e(TAG, "MTK_GEMINI_SUPPORT TRUE" );
                smsMessageSender.setSimId(simId);
            } else {
            
                List<EncapsulatedSimInfoManager> simInfoList = EncapsulatedSimInfoManager.getInsertedSimInfoList(this);
                if(simInfoList != null){
                    simId = simInfoList.isEmpty() ? 0 : (int) simInfoList.get(0).getSimId();
                }
                Log.e(TAG, "sim Id =" + simId);
                smsMessageSender.setSimId(simId);
            }

            // / @}
            try {
                // This call simply puts the message on a queue and sends a
                // broadcast to start
                // a service to send the message. In queing up the message,
                // however, it does
                // insert the message into the DB.
                smsMessageSender.sendMessage(threadId);
                // Make sure this thread isn't over the limits in message count
                ContactList selected = ContactList.getByNumbers(recipients, false, false);
                Conversation conversation = Conversation.get(this, selected, false);
                threadId = conversation.getThreadId();
                Recycler.getSmsRecycler().deleteOldMessagesByThreadId(this, threadId);
                // / M:Code analyze 002,using customized exception
            } catch (MmsException e) {
                Log.e(TAG, "Failed to send SMS message, threadId=" + threadId, e);
            }
        }
    }
}
