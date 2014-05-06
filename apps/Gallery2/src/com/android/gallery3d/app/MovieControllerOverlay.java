/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.CommonControllerOverlay.State;
import com.mediatek.gallery3d.ext.IContrllerOverlayExt;
import com.mediatek.gallery3d.video.IControllerRewindAndForward;
import com.mediatek.gallery3d.video.IControllerRewindAndForward.IRewindAndForwardListener;
import com.mediatek.gallery3d.util.MtkLog;
import com.mediatek.gallery3d.util.MtkUtils;
import com.mediatek.gallery3d.video.ExtensionHelper;
import com.mediatek.gallery3d.video.ScreenModeManager;
import com.mediatek.gallery3d.video.ScreenModeManager.ScreenModeListener;

/**
 * The playback controller for the Movie Player.
 */
public class MovieControllerOverlay extends CommonControllerOverlay implements
        AnimationListener {

    private boolean hidden;

    private final Handler handler;
    private final Runnable startHidingRunnable;
    private final Animation hideAnimation;

    /// M: @{  
    private static final String TAG = "Gallery2/MovieControllerOverlay";
    private static final boolean LOG = true;
    private Context mContext;
    private boolean mInterceptFlag = false;
    /// M: for different display
    private int mControllerButtonPosition;
    private ControllerRewindAndForwardExt mControllerRewindAndForwardExt;
    private ScreenModeExt mScreenModeExt;
    private OverlayExtension mOverlayExt;
    // / M: mtk extension for overlay 
    private ScreenModeManager mScreenModeManager;

    
    // / M: View used to show logo picture from metadata
    private ImageView mLogoView;
    private LogoViewExt mLogoViewExt = new LogoViewExt();

    /// @}

    
    public MovieControllerOverlay(Context context) {
        super(context);
        mContext = context;
        handler = new Handler();
        startHidingRunnable = new Runnable() {
            @Override
            public void run() {
                if(mListener != null && mListener.wfdNeedShowController()) {
                    hide();
                } else {
                    startHiding();
                }
            }
        };

        hideAnimation = AnimationUtils
                .loadAnimation(context, R.anim.player_out);
        hideAnimation.setAnimationListener(this);


    if (ExtensionHelper.getMovieStrategy(context).shouldEnableRewindAndForward()) {

            mControllerRewindAndForwardExt = new ControllerRewindAndForwardExt(context);
        }

        mScreenModeExt = new ScreenModeExt(context);
        mOverlayExt = new OverlayExtension();

        mLogoViewExt.init(context);
        hide();
    }

    public void showPlaying() {
        if (!mOverlayExt.handleShowPlaying()) {
            mState = State.PLAYING;
            showMainView(mPlayPauseReplayView);
        }
        if (LOG) {
            MtkLog.v(TAG, "showPlaying() state=" + mState);
        }
    }

    public void showPaused() {
        if (!mOverlayExt.handleShowPaused()) {
            mState = State.PAUSED;
            showMainView(mPlayPauseReplayView);
        }
        if (LOG) {
            MtkLog.v(TAG, "showPaused() state=" + mState);
        }
    }

    public void showEnded() {
        mOverlayExt.onShowEnded();
        mState = State.ENDED;
        showMainView(mPlayPauseReplayView);
        if (LOG) {
            MtkLog.v(TAG, "showEnded() state=" + mState);
        }
    }

    /**
     * Show loading icon.
     * 
     * @param isHttp Whether the video is a http video or not.
     */
    public void showLoading(boolean isHttp) {
        mOverlayExt.onShowLoading(isHttp);
        mState = State.LOADING;
        showMainView(mLoadingView);
        if (LOG) {
            MtkLog.v(TAG, "showLoading() state=" + mState);
        }
    }

    public void showErrorMessage(String message) {
        mOverlayExt.onShowErrorMessage(message);
        mState = State.ERROR;
        int padding = (int) (getMeasuredWidth() * ERROR_MESSAGE_RELATIVE_PADDING);
        mErrorView.setPadding(padding, mErrorView.getPaddingTop(), padding,
                mErrorView.getPaddingBottom());
        mErrorView.setText(message);
        showMainView(mErrorView);
    }
    
    @Override
    protected void createTimeBar(Context context) {
        mTimeBar = new TimeBar(context, this);
        /// M: set timebar id for test case @{
        int mTimeBarId = 8;
        mTimeBar.setId(mTimeBarId);
        /// @}
    }

    @Override
    public void hide() {
        boolean wasHidden = hidden;
        hidden = true;
     if(mListener != null && !mListener.wfdNeedShowController()) {
	        mPlayPauseReplayView.setVisibility(View.INVISIBLE);
	        mLoadingView.setVisibility(View.INVISIBLE);
	        ///M:pure video only show background
	        if(!mOverlayExt.handleHide()) {
	            setVisibility(View.INVISIBLE);
	        }
	        mBackground.setVisibility(View.INVISIBLE);
	        mTimeBar.setVisibility(View.INVISIBLE);
	        mScreenModeExt.onHide();
	        if (mControllerRewindAndForwardExt != null) {
	            mControllerRewindAndForwardExt.onHide();
	        }
        }
        // /@}
        setFocusable(true);
        requestFocus();
    if (mListener != null && wasHidden != hidden ||
        mListener != null && !showTimeBar) {
        mListener.onHidden();
    }
        if (LOG) {
            MtkLog.v(TAG, "hide() wasHidden=" + wasHidden + ", hidden="
                    + hidden);
        }
    }

    private void showMainView(View view) {
        mMainView = view;
        mErrorView.setVisibility(mMainView == mErrorView ? View.VISIBLE
                : View.INVISIBLE);
        mLoadingView.setVisibility(mMainView == mLoadingView ? View.VISIBLE
                : View.INVISIBLE);
        mPlayPauseReplayView
                .setVisibility(mMainView == mPlayPauseReplayView ? View.VISIBLE
                        : View.INVISIBLE);
        mOverlayExt.onShowMainView();
        show();
    }
    
    @Override
    public void show() {
        boolean wasHidden = hidden;
        hidden = false;
        updateViews();
        setVisibility(View.VISIBLE);
        setFocusable(false);
    if (mListener != null && wasHidden != hidden ||
        mListener != null && !showTimeBar) {
        mListener.onShown();
    }
        maybeStartHiding();
        if (LOG) {
            MtkLog.v(TAG, "show() wasHidden=" + wasHidden + ", hidden="
                    + hidden + ", listener=" + mListener);
        }
    }

    private void maybeStartHiding() {
        cancelHiding();
        if (mState == State.PLAYING) {
            handler.postDelayed(startHidingRunnable, 2500);
        }
        if (LOG) {
            MtkLog.v(TAG, "maybeStartHiding() state=" + mState);
        }
    }

    private void startHiding() {
        startHideAnimation(mBackground);
        startHideAnimation(mTimeBar);
        mScreenModeExt.onStartHiding();
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.onStartHiding();
        }
        startHideAnimation(mPlayPauseReplayView);
    }

    private void startHideAnimation(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.startAnimation(hideAnimation);
        }
    }

    private void cancelHiding() {
        handler.removeCallbacks(startHidingRunnable);
        mBackground.setAnimation(null);
        mTimeBar.setAnimation(null);
        mScreenModeExt.onCancelHiding();
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.onCancelHiding();
        }
        mPlayPauseReplayView.setAnimation(null);
    }

    @Override
    public void onAnimationStart(Animation animation) {
        // Do nothing.
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        // Do nothing.
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        hide();
    }

    public void onClick(View view) {
        if (LOG) {
            MtkLog.v(TAG, "onClick(" + view + ") listener=" + mListener
                    + ", state=" + mState + ", canReplay=" + mCanReplay);
        }
        if (mListener != null) {
            if (view == mPlayPauseReplayView) {
                /// M: when state is retry connecting error, user can replay video 
                if (mState == State.ENDED || mState == State.RETRY_CONNECTING_ERROR) {
                        mListener.onReplay();
                } else if (mState == State.PAUSED || mState == State.PLAYING) {
                    mListener.onPlayPause();
                    //set view disabled (play/pause asynchronous processing)
                    setViewEnabled(false);
                }
            }
        } else {
            mScreenModeExt.onClick(view);
            if (mControllerRewindAndForwardExt != null) {
                mControllerRewindAndForwardExt.onClick(view);
            }
        }
    }

  /*
   * set view enable
   * (non-Javadoc)
   * @see com.android.gallery3d.app.ControllerOverlay#setViewEnabled(boolean)
   */
  @Override
  public void setViewEnabled(boolean isEnabled) {
      if(mListener.onIsRTSP()){
          MtkLog.v(TAG, "setViewEnabled is " + isEnabled);
          mOverlayExt.setCanScrubbing(isEnabled);
          mPlayPauseReplayView.setEnabled(isEnabled);
          if(mControllerRewindAndForwardExt != null){
              mControllerRewindAndForwardExt.setViewEnabled(isEnabled);
          }
      }
  }
  
  /*
   * set play pause button from disable to normal
   * (non-Javadoc)
   * @see com.android.gallery3d.app.ControllerOverlay#setViewEnabled(boolean)
   */
  @Override
  public void setPlayPauseReplayResume(){
      if (mListener.onIsRTSP()) {
          MtkLog.v(TAG, "setPlayPauseReplayResume is enabled is true");
          mPlayPauseReplayView.setEnabled(true);
      }
    }
    
    /**
     * Get time bar enable status
     * @return true is enabled
     * false is otherwise
     */
    public boolean getTimeBarEnabled() {
        return mTimeBar.getScrubbing();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (hidden) {
            show();
        }
        return super.onKeyDown(keyCode, event);
    }

    //if  strereo 3d ui is shown, the touch event should be ignored.
    //for not show controllerOverlay ui.
    public void setInterceptFlag(boolean flag){
        mInterceptFlag = flag;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (super.onTouchEvent(event)) {
            return true;
        }
        //if the flag is true, we will ignore the touch event for 
        //not show controller overlay.
        if(mInterceptFlag) {
            return true;
        }  
        if (hidden) {
            show();
            return true;
        }
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            cancelHiding();
            // you can click play or pause when view is resumed
            // play/pause asynchronous processing
        if ((mState == State.PLAYING || mState == State.PAUSED) && mOverlayExt.mEnableScrubbing) {
            mListener.onPlayPause();
            }
            break;
        case MotionEvent.ACTION_UP:
            maybeStartHiding();
            break;
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = ((MovieActivity)mContext).getWindowManager().getDefaultDisplay().getWidth();
        Rect insets = mWindowInsets;
        int pl = insets.left; // the left paddings
        int pr = insets.right;
        int pt = insets.top;
        int pb = insets.bottom;

        int h = bottom - top;
        int w = right - left;
        boolean error = mErrorView.getVisibility() == View.VISIBLE;

        int y = h - pb;
        // Put both TimeBar and Background just above the bottom system
        // component.
        // But extend the background to the width of the screen, since we don't
        // care if it will be covered by a system component and it looks better.

        // Needed, otherwise the framework will not re-layout in case only the
        // padding is changed
        if(mControllerRewindAndForwardExt != null){
            mBackground.layout(0, y - mTimeBar.getPreferredHeight() - mControllerRewindAndForwardExt.getHeight(), w, y);
            mTimeBar.layout(pl + pr, y - mTimeBar.getPreferredHeight() - mControllerRewindAndForwardExt.getHeight(), w - pr, y - mControllerRewindAndForwardExt.getHeight());
            mControllerRewindAndForwardExt.onLayout(pr, width, y, pr);
        } else {
            mBackground.layout(0, y - mTimeBar.getPreferredHeight(), w, y);
            mTimeBar.layout(pl, y - mTimeBar.getPreferredHeight(), w - pr - mScreenModeExt.getAddedRightPadding(), y);
        }
        mScreenModeExt.onLayout(w, pr, y);
        // Put the play/pause/next/ previous button in the center of the screen
        layoutCenteredView(mPlayPauseReplayView, 0, 0, w, h);

        if (mMainView != null) {
            layoutCenteredView(mMainView, 0, 0, w, h);
        }
    }

    protected void updateViews() {
        if (hidden) {
            return;
        }
        if (showTimeBar) {
            mBackground.setVisibility(View.VISIBLE);
            mTimeBar.setVisibility(View.VISIBLE);
        } else {
            mBackground.setVisibility(View.INVISIBLE);
            mTimeBar.setVisibility(View.INVISIBLE);
        }
        mPlayPauseReplayView.setImageResource(
                mState == State.PAUSED ? R.drawable.videoplayer_play :
                    mState == State.PLAYING ? R.drawable.videoplayer_pause : 
                        R.drawable.videoplayer_reload);
        mScreenModeExt.onShow();
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.onShow();
        }
        if (!mOverlayExt.handleUpdateViews()) {
            mPlayPauseReplayView.setVisibility(
                    (mState != State.LOADING && mState != State.ERROR &&
                    !(mState == State.ENDED && !mCanReplay))
                    ? View.VISIBLE : View.GONE);
        }
        requestLayout();
        if (LOG) {
            MtkLog.v(TAG, "updateViews() state=" + mState + ", canReplay="
                    + mCanReplay);
        }
    }

    // TimeBar listener

    @Override
    public void onScrubbingStart() {
        cancelHiding();
        super.onScrubbingStart();
    }

    @Override
    public void onScrubbingMove(int time) {
        cancelHiding();
        super.onScrubbingMove(time);
    }

    @Override
    public void onScrubbingEnd(int time, int trimStartTime, int trimEndTime) {
        maybeStartHiding();
        super.onScrubbingEnd(time, trimStartTime, trimEndTime);
    }

    

    public void setScreenModeManager(ScreenModeManager manager) {
        mScreenModeManager = manager;
        if (mScreenModeManager != null) {
            mScreenModeManager.addListener(mScreenModeExt);
        }
        if (LOG) {
            MtkLog.v(TAG, "setScreenModeManager(" + manager + ")");
        }
    }

    public IContrllerOverlayExt getOverlayExt() {
        return mOverlayExt;
    }

    public IControllerRewindAndForward getControllerRewindAndForwardExt() {
        return mControllerRewindAndForwardExt;
    }

    private class OverlayExtension implements IContrllerOverlayExt {
        private State mLastState;
        private String mPlayingInfo;
        // The logo picture from metadata
        private Drawable mLogoPic;

        @Override
        public void showBuffering(boolean fullBuffer, int percent) {
            if (LOG) {
                MtkLog.v(TAG, "showBuffering(" + fullBuffer + ", " + percent
                        + ") " + "lastState=" + mLastState + ", state=" + mState);
            }
            if (fullBuffer) {
                // do not show text and loading
                mTimeBar.setSecondaryProgress(percent);
                return;
            }
            if (mState == State.PAUSED || mState == State.PLAYING) {
                mLastState = mState;
            }
            if (percent >= 0 && percent < 100) { // valid value
                mState = State.BUFFERING;
                int msgId = com.mediatek.R.string.media_controller_buffering;
                String text = String.format(getResources().getString(msgId),
                        percent);
                mTimeBar.setInfo(text);
                showMainView(mLoadingView);
            } else if (percent == 100) {
                mState = mLastState;
                mTimeBar.setInfo(null);
                showMainView(mPlayPauseReplayView);// restore play pause state
            } else { // here to restore old state
                mState = mLastState;
                mTimeBar.setInfo(null);
            }
        }

        // set buffer percent to unknown value

        public void clearBuffering() {
            if (LOG) {
                MtkLog.v(TAG, "clearBuffering()");
            }
            mTimeBar.setSecondaryProgress(TimeBar.UNKNOWN);
            showBuffering(false, TimeBar.UNKNOWN);
        }

        public void showReconnecting(int times) {
            clearBuffering();
            mState = State.RETRY_CONNECTING;
            int msgId = R.string.VideoView_error_text_cannot_connect_retry;
            String text = getResources().getString(msgId, times);
            mTimeBar.setInfo(text);
            showMainView(mLoadingView);
            if (LOG) {
                MtkLog.v(TAG, "showReconnecting(" + times + ")");
            }
        }

        public void showReconnectingError() {
            clearBuffering();
            mState = State.RETRY_CONNECTING_ERROR;
            int msgId = com.mediatek.R.string.VideoView_error_text_cannot_connect_to_server;
            String text = getResources().getString(msgId);
            mTimeBar.setInfo(text);
            showMainView(mPlayPauseReplayView);
            if (LOG) {
                MtkLog.v(TAG, "showReconnectingError()");
            }
        }

        public void setPlayingInfo(boolean liveStreaming) {
            int msgId;
            if (liveStreaming) {
                msgId = com.mediatek.R.string.media_controller_live;
            } else {
                msgId = com.mediatek.R.string.media_controller_playing;
            }
            mPlayingInfo = getResources().getString(msgId);
            if (LOG) {
                MtkLog.v(TAG, "setPlayingInfo(" + liveStreaming
                        + ") playingInfo=" + mPlayingInfo);
            }
        }

        // for pause feature
        private boolean mCanPause = true;
        private boolean mEnableScrubbing = false;

        public void setCanPause(boolean canPause) {
            this.mCanPause = canPause;
            if (LOG) {
                MtkLog.v(TAG, "setCanPause(" + canPause + ")");
            }
        }

        public void setCanScrubbing(boolean enable) {
            mEnableScrubbing = enable;
            mTimeBar.setScrubbing(enable);
            if (LOG) {
                MtkLog.v(TAG, "setCanScrubbing(" + enable + ")");
            }
        }
        ///M:for only audio feature.
        private boolean mAlwaysShowBottom;
        public void setBottomPanel(boolean alwaysShow, boolean foreShow) {
            mAlwaysShowBottom = alwaysShow;
            if (!alwaysShow) { // clear background
                setBackgroundDrawable(null);
                setBackgroundColor(Color.TRANSPARENT);
                // Do not show mLogoView when change from audio-only video to
                // A/V video.
                if (mLogoPic != null) {
                    MtkLog.v(TAG, "setBottomPanel() dissmiss orange logo picuture");
                    mLogoPic = null;
                    mLogoView.setImageDrawable(null);
                    mLogoView.setBackgroundColor(Color.TRANSPARENT);
                    mLogoView.setVisibility(View.GONE);
                }
            } else {
                // Don't set the background again when there is a logo picture
                // of the audio-only video
                if (mLogoPic != null) {
                    setBackgroundDrawable(null);
                    mLogoView.setImageDrawable(mLogoPic);
                } else {
                    setBackgroundResource(R.drawable.media_default_bkg);
                }
                if (foreShow) {
                    setVisibility(View.VISIBLE);
                    // show();//show the panel
                    // hide();//hide it for jelly bean doesn't show control when
                    // enter the video.
                }
            }
            if (LOG) {
                MtkLog.v(TAG, "setBottomPanel(" + alwaysShow + ", " + foreShow
                        + ")");
            }
        }
        
        public boolean handleHide() {
            if (LOG) {
                MtkLog.v(TAG, "handleHide() mAlwaysShowBottom" + mAlwaysShowBottom);
            }
            return mAlwaysShowBottom;
        }

        
        /**
         * Set the picture which get from metadata.
         * @param byteArray The picture in byteArray.
         */
        public void setLogoPic(byte[] byteArray) {
            Drawable backgound = MtkUtils.bytesToDrawable(byteArray);
            setBackgroundDrawable(null);
            mLogoView.setBackgroundColor(Color.BLACK);
            mLogoView.setImageDrawable(backgound);
            mLogoView.setVisibility(View.VISIBLE);
            mLogoPic = backgound;
        }

        public boolean isPlayingEnd() {
            if (LOG) {
                MtkLog.v(TAG, "isPlayingEnd() state=" + mState);
            }
            boolean end = false;
            if (State.ENDED == mState || State.ERROR == mState
                    || State.RETRY_CONNECTING_ERROR == mState) {
                end = true;
            }
            return end;
        }

        public boolean handleShowPlaying() {
            if (mState == State.BUFFERING) {
                mLastState = State.PLAYING;
                return true;
            }
            return false;
        }

        public boolean handleShowPaused() {
            mTimeBar.setInfo(null);
            if (mState == State.BUFFERING) {
                mLastState = State.PAUSED;
                return true;
            }
            return false;
        }

        /**
         * Show a information when loading or seeking
         * 
         * @param isHttp Whether the video is a http video or not.
         */
        public void onShowLoading(boolean isHttp) {
            int msgId;
            if (isHttp) {
                msgId = R.string.VideoView_info_buffering;
            } else {
                msgId = com.mediatek.R.string.media_controller_connecting;
            }
            String text = getResources().getString(msgId);
            mTimeBar.setInfo(text);
        }

        public void onShowEnded() {
            clearBuffering();
            mTimeBar.setInfo(null);
        }

        public void onShowErrorMessage(String message) {
            clearBuffering();
        }

        public boolean handleUpdateViews() {
            mPlayPauseReplayView
                    .setVisibility((mState != State.LOADING
                            && mState != State.ERROR
                            &&
                            // !(state == State.ENDED && !canReplay) && //show
                            // end when user stopped it.
                            mState != State.BUFFERING
                            && mState != State.RETRY_CONNECTING && !(mState != State.ENDED
                            && mState != State.RETRY_CONNECTING_ERROR && !mCanPause))
                    // for live streaming
                    ? View.VISIBLE
                            : View.GONE);

            if (mPlayingInfo != null && mState == State.PLAYING) {
                mTimeBar.setInfo(mPlayingInfo);
            }
            return true;
        }

        public void onShowMainView() {
            if (LOG) {
                MtkLog.v(TAG, "onShowMainView() enableScrubbing=" + mEnableScrubbing + ", state="
                        + mState);
            }
            if (mEnableScrubbing
                    && (mState == State.PAUSED || mState == State.PLAYING)) {
                mTimeBar.setScrubbing(true);
            } else {
                mTimeBar.setScrubbing(false);
            }
        }
    }

    class ScreenModeExt implements View.OnClickListener, ScreenModeListener {
        // for screen mode feature
        private ImageView mScreenView;
        private int mScreenPadding;
        private int mScreenWidth;

        private static final int MARGIN = 10; // dip
        private ViewGroup mParent;
        private ImageView mSeprator;

        
        public ScreenModeExt(Context context) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            
            LayoutParams wrapContent =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            //add screenView
            mScreenView = new ImageView(context);
            mScreenView.setImageResource(R.drawable.ic_media_fullscreen);//default next screen mode
            mScreenView.setScaleType(ScaleType.CENTER);
            mScreenView.setFocusable(true);
            mScreenView.setClickable(true);
            mScreenView.setOnClickListener(this);
            addView(mScreenView, wrapContent);
            
            if(mControllerRewindAndForwardExt !=null){
                MtkLog.v(TAG, "ScreenModeExt enableRewindAndForward");
                mSeprator = new ImageView(context);
                mSeprator.setImageResource(R.drawable.ic_separator_line);//default next screen mode
                mSeprator.setScaleType(ScaleType.CENTER);
                mSeprator.setFocusable(true);
                mSeprator.setClickable(true);
                mSeprator.setOnClickListener(this);
                addView(mSeprator, wrapContent);
                
            } else {
                MtkLog.v(TAG, "ScreenModeExt disableRewindAndForward");
            }
            
            //for screen layout
            Bitmap screenButton = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_media_bigscreen);
            mScreenWidth = screenButton.getWidth();
            mScreenPadding = (int) (metrics.density * MARGIN);
            screenButton.recycle();
        }

        private void updateScreenModeDrawable() {
            int screenMode = mScreenModeManager.getNextScreenMode();
            if (screenMode == ScreenModeManager.SCREENMODE_BIGSCREEN) {
                mScreenView.setImageResource(R.drawable.ic_media_bigscreen);
            } else if (screenMode == ScreenModeManager.SCREENMODE_FULLSCREEN) {
                mScreenView.setImageResource(R.drawable.ic_media_fullscreen);
            } else {
                mScreenView.setImageResource(R.drawable.ic_media_cropscreen);
            }
        }

        @Override
        public void onClick(View v) {
            if (v == mScreenView && mScreenModeManager != null) {
                mScreenModeManager.setScreenMode(mScreenModeManager
                        .getNextScreenMode());
                show();// show it?
            }
        }

        public void onStartHiding() {
            startHideAnimation(mScreenView);
        }

        public void onCancelHiding() {
            mScreenView.setAnimation(null);
        }

        public void onHide() {
            mScreenView.setVisibility(View.INVISIBLE);
            if(mControllerRewindAndForwardExt != null){
                mSeprator.setVisibility(View.INVISIBLE);
            }
        }
        public void onShow() {
            if (showTimeBar) {
                mScreenView.setVisibility(View.VISIBLE);
            } else {
                onHide();
            }
            
            if(mControllerRewindAndForwardExt != null){
                mSeprator.setVisibility(View.VISIBLE);
            }
        }
        public void onLayout(int width, int paddingRight, int yPosition) {
            // layout screen view position
            int sw = getAddedRightPadding();
            int sepratorPosition = (width - paddingRight - sw - mControllerButtonPosition) / 2 + mControllerButtonPosition;
            int sepratorWidth = 2;
            if(mControllerRewindAndForwardExt != null){
                mScreenView.layout(width - paddingRight - sw, yPosition
                        - mControllerRewindAndForwardExt.getHeight(), width - paddingRight,
                        yPosition);
            } else {
               mScreenView.layout(width - paddingRight - sw, yPosition
                    - mTimeBar.getBarHeight(), width - paddingRight,
                    yPosition);
            }
            if(mControllerRewindAndForwardExt != null){
                mSeprator.layout(sepratorPosition , yPosition
                        - mControllerRewindAndForwardExt.getHeight(), sepratorPosition + sepratorWidth,
                        yPosition);
            }
        }

        public int getAddedRightPadding() {
            return mScreenPadding * 2 + mScreenWidth;
        }

        @Override
        public void onScreenModeChanged(int newMode) {
            updateScreenModeDrawable();
        }
    }

    // / @}
    //for stereo feature
    private boolean showTimeBar = true;
    public void displayTimeBar(boolean display) {
        showTimeBar = display;
    }

    class ControllerRewindAndForwardExt implements View.OnClickListener, IControllerRewindAndForward{
        //for screen mode feature
        private LinearLayout mContollerButtons;
        private ImageView mStop;
        private ImageView mForward;
        private ImageView mRewind;
        private IRewindAndForwardListener mListenerForRewind;
        private int mButtonWidth;
        private int mButtonHeight;
        private static final int BUTTON_PADDING = 40;
        private int mTimeBarHeight = 0;
        
        public ControllerRewindAndForwardExt(Context context) {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt init");
            mTimeBarHeight = mTimeBar.getBarHeight();
            Bitmap button = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_menu_forward);
            mButtonWidth = button.getWidth();
            mButtonHeight = button.getHeight();
            button.recycle();
            
            mContollerButtons = new LinearLayout(context);
            mContollerButtons.setHorizontalGravity(LinearLayout.HORIZONTAL);
            mContollerButtons.setVisibility(View.VISIBLE);
            mContollerButtons.setGravity(Gravity.CENTER);
            
            LinearLayout.LayoutParams buttonParam = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mRewind = new ImageView(context);
            mRewind.setImageResource(R.drawable.icn_media_rewind);
            mRewind.setScaleType(ScaleType.CENTER);
            mRewind.setFocusable(true);
            mRewind.setClickable(true);
            mRewind.setOnClickListener(this);
            mContollerButtons.addView(mRewind, buttonParam);
            
            mStop = new ImageView(context);
            mStop.setImageResource(R.drawable.icn_media_stop);
            mStop.setScaleType(ScaleType.CENTER);
            mStop.setFocusable(true);
            mStop.setClickable(true);
            mStop.setOnClickListener(this);
            LinearLayout.LayoutParams stopLayoutParam = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stopLayoutParam.setMargins(BUTTON_PADDING, 0, BUTTON_PADDING, 0);
            mContollerButtons.addView(mStop, stopLayoutParam);
            
            mForward = new ImageView(context);
            mForward.setImageResource(R.drawable.icn_media_forward);
            mForward.setScaleType(ScaleType.CENTER);
            mForward.setFocusable(true);
            mForward.setClickable(true);
            mForward.setOnClickListener(this);
            mContollerButtons.addView(mForward, buttonParam);
            
            LinearLayout.LayoutParams wrapContent = new LinearLayout.LayoutParams(
                  getAddedRightPadding(), ViewGroup.LayoutParams.WRAP_CONTENT);
            addView(mContollerButtons, wrapContent);
        }

        @Override
        public void onClick(View v) {
            if (v == mStop) {
                MtkLog.v(TAG, "ControllerRewindAndForwardExt onClick mStop");
                mListenerForRewind.onStopVideo();
            } else if (v == mRewind) {
                MtkLog.v(TAG, "ControllerRewindAndForwardExt onClick mRewind");
                mListenerForRewind.onRewind();
            } else if (v == mForward) {
                MtkLog.v(TAG, "ControllerRewindAndForwardExt onClick mForward");
                mListenerForRewind.onForward();
            }
        }

        public void onStartHiding() {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt onStartHiding");
            startHideAnimation(mContollerButtons);
        }

        public void onCancelHiding() {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt onCancelHiding");
            mContollerButtons.setAnimation(null);
        }

        public void onHide() {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt onHide");
            mContollerButtons.setVisibility(View.INVISIBLE);
        }

        public void onShow() {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt onShow");
            mContollerButtons.setVisibility(View.VISIBLE);
        }

        public void onLayout(int l, int r, int b, int pr) {
            MtkLog.v(TAG, "ControllerRewindAndForwardExt onLayout");
            int cl = (r - l - getAddedRightPadding()) / 2;
            int cr = cl + getAddedRightPadding();
            mControllerButtonPosition = cr + pr;
            mContollerButtons.layout(cl + pr, b - mButtonHeight, cr + pr, b);
        }
        
        public int getHeight() {
            return mButtonHeight;
        }
        
        public int getAddedRightPadding() {
            return mButtonWidth * 3 + BUTTON_PADDING * 2;
        }
        @Override
        public void setIListener(IRewindAndForwardListener listener){
            MtkLog.v(TAG, "ControllerRewindAndForwardExt setIListener " + listener);
            mListenerForRewind = listener;
        }
        
        @Override
        public void showControllerButtonsView(boolean canStop, boolean canRewind, boolean canForward){
            MtkLog.v(TAG, "ControllerRewindAndForwardExt showControllerButtonsView " + canStop + canRewind + canForward);
            // show ui
            mStop.setEnabled(canStop);
            mRewind.setEnabled(canRewind);
            mForward.setEnabled(canForward);
        }

        @Override
        public void setListener(Listener listener) {
            setListener(listener);
        }
        @Override
        public boolean getPlayPauseEanbled() {
            return mPlayPauseReplayView.isEnabled();
        }
        
        @Override
        public boolean getTimeBarEanbled() {
            return mTimeBar.getScrubbing();
        }

        @Override
        public void setCanReplay(boolean canReplay) {
            setCanReplay(canReplay);
        }

        @Override
        public View getView() {
            return mContollerButtons;
        }

        @Override
        public void show() {
            show();
        }

        @Override
        public void showPlaying() {
            showPlaying();
        }

        @Override
        public void showPaused() {
            showPaused();
        }

        @Override
        public void showEnded() {
            showEnded();
        }

        @Override
        public void showLoading() {
            showLoading();
        }

        @Override
        public void showErrorMessage(String message) {
            showErrorMessage(message);
        }

        public void setTimes(int currentTime, int totalTime ,int trimStartTime, int trimEndTime) {
            setTimes(currentTime, totalTime,0,0);
        }

        @Override
        public void setPlayPauseReplayResume() {
        }

        @Override
        public void setViewEnabled(boolean isEnabled) {
            // TODO Auto-generated method stub
            MtkLog.v(TAG, "ControllerRewindAndForwardExt setViewEnabled is " + isEnabled);
            mRewind.setEnabled(isEnabled);
            mForward.setEnabled(isEnabled);
        }
        @Override
        public void displayTimeBar(boolean display) {

        }
    }
    /// @}
    
    // /M:Add LogoView for audio-only video.
    class LogoViewExt {
        private void init(Context context) {
            // Add logo picture
            RelativeLayout movieView =
                    (RelativeLayout) ((MovieActivity) mContext).findViewById(R.id.movie_view_root);
            FrameLayout.LayoutParams matchParent =
                    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT, Gravity.CENTER);
            mLogoView = new ImageView(mContext);
            mLogoView.setAdjustViewBounds(true);
            mLogoView.setMaxWidth(((MovieActivity) mContext).getWindowManager().getDefaultDisplay()
                    .getWidth());
            mLogoView.setMaxHeight(((MovieActivity) mContext).getWindowManager()
                    .getDefaultDisplay().getHeight());
            movieView.addView(mLogoView, matchParent);
            mLogoView.setVisibility(View.GONE);
        }
    }
    // / @}
}
