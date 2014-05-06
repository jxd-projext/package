/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.mms.widget;

import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony.Threads;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.TextView;
import android.widget.RelativeLayout.LayoutParams;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.ConversationList;
import com.android.mms.ui.ConversationListItem;
import com.android.mms.ui.FolderViewList;
import com.android.mms.ui.MessageUtils;

import java.util.Collection;
import java.util.Map;

import com.mediatek.encapsulation.android.telephony.EncapsulatedTelephony;
import com.mediatek.ipmsg.util.IpMessageUtils;
import com.android.mms.util.SmileyParser2;
import com.mediatek.mms.ipmessage.IpMessageConsts;

public class MmsWidgetService extends RemoteViewsService {
    private static final String TAG = "MmsWidgetService";

    public static final String ACTION_FOLDER_MODE = "com android.mms.widget.ACTION_FOLDER_MODE";
    public static final String ACTION_CONVERSATION_MODE = "com android.mms.widget.ACTION_CONVERSATION_MODE";
    public static final String EXTRA_KEY_FOLDER_TYPE = "folder_type";
    public static final String EXTRA_KEY_CONVSATION_TYPE = "conversation_type";
    public static final String EXTRA_KEY_THREAD_ID = "thread_id";

    public static final int FOLDER_HAS_UNREAD = 1;
    public static final int FOLDER_CB_OR_PUSH = 2;
    public static final int FOLDER_HAS_DRAFT  = 3;
    public static final int FOLDER_HAS_ERROR  = 4;
    public static final int FOLDER_NORMAL     = 5;

    public static final int MORE_MESSAGES     = 600;
    /**
     * Lock to avoid race condition between widgets.
     */
    private static final Object sWidgetLock = new Object();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Log.v(TAG, "onGetViewFactory intent: " + intent);
        return new MmsFactory(getApplicationContext(), intent);
    }

    /**
     * Remote Views Factory for Mms Widget.
     */
    private static class MmsFactory
            implements RemoteViewsService.RemoteViewsFactory, Contact.UpdateListener {
        private static final int MAX_CONVERSATIONS_COUNT = 25;
        private final Context mContext;
        private final int mAppWidgetId;
        private boolean mShouldShowViewMore;
        private static Cursor sConversationCursor;
        private int mUnreadConvCount;
        private final AppWidgetManager mAppWidgetManager;

        // Static colors
        private static int SUBJECT_TEXT_COLOR_READ;
        private static int SUBJECT_TEXT_COLOR_UNREAD;
        private static int SENDERS_TEXT_COLOR_READ;
        private static int SENDERS_TEXT_COLOR_UNREAD;
        private Handler mHandler = new Handler();

        public MmsFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            mAppWidgetManager = AppWidgetManager.getInstance(context);
            Log.v(TAG, "MmsFactory intent: " + intent + "widget id: " + mAppWidgetId);
            // Initialize colors
            Resources res = context.getResources();
            SENDERS_TEXT_COLOR_READ = res.getColor(R.color.widget_sender_text_color_read);
            SENDERS_TEXT_COLOR_UNREAD = res.getColor(R.color.widget_sender_text_color_unread);
            SUBJECT_TEXT_COLOR_READ = res.getColor(R.color.widget_subject_text_color_read);
            SUBJECT_TEXT_COLOR_UNREAD = res.getColor(R.color.widget_subject_text_color_unread);
        }

        @Override
        public void onCreate() {
            Log.v(TAG, "onCreate");
            Contact.addListener(this);
        }

        @Override
        public void onDestroy() {
            Log.v(TAG, "onDestroy");
            synchronized (sWidgetLock) {
                if (sConversationCursor != null && !sConversationCursor.isClosed()) {
                    sConversationCursor.close();
                    sConversationCursor = null;
                }
                Contact.removeListener(this);
            }
        }

        @Override
        public void onDataSetChanged() {
            Log.v(TAG, "onDataSetChanged is called");
            synchronized (sWidgetLock) {
                if (sConversationCursor != null) {
                    sConversationCursor.close();
                    sConversationCursor = null;
                }
                sConversationCursor = queryAllConversations();
                mUnreadConvCount = queryUnreadCount();
                Log.v(TAG, "mUnreadConvCount"+mUnreadConvCount);
                onLoadComplete();
            }
        }

        private Cursor queryAllConversations() {
            return mContext.getContentResolver().query(
                    Conversation.sAllThreadsUriExtend, Conversation.ALL_THREADS_PROJECTION_EXTEND,
                    null, null, null);
        }

        private int queryUnreadCount() {
            Cursor cursor = null;
            int unreadCount = 0;
            try {
                cursor = mContext.getContentResolver().query(Conversation.sAllUnreadMessagesUri,
                        null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    unreadCount = cursor.getInt(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            Log.v(TAG, "queryUnreadCount unreadCount=" + unreadCount);
            return unreadCount;
        }

        /**
         * Returns the number of items should be shown in the widget list.  This method also updates
         * the boolean that indicates whether the "show more" item should be shown.
         * @return the number of items to be displayed in the list.
         */
        @Override
        public int getCount() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getCount");
            }
            synchronized (sWidgetLock) {
                if (sConversationCursor == null) {
                    return 0;
                }
                final int count = getConversationCount();
                mShouldShowViewMore = count < sConversationCursor.getCount();
                mUnreadConvCount = queryUnreadCount();
                Log.v(TAG, "mUnreadConvCount" + mUnreadConvCount);
                onLoadComplete();
                return count + (mShouldShowViewMore ? 1 : 0);
            }
        }

        /**
         * Returns the number of conversations that should be shown in the widget.  This method
         * doesn't update the boolean that indicates that the "show more" item should be included
         * in the list.
         * @return
         */
        private int getConversationCount() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getConversationCount");
            }
                return Math.min(sConversationCursor.getCount(), MAX_CONVERSATIONS_COUNT);
        }

        /*
         * Add color to a given text
         */
        private SpannableStringBuilder addColor(CharSequence text, int color) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            if (color != 0) {
                builder.setSpan(new ForegroundColorSpan(color), 0, text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return builder;
        }

        /**
         * @return the {@link RemoteViews} for a specific position in the list.
         */
        @Override
        public RemoteViews getViewAt(int position) {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getViewAt position: " + position);
            }
            synchronized (sWidgetLock) {
                // "View more conversations" view.
                if (sConversationCursor == null
                        || (mShouldShowViewMore && position >= getConversationCount())) {
                    return getViewMoreConversationsView();
                }

                if (!sConversationCursor.moveToPosition(position)) {
                    // If we ever fail to move to a position, return the "View More conversations"
                    // view.
                    Log.w(TAG, "Failed to move to position: " + position);
                    return getViewMoreConversationsView();
                }

                Conversation conv = Conversation.from(mContext, sConversationCursor);

                // Inflate and fill out the remote view
                RemoteViews remoteViews = new RemoteViews(
                        mContext.getPackageName(), R.layout.widget_conversation);

                if (conv.hasUnreadMessages()) {
                    remoteViews.setViewVisibility(R.id.widget_unread_background, View.VISIBLE);
                    remoteViews.setViewVisibility(R.id.widget_read_background, View.GONE);
                } else {
                    remoteViews.setViewVisibility(R.id.widget_unread_background, View.GONE);
                    remoteViews.setViewVisibility(R.id.widget_read_background, View.VISIBLE);
                }
                boolean hasAttachment = conv.hasAttachment();
                remoteViews.setViewVisibility(R.id.attachment, hasAttachment ? View.VISIBLE :
                    View.GONE);

                /// M: check mute status
                remoteViews.setViewVisibility(R.id.mute, conv.isMute() ? View.VISIBLE : View.GONE);

                // Date M: use another method
                remoteViews.setTextViewText(R.id.date,
                        addColor(MessageUtils.formatTimeStampStringExtend(mContext, conv.getDate()),
                                conv.hasUnreadMessages() ? SUBJECT_TEXT_COLOR_UNREAD :
                                    SUBJECT_TEXT_COLOR_READ));

                // From
                int color = conv.hasUnreadMessages() ? SENDERS_TEXT_COLOR_UNREAD :
                        SENDERS_TEXT_COLOR_READ;
                SpannableStringBuilder from = addColor(conv.getRecipients().formatNames(", "),
                        color);

                /** M: do not show draft flag
                if (conv.hasDraft()) {
                    from.append(mContext.getResources().getString(R.string.draft_separator));
                    int before = from.length();
                    from.append(mContext.getResources().getString(R.string.has_draft));
                    from.setSpan(new TextAppearanceSpan(mContext,
                            android.R.style.TextAppearance_Small, color), before,
                            from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    from.setSpan(new ForegroundColorSpan(
                            mContext.getResources().getColor(R.drawable.text_color_red)),
                            before, from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }
                */

                // Unread messages are shown in bold
                if (conv.hasUnreadMessages()) {
                    from.setSpan(ConversationListItem.STYLE_BOLD, 0, from.length(),
                            Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }

                /// M: if this is a guide thread
                if (conv.getType() == EncapsulatedTelephony.Threads.IP_MESSAGE_GUIDE_THREAD) {
                    String title = IpMessageUtils.getResourceManager(mContext)
                        .getSingleString(IpMessageConsts.string.ipmsg_service_title);
                    remoteViews.setTextViewText(R.id.from, title);
                } else {
                    remoteViews.setTextViewText(R.id.from, from);
                }

                // Subject
                // TODO: the SmileyParser inserts image spans but they don't seem to make it
                // into the remote view.
                if (IpMessageUtils.getIpMessagePlugin(mContext).isActualPlugin()
                        && IpMessageUtils.getServiceManager(mContext).isFeatureSupported(
                                IpMessageConsts.FeatureId.PARSE_EMO_WITHOUT_ACTIVATE)) {
                    SmileyParser2 parser = SmileyParser2.getInstance();
                    remoteViews.setTextViewText(R.id.subject,
                            addColor(parser.addSmileySpans(conv.getSnippet()),
                                    conv.hasUnreadMessages() ? SUBJECT_TEXT_COLOR_UNREAD :
                                        SUBJECT_TEXT_COLOR_READ));
                } else {
                    remoteViews.setTextViewText(R.id.subject,
                            addColor(conv.getSnippet(),
                                    conv.hasUnreadMessages() ? SUBJECT_TEXT_COLOR_UNREAD :
                                        SUBJECT_TEXT_COLOR_READ));
                }

                // On click intent.
//                Intent clickIntent = new Intent(Intent.ACTION_VIEW);
//                clickIntent.setType("vnd.android-dir/mms-sms");
//                clickIntent.putExtra("thread_id", conv.getThreadId());
                boolean dirMode;
                Intent clickIntent = null;
                dirMode = MmsConfig.getMmsDirMode();
                if (MmsConfig.getFolderModeEnabled() && dirMode) {
                    clickIntent = new Intent(ACTION_FOLDER_MODE);
                    if (conv.hasUnreadMessages()) {
                        clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, FOLDER_HAS_UNREAD);
                        clickIntent.putExtra(EXTRA_KEY_THREAD_ID, conv.getThreadId());
                    } else if (conv.hasDraft()) {
                        clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, FOLDER_HAS_DRAFT);
                    } else if (conv.hasError()) {
                        clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, FOLDER_HAS_ERROR);
                    } else if (EncapsulatedTelephony.Threads.WAPPUSH_THREAD == conv.getType() ||
                            EncapsulatedTelephony.Threads.CELL_BROADCAST_THREAD == conv.getType()) {
                        clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, FOLDER_CB_OR_PUSH);
                    } else {
                        clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, FOLDER_NORMAL);
                        clickIntent.putExtra(EXTRA_KEY_THREAD_ID, conv.getThreadId());
                    }
                } else {
                    clickIntent = new Intent(ACTION_CONVERSATION_MODE);
                    clickIntent.putExtra(EXTRA_KEY_CONVSATION_TYPE, conv.getType());
                    clickIntent.putExtra(EXTRA_KEY_THREAD_ID, conv.getThreadId());
                }

                remoteViews.setOnClickFillInIntent(R.id.widget_conversation, clickIntent);

                return remoteViews;
            }
        }

        /**
         * @return the "View more conversations" view. When the user taps this item, they're
         * taken to the messaging app's conversation list.
         */
        private RemoteViews getViewMoreConversationsView() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getViewMoreConversationsView");
            }
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.view_more_conversations));
            Intent clickIntent = null;
            if (MmsConfig.getFolderModeEnabled() && MmsConfig.getMmsDirMode()) {
                clickIntent = new Intent(ACTION_FOLDER_MODE);
                clickIntent.putExtra(EXTRA_KEY_FOLDER_TYPE, MORE_MESSAGES);
            } else {
                clickIntent = new Intent(ACTION_CONVERSATION_MODE);
                clickIntent.putExtra(EXTRA_KEY_CONVSATION_TYPE, MORE_MESSAGES);
            }
            view.setOnClickFillInIntent(R.id.widget_loading, clickIntent);
//            view.setOnClickFillInIntent(R.id.widget_loading,
//                    new Intent(mContext, ConversationList.class));
            return view;
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.loading_conversations));
            return view;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void onLoadComplete() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onLoadComplete");
            }
            RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.widget);

            remoteViews.setViewVisibility(R.id.widget_unread_count, mUnreadConvCount > 0 ?
                    View.VISIBLE : View.GONE);
            if (mUnreadConvCount > 0 && mUnreadConvCount <= 99) {
                remoteViews.setTextViewText(R.id.widget_unread_count,
                        Integer.toString(mUnreadConvCount));
            } else if (mUnreadConvCount > 99) {
                remoteViews.setTextViewText(R.id.widget_unread_count, "99+");
            }

            mAppWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, remoteViews);
        }
        private Runnable mUpdateFromContactRunnable = new Runnable() {
            public void run() {
                
                Log.v(TAG, "mUpdateFromContactRunnable is called ");
                mAppWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.conversation_list);
            }
        };

        public void onUpdate(Contact updated) {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onUpdate from Contact: " + updated);
            }
            
            Log.v(TAG, "onUpdate from Contact: " + updated);
            mHandler.removeCallbacks(mUpdateFromContactRunnable);
            mHandler.postDelayed(mUpdateFromContactRunnable,1000);
        }

    }

    /// M: fix bug ALPS00448814, update conversation in the MmsWidget @{
    @Override
    public void onCreate() {
        super.onCreate();
        getContentResolver().registerContentObserver(DRAFT_URI, true, mConversationObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mConversationObserver);
    }

    private static final Uri DRAFT_URI = Uri.parse("content://mms-sms/draftbox");

    private final ContentObserver mConversationObserver =
      new ContentObserver(new Handler()) {
          public void onChange(boolean selfUpdate) {
              MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
          }
    };
    /// @}
}
