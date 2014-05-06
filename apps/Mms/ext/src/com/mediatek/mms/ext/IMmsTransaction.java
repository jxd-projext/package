/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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

package com.mediatek.mms.ext;

/// M: ALPS00440523, set service to foreground @ {
import android.app.Service;
/// @}
/// M: ALPS00545779, for FT, restart pending receiving mms @ {
import android.net.Uri;
/// @}
/// M: ALPS00452618, set special HTTP retry handler for CMCC FT @
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
/// @}

public interface IMmsTransaction {
    /**
     * Returns continuous server fail count.
     *
     * @return              Number of continuous server fail.
     */
    int getMmsServerFailCount();

    /**
     * Set status code from server this time, if it is server fail need handled, will make
     * server fail count inscrease
     * @param value         Status code from server
     *
     */
    void setMmsServerStatusCode(int code);

    /**
     * Update connection if we meet same server error many times.
     *
     * @return              If it really update connection returns true, otherwise false.
     */
    boolean updateConnection();

    /**
     * Check support waiting 1s before start PDP or not
     *
     * @return              If support return true, otherwise return false.
     */
    boolean isSyncStartPdpEnabled();

    /// M: ALPS00452618, set special HTTP retry handler for CMCC FT @
    /**
     * Get HTTP request retry handler
     *
     * @return              Return HTTP request retry handler instance
     */
   DefaultHttpRequestRetryHandler getHttpRequestRetryHandler();
    /// @}

    /// M: ALPS00440523, set service to foreground @ {
    /**
     * Set service to foreground
     *
     * @param service         Service that need to be foreground
     */
   void startServiceForeground(Service service);

    /**
     * Set service to foreground
     *
     * @param service         Service that need stop to be foreground
     */
   void stopServiceForeground(Service service);

    /**
     * Check support auto restart incompleted mms transactions or not
     *
     * @return              If support return true, otherwise return false
     */
    boolean isRestartPendingsEnabled();
    /// @}
    /// M: ALPS00440523, set property @ {
    void setSoSendTimeoutProperty();
    /// @}

    /// M: ALPS00545779, for FT, restart pending receiving mms @ {
    /**
     * Check if the specified pending Mms need restart
     *
     * pduUri                mms uri
     * failureType         Last fail reason of this mms
     * @return              If yes return true, otherwise return false
     */
    boolean isPendingMmsNeedRestart(Uri pduUri, int failureType);
    /// @}

    /// M: Support multi-transaction in GEMINI @ {
    /**
     * Check if the multi-transaction is supported for GEMINI
     *
     * @return              If yes return true, otherwise return false
     */
    boolean isGminiMultiTransactionEnabled();
    /// @}

    /**
     * M: to stop send the M-NotifyResp.ind to server for error case RETRIEVE_STATUS_ERROR_PERMANENT_MESSAGE_NOT_FOUND.
     * @return true: Do not send the Notify Response.
     *         false: Send the Notify Response ind to server
     */
    boolean isEnableRetrieveStatusErrorCheck();
}

