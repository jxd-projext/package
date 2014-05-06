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

package com.android.videoeditor.widgets;

import java.util.ArrayList;
import java.util.List;

import com.android.videoeditor.R;
import com.android.videoeditor.util.MtkLog;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * The timeline scroll view
 */
public class TimelineHorizontalScrollView extends HorizontalScrollView {
    public final static int PLAYHEAD_NORMAL = 1;
    public final static int PLAYHEAD_MOVE_OK = 2;
    public final static int PLAYHEAD_MOVE_NOT_OK = 3;

    // Instance variables
    private final List<ScrollViewListener> mScrollListenerList;
    private final Handler mHandler;
    private final int mPlayheadMarginTop;
    private final int mPlayheadMarginTopOk;
    private final int mPlayheadMarginTopNotOk;
    private final int mPlayheadMarginBottom;
    private Drawable mNormalPlayheadDrawable;
    private final Drawable mMoveOkPlayheadDrawable;
    private final Drawable mMoveNotOkPlayheadDrawable;
    private final int mHalfParentWidth;
    private ScaleGestureDetector mScaleDetector;
    private int mLastScrollX;
    private boolean mIsScrolling;
    private boolean mAppScroll;
    private boolean mEnableUserScrolling;

    // The runnable which executes when the scrolling ends
    private Runnable mScrollEndedRunnable = new Runnable() {
        @Override
        public void run() {
            mIsScrolling = false;

            for (ScrollViewListener listener : mScrollListenerList) {
                listener.onScrollEnd(TimelineHorizontalScrollView.this, getScrollX(),
                        getScrollY(), mAppScroll);
            }

            mAppScroll = false;
        }
    };

    public TimelineHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mEnableUserScrolling = true;
        mScrollListenerList = new ArrayList<ScrollViewListener>();
        mHandler = new Handler();

        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        // This value is shared by all children. It represents the width of
        // the left empty view.
        setTag(R.id.left_view_width, mHalfParentWidth);
        setTag(R.id.playhead_offset, -1);
        setTag(R.id.playhead_type, PLAYHEAD_NORMAL);

        final Resources resources = context.getResources();

        // Get the playhead margins
        mPlayheadMarginTop = (int)resources.getDimension(R.dimen.playhead_margin_top);
        mPlayheadMarginBottom = (int)resources.getDimension(R.dimen.playhead_margin_bottom);
        mPlayheadMarginTopOk = (int)resources.getDimension(R.dimen.playhead_margin_top_ok);
        mPlayheadMarginTopNotOk = (int)resources.getDimension(R.dimen.playhead_margin_top_not_ok);

        // Prepare the playhead drawable
        mNormalPlayheadDrawable = resources.getDrawable(R.drawable.ic_playhead);
        mMoveOkPlayheadDrawable = resources.getDrawable(R.drawable.playhead_move_ok);
        mMoveNotOkPlayheadDrawable = resources.getDrawable(R.drawable.playhead_move_not_ok);

        /// M: ALPS001374031 9.png image cannot show for this case, need change another png@{
        if (shouldReplacePlayhead(resources)) {
            mNormalPlayheadDrawable = resources.getDrawable(R.drawable.playhead_lowdensity);
        }
        /// @}
    }

    /*
     * M: ALPS001374031 9.png image cannot show for this case, need change another png
     * To cover more cases:
     * maybe density < 1.5, the ic_playhead.9.png cannot show completely.
     * Should not change the tablet for the playhead_lowdensity.png is not enough height
     * Only both small layout and low density return true
     */
    private boolean shouldReplacePlayhead(final Resources resources) {
        boolean smallScreen = false;
        Configuration conf = resources.getConfiguration();
        if (conf.orientation == Configuration.ORIENTATION_PORTRAIT &&
                conf.screenWidthDp <= 360) {// now cover 360dp
            smallScreen = true;
        } else if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                conf.screenHeightDp <= 360) {// add some threadhold
            smallScreen =true;
        }
        boolean lowDensity = false;
        float density = resources.getDisplayMetrics().density;
        if (density < 1.5f) {
            lowDensity = true;
        }
        boolean ret = smallScreen && lowDensity;
        MtkLog.d(TAG, "shouldReplacePlayhead:" + smallScreen + " " + lowDensity + ",ret:" + ret);
        return ret;
    }

    public TimelineHorizontalScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineHorizontalScrollView(Context context) {
        this(context, null, 0);
    }

    /**
     * Invoked to enable/disable user scrolling (as opposed to programmatic scrolling)
     * @param enable true to enable user scrolling
     */
    public void enableUserScrolling(boolean enable) {
        mEnableUserScrolling = enable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        return mScaleDetector.isInProgress() || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnableUserScrolling) {
            mScaleDetector.onTouchEvent(ev);

            if (!mScaleDetector.isInProgress()) {
                setLastEvent(ev);
                return super.onTouchEvent(ev);
            } else {
                return true;
            }
        } else {
            if (mScaleDetector.isInProgress()) {
                final MotionEvent cancelEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
                mScaleDetector.onTouchEvent(cancelEvent);
                cancelEvent.recycle();
            }
            return true;
        }
    }

    /**
     * @param listener The scale listener
     */
    public void setScaleListener(ScaleGestureDetector.SimpleOnScaleGestureListener listener) {
        mScaleDetector = new ScaleGestureDetector(getContext(), listener);
    }

    /**
     * @param listener The listener
     */
    public void addScrollListener(ScrollViewListener listener) {
        mScrollListenerList.add(listener);
    }

    /**
     * @param listener The listener
     */
    public void removeScrollListener(ScrollViewListener listener) {
        mScrollListenerList.remove(listener);
    }

    /**
     * @return true if scrolling is in progress
     */
    public boolean isScrolling() {
        return mIsScrolling;
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll position
     * @param smooth true to scroll smoothly
     */
    public void appScrollTo(int scrollX, boolean smooth) {
        if (getScrollX() == scrollX) {
            return;
        }

        mAppScroll = true;

        if (smooth) {
            smoothScrollTo(scrollX, 0);
        } else {
            scrollTo(scrollX, 0);
        }
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll offset
     * @param smooth true to scroll smoothly
     */
    public void appScrollBy(int scrollX, boolean smooth) {
        mAppScroll = true;

        if (smooth) {
            smoothScrollBy(scrollX, 0);
        } else {
            scrollBy(scrollX, 0);
        }
    }

    @Override
    public void computeScroll() {
        MtkLog.d(TAG, "computeScroll()");
        super.computeScroll();

        final int scrollX = getScrollX();
        if (mLastScrollX != scrollX) {
            mLastScrollX = scrollX;

            // Cancel the previous event
            mHandler.removeCallbacks(mScrollEndedRunnable);

            consumeScrollEndedIfNeed();
            // Post a new event
            mHandler.postDelayed(mScrollEndedRunnable, 300);

            final int scrollY = getScrollY();
            if (mIsScrolling) {
                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollProgress(this, scrollX, scrollY, mAppScroll);
                }
            } else {
                mIsScrolling = true;

                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollBegin(this, scrollX, scrollY, mAppScroll);
                }
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        final int playheadOffset = (Integer)getTag(R.id.playhead_offset);
        final int startX;
        if (playheadOffset < 0) {
            // Draw the playhead in the middle of the screen
            startX = mHalfParentWidth + getScrollX();
        } else {
            // Draw the playhead at the specified position (during trimming)
            startX = playheadOffset;
        }

        final int playheadType = (Integer)getTag(R.id.playhead_type);
        final int halfPlayheadWidth = mNormalPlayheadDrawable.getIntrinsicWidth() / 2;
        switch (playheadType) {
            case PLAYHEAD_NORMAL: {
                // Draw the normal playhead
                mNormalPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTop,
                        startX + halfPlayheadWidth,
                        getHeight() - mPlayheadMarginBottom);
                mNormalPlayheadDrawable.draw(canvas);
                break;
            }

            case PLAYHEAD_MOVE_OK: {
                // Draw the move playhead
                mMoveOkPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTopOk,
                        startX + halfPlayheadWidth,
                        mPlayheadMarginTopOk + mMoveOkPlayheadDrawable.getIntrinsicHeight());
                mMoveOkPlayheadDrawable.draw(canvas);
                break;
            }

            case PLAYHEAD_MOVE_NOT_OK: {
                // Draw the move playhead
                mMoveNotOkPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTopNotOk,
                        startX + halfPlayheadWidth,
                        mPlayheadMarginTopNotOk + mMoveNotOkPlayheadDrawable.getIntrinsicHeight());
                mMoveNotOkPlayheadDrawable.draw(canvas);
                break;
            }

            default: {
                break;
            }
        }
    }
    
    /// M: log info @{
    private static final String TAG = "TimelineHorizontalScrollView";
    private static final boolean LOG = true;
    /// @}
    
    /* M: ALPS00311815 User's scroll action will remove app's scroll action,
     * then, App will show user's final scroll position,
     * but not move to user's position for that onScrollEnd()'s mAppScroll is true.
     * mAppScroll = true means: position is correct, just need to set view.(now is playing)
     * mAppScroll = false means: view is right, just need to set position.(now is not playing, but user move)
     * @{
     */
    private int mLastOnTouchEvent = -1; /// Must assign a default value ALPS00791418
    
    // Only user scroll will call this
    private void setLastEvent(MotionEvent ev) {
        MtkLog.v(TAG, "setLastEvent(" + ev + ")");
        mLastOnTouchEvent = ev.getAction();
    }
    
    private void consumeScrollEndedIfNeed() {
        MtkLog.d(TAG, "consumeScrollEndedIfNeed() mLastOnTouchEvent:" + mLastOnTouchEvent + ", mAppScroll:" + mAppScroll);
        switch(mLastOnTouchEvent) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE: {
            if (mAppScroll) {
                mScrollEndedRunnable.run();
                mAppScroll = false;
                MtkLog.v(TAG, "consumeScrollEndedIfNeed() app scroll is stoped by user touch event.");
            }
            break;
        }
        
        default: {
            break;
        }
        }
            
    }
    /* @} */
}
