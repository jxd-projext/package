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

package com.android.gallery3d.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.widget.Toast;

import com.android.gallery3d.app.PhotoDataAdapter.MavListener;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.R;
import com.android.gallery3d.ui.BitmapScreenNail;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

// M: mtk import
import com.android.gallery3d.data.MediaObject;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.Display;

import com.mediatek.gallery3d.gif.GifDecoderWrapper;
import com.mediatek.gallery3d.mpo.MavRenderThread;
import com.mediatek.gallery3d.mpo.MavSeekBar;
import com.mediatek.gallery3d.panorama.PanoramaConfig;
import com.mediatek.gallery3d.panorama.PanoramaHelper;
import com.mediatek.gallery3d.panorama.PanoramaScreenNail;
import com.mediatek.gallery3d.stereo.StereoHelper;
import com.mediatek.gallery3d.ui.MtkBitmapScreenNail;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;
import com.mediatek.gallery3d.util.MediatekFeature.DataBundle;
import com.mediatek.gallery3d.util.MediatekFeature.Params;

public class SinglePhotoDataAdapter extends TileImageViewAdapter
        implements PhotoPage.Model, MavRenderThread.OnDrawMavFrameListener {

    private static final String TAG = "Gallery2/SinglePhotoDataAdapter";
    private static final int SIZE_BACKUP = 1024;
    private static final int MSG_UPDATE_IMAGE = 1;

    //added for Mediatek feature
    private static final int MSG_RUN_OBJECT = 2;
    private static final int MSG_UPDATE_SECOND_IMAGE = 3;
    private static final int MSG_UPDATE_LARGE_IMAGE = 4;
    private static final int MSG_UPDATE_MAV_FRAME = 5;
    // M: 6592 panorama add @{
    private static final int MSG_UPDATE_PANORAMA = 6;
    // @}
    private static final boolean mIsGifAnimationSupported = 
                            MediatekFeature.isGifAnimationSupported();
    private static final boolean mIsStereoDisplaySupported = 
                                          MediatekFeature.isStereoDisplaySupported();
    private static final boolean mIsMavSupported = 
                            MediatekFeature.isMAVSupported();
    // M: 6592 panorama add @{
    private static final boolean mIsPanorama3DSupported = MediatekFeature.isPanorama3DSupported();
    // @}
    private boolean mIsActive = false;

    // Mediatek patch to improve Display image performance:
    // As there is a chance that the UriImage is indeed a very large JPEG
    // image capture by digital camera, for example, 3500x2333, view this
    // kind image will cost a lot of time. 
    // This process is time consuming is caused by the following two reason:
    // 1, creating a RegionDecoder will cause about 300ms on a 1G Hz single
    //    core device.
    // 2, region decoding by RegionDecoder is via SW, no hardware is used.
    //
    // The solution is firstly decode a small Bitmap by Bitmap factory to
    // make UI displays fast, then follow orginal routin to decode a large
    // bitmap by region decoder.
    // BitmapFactory will returns a bitmap sooner because:
    // 1, there is no need to construct a decoder object.
    // 2, there is a chance to use HW acceleration
    // 
    // This solution will shorten black screen duration, but complext program
    // structure.

    private static final boolean mShowThumbFirst = true;
    private boolean mShowedThumb;

    //Stereo display feature
    //Stereo display requires that first and second frame 
    private boolean mShowStereoImage;
    //added for Mediatek feature end

    private MediaItem mItem;
    private boolean mHasFullImage;
    private Future<?> mTask;
    private Handler mHandler;

    private PhotoView mPhotoView;
    private ThreadPool mThreadPool;
    private int mLoadingState = LOADING_INIT;
    private BitmapScreenNail mBitmapScreenNail;
    
    /// M: added for mav playback
    private MavListener mMavListener;
    private boolean mIsMavLoadingFinished;
    private boolean mIsMavLoadingSuccess;
    private static final int TYPE_LOAD_TOTAL_COUNT = 0; 
    private static final int TYPE_LOAD_FRAME = 1; 
    private ScreenNail[] mpoFrames;
    private int mpoTotalCount;
    private MavRenderThread mMavRenderThread = null;
    private final AbstractGalleryActivity mActivity;
    // M: 6592 panorama add @{
    private Future<ScreenNail> mPanoramaTask;
    private boolean mPanoramaTaskSubmit = false;
    // @}

    public SinglePhotoDataAdapter(
            AbstractGalleryActivity activity, PhotoView view, MediaItem item) {
        mItem = Utils.checkNotNull(item);
        mActivity = activity;
        
        mHasFullImage = (item.getSupportedOperations() &
                MediaItem.SUPPORT_FULL_IMAGE) != 0;
        
        // M: added for gif animation
        if (mIsGifAnimationSupported && 
            (item.getSupportedOperations() & 
             MediaItem.SUPPORT_GIF_ANIMATION) != 0) {
            mAnimateGif = true;
        } else {
            mAnimateGif = false;
        }
        
        // M: added for mav palyback
        if (mIsMavSupported && 
                (item.getSupportedOperations() & MediaItem.SUPPORT_MAV_PLAYBACK) != 0) {
            mMavPlayback = true;
        } else {
            mMavPlayback = false;
        }
        
        //added for stereo display
        mShowStereoImage = mIsStereoDisplaySupported &&
                ((mItem.getSupportedOperations() &
                 MediaItem.SUPPORT_STEREO_DISPLAY) != 0);
        Log.i(TAG,TAG+":mShowStereoImage="+mShowStereoImage);
        mPhotoView = Utils.checkNotNull(view);
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            @SuppressWarnings("unchecked")
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_IMAGE ||
                                 message.what == MSG_RUN_OBJECT ||
                                 message.what == MSG_UPDATE_SECOND_IMAGE ||
                                 message.what == MSG_UPDATE_LARGE_IMAGE ||
                                 message.what == MSG_UPDATE_MAV_FRAME ||
                                 message.what == MSG_UPDATE_PANORAMA); //M: 6592 panorama add
                switch (message.what) {
                    case MSG_UPDATE_IMAGE:
                        onDecodeThumbComplete((Future<Bitmap>) message.obj);
                        return;
                    case MSG_RUN_OBJECT: {
                        ((Runnable) message.obj).run();
                        return;
                    }
                    case MSG_UPDATE_SECOND_IMAGE: {
                        onDecodeSecondThumbComplete(
                             (Future<MediatekFeature.DataBundle>) message.obj);
                        return;
                    }
                    case MSG_UPDATE_LARGE_IMAGE: {
                        onDecodeLargeComplete((ImageBundle) message.obj);
                        return;
                    }
                    case MSG_UPDATE_MAV_FRAME: {
                        updateMavcontent(message.arg1);
                        mMavListener.setProgress(message.arg1);
                        return;
                    }
                    // M: 6592 panorama add @{
                    case MSG_UPDATE_PANORAMA: {
                        onDecodePanoramaComplete((Future<ScreenNail>) message.obj);
                        return;
                    }
                    // @}
                    default: throw new AssertionError();
                }
            }
        };
        mThreadPool = activity.getThreadPool();
    }
    ///M:
    public void setSourceAndItem(MediaSet mediaSet, Path itemPath) {
    }

    private static class ImageBundle {
        public final BitmapRegionDecoder decoder;
        public final Bitmap backupImage;

        public ImageBundle(BitmapRegionDecoder decoder, Bitmap backupImage) {
            this.decoder = decoder;
            this.backupImage = backupImage;
        }
    }

    private FutureListener<BitmapRegionDecoder> mLargeListener =
            new FutureListener<BitmapRegionDecoder>() {
        @Override
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            BitmapRegionDecoder decoder = future.get();
            if (decoder == null) return;
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            
            if((mLoadingState == LOADING_FAIL)
                    && MediatekFeature.isOutOfLimitation(mItem.getMimeType(), width, height)) {
                MtkLog.d(TAG, String.format("out of limitation: %s [mime type: %s, width: %d, height: %d]", 
                        mItem.getPath().toString(), mItem.getMimeType(), width, height));
                decoder.recycle();
                return;
            }

            width = StereoHelper.adjustDim(true, mItem.getStereoLayout(), width);
            height = StereoHelper.adjustDim(false, mItem.getStereoLayout(), height);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.computeSampleSize(
                    (float) SIZE_BACKUP / Math.max(width, height));

            // M: for picture quality enhancement
            MediatekFeature.enablePictureQualityEnhance(options, true);

            Bitmap bitmap = decoder.decodeRegion(new Rect(0, 0, width, height), options);
            mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_UPDATE_LARGE_IMAGE, new ImageBundle(decoder, bitmap)));
        }
    };

    private FutureListener<Bitmap> mThumbListener =
            new FutureListener<Bitmap>() {
        @Override
        public void onFutureDone(Future<Bitmap> future) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_UPDATE_IMAGE, future));
        }
    };

    private FutureListener<MediatekFeature.DataBundle> mSecondThumbListener =
            new FutureListener<MediatekFeature.DataBundle>() {
        public void onFutureDone(Future<MediatekFeature.DataBundle> future) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_UPDATE_SECOND_IMAGE, future));
        }
    };
    @Override
    public boolean isEmpty() {
        return false;
    }

    /*private void setScreenNail(Bitmap bitmap, int width, int height) {
        mBitmapScreenNail = new BitmapScreenNail(bitmap);
        setScreenNail(mBitmapScreenNail, width, height);
    }*/

    private void onDecodeLargeComplete(ImageBundle bundle) {
        try {
            //setScreenNail(bundle.backupImage,
            //        bundle.decoder.getWidth(), bundle.decoder.getHeight());
            //setRegionDecoder(bundle.decoder);

            //adjust full image dimesion if needed
            int fullWidth = bundle.decoder.getWidth();
            int fullHeight = bundle.decoder.getHeight();
            fullWidth = StereoHelper.adjustDim(true, mItem.getStereoLayout(),
                                               fullWidth);
            fullHeight = StereoHelper.adjustDim(false, mItem.getStereoLayout(),
                                                fullHeight);

            ScreenNail screenNail = 
                MediatekFeature.getMtkScreenNail(mItem, bundle.backupImage);
            if (null != screenNail) {
                setRegionDecoder(bundle.decoder, screenNail, fullWidth, fullHeight);
            } else {
                setRegionDecoder(bundle.decoder, bundle.backupImage,
                                 fullWidth, fullHeight);
            }
            mPhotoView.notifyImageChange(0);
            // M: 6592 panorama add @{
            if (mIsPanorama3DSupported && isPanorama(0) && mLoadingState != LOADING_FAIL
                    && !mPanoramaTaskSubmit) {
                Params params = new Params();
                params.inOriginalFrame = true;
                params.inOriginalTargetSize = Params.THUMBNAIL_TARGET_SIZE_PANORAMA;
                MediatekFeature.enablePictureQualityEnhance(params, true);
                mPanoramaTask = mThreadPool.submit(new PanoramaScreenNailJob(mItem, params),
                        mPanoramaListener);
                mPanoramaTaskSubmit = true;
            }
            // @}
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode large", t);
        }
    }

    private void onDecodeThumbComplete(Future<Bitmap> future) {
        try {
            Bitmap backup = future.get();
            if (backup == null) {
                mLoadingState = LOADING_FAIL;
                return;
            } else {
                mLoadingState = LOADING_COMPLETE;
            }
            ScreenNail screenNail = MediatekFeature.getMtkScreenNail(mItem, backup);
            if (null != screenNail) {
                int width = 0;
                int height = 0;
                PhotoView.Size size = MediatekFeature.getSizeForSubtype(screenNail);
                if (size != null) {
                    width = size.width;
                    height = size.height;
                } else {
                    width = screenNail.getWidth();
                    height = screenNail.getHeight();
                }
                setScreenNail(screenNail, width, height);
            } else {
                screenNail = new BitmapScreenNail(backup);
                setScreenNail(screenNail, screenNail.getWidth(), screenNail.getHeight());
            }
            //setScreenNail(backup, backup.getWidth(), backup.getHeight());
            mPhotoView.notifyImageChange(0);

            //decode second image for stereo display
            if (mShowStereoImage) {
                //create mediatek parameters
                MediatekFeature.Params params = new MediatekFeature.Params();
                params.inOriginalFrame = false;
                params.inFirstFrame = true;//we decode the first frame if possible
                params.inSecondFrame = true;
                MediatekFeature.enablePictureQualityEnhance(params, true);
                Log.i(TAG,"onDecodeThumbComplete:start second image task");
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL, params),
                        mSecondThumbListener);
                return;
            }

            // M: if decode thumbnail fail, there is no need to decode full image
            if (mShowThumbFirst && mHasFullImage && mLoadingState != LOADING_FAIL) {
                //after showed thumbnail, change status
                mShowedThumb = true;
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            }
            // M: 6592 panorama add @{
            if (mIsPanorama3DSupported && isPanorama(0) && mLoadingState != LOADING_FAIL
                    && !mPanoramaTaskSubmit) {
                Params params = new Params();
                params.inOriginalFrame = true;
                params.inOriginalTargetSize = Params.THUMBNAIL_TARGET_SIZE_PANORAMA;
                MediatekFeature.enablePictureQualityEnhance(params, true);
                mPanoramaTask = mThreadPool.submit(new PanoramaScreenNailJob(mItem, params),
                        mPanoramaListener);
                mPanoramaTaskSubmit = true;
            }
            // @}

        } catch (Throwable t) {
            Log.w(TAG, "fail to decode thumb", t);
        }
    }

    private void onDecodeSecondThumbComplete(
                     Future<MediatekFeature.DataBundle> future) {
        try {
            Bitmap second = future.get() != null ?
                            future.get().secondFrame : null;
            Log.i(TAG,"onDecodeSecondThumbComplete:second="+second);
            if (second == null) return;

            //as we have already made sure that second screen
            //nail has the same dimension as screen nail, only
            //bitmap is needed to set
            setStereoScreenNail(
                StereoHelper.STEREO_INDEX_FIRST, future.get().firstFrame);
            setStereoScreenNail(
                StereoHelper.STEREO_INDEX_SECOND, second);

            mPhotoView.notifyImageChange(0);

            //the 2d to 3d picture should not enter stereo mode.
            //Enter stereo mode only when stereo photo is encountered
            resetStereoMode();
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode thumb", t);
        } finally {
            // M: if decode thumbnail fail, there is no need to decode full image
            if (mShowThumbFirst && mHasFullImage && mLoadingState != LOADING_FAIL) {
                //after showed thumbnail, change status
                mShowedThumb = true;
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            }
        }
    }

    private void resetStereoMode() {
        if (!mShowStereoImage) return;

        boolean stereoMode = false;
        if (null != getStereoScreenNail(2,mPhotoView.getAcEnabled())) {
            //if stereo pair is loaded, we can enter stereo mode
            stereoMode = true;
        }

        int stereoLayout = stereoMode ?
                StereoHelper.STEREO_TYPE_SIDE_BY_SIDE :
                StereoHelper.STEREO_TYPE_2D;
         mPhotoView.setStereoLayout(stereoLayout);
       
        if (stereoMode && (mItem.getSupportedOperations() & 
            MediaItem.SUPPORT_CONVERT_TO_3D) != 0) {
            //for 2d to 3d image, we enter normal mode by default
            stereoMode = false;
        }

        Log.v(TAG,"resetStereoMode:stereoMode=" + stereoMode);
        mPhotoView.setStereoMode(stereoMode, stereoMode);
    }

    public void resume() {
        mIsActive = true;

        if (mTask == null || mMavPlayback) {
            //when resume, first show thumbnail
            mShowedThumb = false;

            // M: if decode thumbnail fail, there is no need to decode full image
            if (!mShowThumbFirst && mHasFullImage && mLoadingState != LOADING_FAIL) {
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            } else {
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
            }
        // M: 6592 panorama add @{
        } else if (mTask != null) {
            if (mIsPanorama3DSupported && isPanorama(0) && mLoadingState != LOADING_FAIL && !mPanoramaTaskSubmit) {
                Params params = new Params();
                params.inOriginalFrame = true;
                params.inOriginalTargetSize = Params.THUMBNAIL_TARGET_SIZE_PANORAMA;
                MediatekFeature.enablePictureQualityEnhance(params, true);
                mPanoramaTask = mThreadPool.submit(new PanoramaScreenNailJob(mItem, params),
                        mPanoramaListener);
                mPanoramaTaskSubmit = true;
            }
        }
        // @}

        // M: added for gif animation
        if (mIsGifAnimationSupported && null == mGifTask) {
            if (mAnimateGif) {
                //create mediatek parameters
                MediatekFeature.Params params = new MediatekFeature.Params();
                params.inOriginalFrame = false;
                params.inGifDecoder = true;//we want gif decoder
                Log.i(TAG,"resume:start GifDecoder task");
                mGifTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL, params),
                        new GifDecoderListener());
            }
        }
        
        if (mIsMavSupported) {
            if(mMavPlayback) {
                MtkLog.d(TAG, "create mav decoder task");
    
                // create mediatek parameters
                // only set inMpoTotalCount as true
                // first get mpo's total count
                // because we need set it to MavSeekBar for update progress 
                Params params = new Params();
                params.inMpoTotalCount = true;
                
                MtkLog.d(TAG, "get mav total count");
                mIsMavLoadingFinished = false;
                mPhotoView.setMavLoadingFinished(mIsMavLoadingFinished);
                
                mMpoDecoderTask = mThreadPool.submit(mItem.requestImage(
                        MediaItem.TYPE_THUMBNAIL, params), new MavDecoderListener(mItem, TYPE_LOAD_TOTAL_COUNT));
            }
        }

        //reset stereo mode
        resetStereoMode();
    }
    @Override
    public void pause() {
        mIsActive = false;

        Future<?> task = mTask;
        task.cancel();
        task.waitDone();
        if (task.get() == null) {
            mTask = null;
        }
        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
        // M: cancel GIF task
        task = mGifTask;
        if (null != task) {
            task.cancel();
            task.waitDone();
        }
        mGifTask = null;
        
        /// M: cancel mav task
        if (null != mMpoDecoderTask) {
            mMpoDecoderTask.cancel();
            mMpoDecoderTask.waitDone();
        }
        if(mpoFrames != null) {
            int length = mpoFrames.length;
            for(int idx = 0; idx < length; idx++) {
                if(mpoFrames[idx] != null) {
                    mpoFrames[idx].recycle();
                    mpoFrames[idx] = null;
                }
            }
            mpoFrames = null;
        }
        // M: 6592 panorama add @{
        if (null != mPanoramaTask) {
            mPanoramaTask.cancel();
            mPanoramaTask.waitDone();
            mPanoramaTask = null;
        }
        mPanoramaTaskSubmit = false;
        if (mPanoramaScreenNail !=  null) {
            mPanoramaScreenNail.recycle();
            mPanoramaScreenNail = null;
        }
        // @}
    }

    @Override
    public void moveTo(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getImageSize(int offset, PhotoView.Size size) {
        if (offset == 0) {
            size.width = mItem.getWidth();
            size.height = mItem.getHeight();
        } else {
            size.width = 0;
            size.height = 0;
        }
    }

    @Override
    public int getImageRotation(int offset) {
        return (offset == 0) ? mItem.getFullImageRotation() : 0;
    }

    @Override
    public ScreenNail getScreenNail(int offset) {
        return (offset == 0) ? getScreenNail() : null;
    }

    // M: 6592 panorama add @{
    @Override
    public ScreenNail getPanoramaScreenNail(int offset) {
        if (!mIsPanorama3DSupported && offset != 0) {
            return null;
        }
        if (mPanoramaScreenNail == null) {
            mPanoramaScreenNail = PanoramaHelper.newPlaceholderPanoramaScreenNail(mItem, 
                    mActivity.getResources().getColor(R.color.photo_placeholder));
        }
        return mPanoramaScreenNail;
    }
    // @}
    
    @Override
    public void setNeedFullImage(boolean enabled) {
        // currently not necessary.
    }

    @Override
    public boolean isCamera(int offset) {
        return false;
    }

    @Override
    public boolean isPanorama(int offset) {
        // M: 6592 panorama add @{
        if (mIsPanorama3DSupported && mItem != null && offset == 0) {
            return mItem.isPanorama();
        }
        // @}
        return false;
    }

    @Override
    public boolean isStaticCamera(int offset) {
        return false;
    }

    @Override
    public boolean isVideo(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO;
    }

    @Override
    public boolean isDeletable(int offset) {
        //return (mItem.getSupportedOperations() & MediaItem.SUPPORT_DELETE) != 0;
        // M: item in SinglePhotoDataAdapter should always be undeletable,
        // since it does not have a containing set, and might cause JE
        // when deleting it in film mode.
        return false;
    }

    @Override
    public MediaItem getMediaItem(int offset) {
        return offset == 0 ? mItem : null;
    }

    @Override
    public int getCurrentIndex() {
        return 0;
    }

    @Override
    public void setCurrentPhoto(Path path, int indexHint) {
        // ignore
    }

    @Override
    public void setFocusHintDirection(int direction) {
        // ignore
    }

    @Override
    public void setFocusHintPath(Path path) {
        // ignore
    }

    @Override
    public int getLoadingState(int offset) {
        return mLoadingState;
    }

    public void enterConsumeMode() {
        return;//temporarily do nothing
    }

    public boolean enteredConsumeMode() {
        return false;
    }

    public void triggerStereoFullImage() {
        return;
    }

    // M: following code are added for gif
    private boolean mAnimateGif;
    private Future<?> mGifTask;
    private GifDecoderWrapper mGifDecoder;
    private Bitmap mCurrentGifFrame;
    private int mCurrentFrameNum;
    private int mTotalFrameCount;
    /// M: current file is mpo, and support mav playback
    private boolean mMavPlayback;
    private Future<DataBundle> mMpoDecoderTask;
    
    private class GifDecoderListener
            implements Runnable, FutureListener<MediatekFeature.DataBundle> {
        private Future<MediatekFeature.DataBundle> mFuture;

        public GifDecoderListener() {}

        @Override
        public void onFutureDone(Future<MediatekFeature.DataBundle> future) {
            mFuture = future;
            if (MediatekFeature.isGifSupported() && null != mFuture.get()) {
                mHandler.sendMessage(
                        mHandler.obtainMessage(MSG_RUN_OBJECT, this));
            }
        }

        @Override
        public void run() {
            startGifAnimation(mFuture);
        }
    }

    private void startGifAnimation(Future<MediatekFeature.DataBundle> future) {
        mGifTask = null;
        mGifDecoder = future.get().gifDecoder;
        if (mGifDecoder != null) {
            //prepare Gif animation state
            mCurrentFrameNum = 0;
            mTotalFrameCount = mGifDecoder.getTotalFrameCount();
            if (mTotalFrameCount <= 1) {
                Log.w("SinglePhotoDataAdapter","invalid frame count, NO animation!");
                return;
            }
            //create gif frame bitmap
            mCurrentGifFrame = Bitmap.createBitmap(mGifDecoder.getWidth(), 
                                                   mGifDecoder.getHeight(),
                                                   Bitmap.Config.ARGB_8888);
            Utils.assertTrue(null != mCurrentGifFrame);
            //start GIF animation
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_RUN_OBJECT, 
                                     new GifAnimationRunnable()));
        }
    }

    private class GifAnimationRunnable implements Runnable {
        public GifAnimationRunnable() {
        }

        @Override
        public void run() {
            if (!mIsActive) {
                Log.i("SinglePhotoDataAdapter","GifAnimationRunnable:run:already paused");
                //releaseGifResource();
                return;
            }

            if (null == mGifDecoder) {
                Log.e("SinglePhotoDataAdapter","GifAnimationRunnable:run:invalid GifDecoder");
                //releaseGifResource();
                return;
            }

            long preTime = SystemClock.uptimeMillis();

            //assign decoded bitmap to CurrentGifFrame
            Bitmap curBitmap = mGifDecoder.getFrameBitmap(mCurrentFrameNum);
            if (null == curBitmap) {
                Log.e("SinglePhotoDataAdapter","GifAnimationRunnable:onFutureDone:got null frame!");
                //releaseGifResource();
                return;
            }

            //get curent frame duration
            long curDuration = mGifDecoder.getFrameDuration(mCurrentFrameNum);
            //calculate next frame index
            mCurrentFrameNum = (mCurrentFrameNum + 1) % mTotalFrameCount;

            //update Current Gif Frame
            Canvas canvas = new Canvas(mCurrentGifFrame);
            canvas.drawColor(MediatekFeature.getGifBackgroundColor());
            Matrix m = new Matrix();
            canvas.drawBitmap(curBitmap,m,null);
            
            curBitmap.recycle();

            updateGifFrame(mCurrentGifFrame);

            mHandler.sendMessageAtTime(
                    mHandler.obtainMessage(MSG_RUN_OBJECT, this), (curDuration+preTime));
        }
    }

    private void updateGifFrame(Bitmap gifFrame) {
        if (gifFrame == null) return;

        ScreenNail screenNail = MediatekFeature.getMtkScreenNail(mItem, gifFrame);
        if (null != screenNail) {
            if (mScreenNail != null) {
                mScreenNail.recycle();
            }
            setScreenNail(screenNail, screenNail.getWidth(),
                                      screenNail.getHeight());
        } else {
            setScreenNail(gifFrame, gifFrame.getWidth(), gifFrame.getHeight());
        }
        mPhotoView.notifyImageChange(0); // the current image
    }

    private void releaseGifResource() {
        mGifDecoder = null;
        if (null != mCurrentGifFrame && !mCurrentGifFrame.isRecycled()) {
            mCurrentGifFrame.recycle();
            mCurrentGifFrame = null;
        }
    }
    
    // M: added for MAV
    @Override
    public boolean isMav(int offset) {
        boolean isMavType = MediatekFeature.MIMETYPE_MPO.equalsIgnoreCase(mItem.getMimeType());
        isMavType &= (mItem.getSubType() & MediaObject.SUBTYPE_MPO_MAV) != 0;
        return isMavType;
    }
    
    // M: following code are added for mav decoder    
    private class MavDecoderListener
            implements Runnable, FutureListener<DataBundle> {
        private Future<DataBundle> mFuture;
        private int mType;
        private MediaItem mItem;

        public MavDecoderListener(MediaItem item, int type) {
            mType = type;
            mItem = item;
        }

        @Override
        public void onFutureDone(Future<DataBundle> future) {
            mFuture = future;
            if (MediatekFeature.isMAVSupported() && null != mFuture.get()) {
                mHandler.sendMessage(
                        mHandler.obtainMessage(MSG_RUN_OBJECT, this));
            }
        }

        @Override
        public void run() {
            updateMavDecoder(mFuture, mItem, mType);
        }
    }
    
    private void updateMavDecoder(Future<DataBundle> future, MediaItem item, int type) {
        Log.d(TAG, ">> updateMavDecoder, type: " + type);
        if(type == TYPE_LOAD_TOTAL_COUNT) {
            mpoTotalCount = future.get().mpoTotalCount;
            MtkLog.d(TAG, "the mav total count is " + mpoTotalCount);
            
            // update MavSeekbar's max value and current progress
            if(mMavListener != null) {
                mMavListener.setSeekBar(mpoTotalCount - 1, MavSeekBar.INVALID_PROCESS);
            }
            
            // submit task to decode frames
            Params params = new Params();
            params.inMpoFrames = true;//we want mpo frames
            // get windows size
            Display defaultDisplay = ((Activity)mActivity).getWindowManager().getDefaultDisplay();
            int displaywidth = defaultDisplay.getWidth();
            int displayHeight = defaultDisplay.getHeight();
            int useMemoryForFullDisplay = 100 *(displaywidth*displayHeight);

            int parameter = (useMemoryForFullDisplay > MediatekFeature.MEMORY_THRESHOLD_MAV_L1) ? 2 : 1;
            long availableMemory = MediatekFeature.availableMemoryForMavPlayback(mActivity);
 
            if(availableMemory > MediatekFeature.MEMORY_THRESHOLD_MAV_L1) {
                params.inTargetDisplayHeight = defaultDisplay.getHeight()/parameter;
                params.inTargetDisplayWidth = defaultDisplay.getWidth()/parameter;
            } else if(availableMemory <= MediatekFeature.MEMORY_THRESHOLD_MAV_L1 && availableMemory > MediatekFeature.MEMORY_THRESHOLD_MAV_L2){
                MtkLog.d(TAG, "no enough memory, degrade sample rate to 1/2 of parameter");
                params.inTargetDisplayHeight = defaultDisplay.getHeight() / (parameter*2);
                params.inTargetDisplayWidth = defaultDisplay.getWidth() / (parameter*2);
            } else if(availableMemory <= MediatekFeature.MEMORY_THRESHOLD_MAV_L2){
                MtkLog.d(TAG, "no enough memory, degrade sample rate to 1/4 of parameter");
                params.inTargetDisplayHeight = defaultDisplay.getHeight() / (parameter*4);
                params.inTargetDisplayWidth = defaultDisplay.getWidth() / (parameter*4);
            }
            params.inPQEnhance = true;
            MtkLog.d(TAG, "display width: " + defaultDisplay.getWidth() + ", height: " + defaultDisplay.getHeight());
            
            // set MavListener, it's used to update MavSeekBar's progress by decode progress
            item.setMavListener(mMavListener);
            MtkLog.d(TAG, "start load all mav frames");
            mMpoDecoderTask = mThreadPool.submit(
                    item.requestImage(MediaItem.TYPE_THUMBNAIL, params),
                    new MavDecoderListener(item, TYPE_LOAD_FRAME));
        } else if(type == TYPE_LOAD_FRAME) {
            mpoFrames = getScreenNails(future.get().mpoFrames);
            if (mpoFrames != null && mpoFrames[0] != null) {
                MtkLog.d(TAG, "mpo frame width: " + mpoFrames[0].getWidth() + ", height: "
                        + mpoFrames[0].getHeight());
                // set the flag as true, it's used to hide mav icon
                mIsMavLoadingFinished = true;
                mIsMavLoadingSuccess = true;
                mPhotoView.setMavLoadingFinished(mIsMavLoadingFinished);
                startMavPlayback();
            } else {
                Log.i(TAG, "Invalid mpo file, load frame failed");
                mIsMavLoadingFinished = true;
                mIsMavLoadingSuccess = false;
                mPhotoView.setMavLoadingFinished(mIsMavLoadingFinished);
                Toast.makeText(mActivity.getApplicationContext(), R.string.mav_invalid,
                        Toast.LENGTH_SHORT).show();
            }
        }
        Log.d(TAG, "<< updateMavDecoder");
    }
    
    private ScreenNail[] getScreenNails(Bitmap[] bmps) {
        if(bmps == null) {
            return null;
        }
        int len = bmps.length;
        ScreenNail[] screenNails = new ScreenNail[len];
        for(int i = 0; i < len; i++) {
            if(bmps[i] == null) {
                MtkLog.d(TAG, "getScreenNails: bmps[" + i + "] is null");
            }
            ScreenNail screenNail = MediatekFeature.getMtkScreenNail(getMediaItem(0), bmps[i]);
            if(screenNail == null) {
                screenNail = new BitmapScreenNail(bmps[i]);
            }
            screenNails[i] = screenNail;
        }
        return screenNails;
    }
    
    private void startMavPlayback() {
        MtkLog.d(TAG, "startMavPlayback");
        if (mpoTotalCount > 0) {
            // TODO check mpo frame size if has limitation
            // set max value and middle position
            int middleFrame = (int) (mpoTotalCount / 2);
            MtkLog.d(TAG, "the middle frame is " + middleFrame);
            mCurrentMpoIndex = middleFrame;
            if (mMavListener != null) {
                mMavListener.setSeekBar(mpoTotalCount - 1, middleFrame);
                mMavListener.setStatus(true);
            }
            if (mMavRenderThread != null) {
                mMavRenderThread.setActive(false);
                mMavRenderThread.setRenderRequester(true);
                mMavRenderThread = null;
            } 
            mMavRenderThread = new MavRenderThread(mActivity);
            mMavRenderThread.initAnimation(mCurrentMpoIndex, MavRenderThread.CONTINUOUS_FRAME_ANIMATION);
            mMavRenderThread.setOnDrawMavFrameListener(this);
            mMavRenderThread.setActive(true);
            mMavRenderThread.setRenderRequester(true);
            mMavRenderThread.start();
        } else {
            MtkLog.e(TAG, "mpoTotalCount <= 0");
        }
    }
    
    private Object mRenderLock = new Object();
    public boolean mRenderRequested = false;
    
    private void requestRender() {
        MtkLog.d(TAG, "requestRender");
        long time = SystemClock.uptimeMillis();
        synchronized (mRenderLock) {
            mRenderRequested = true;
            mRenderLock.notifyAll();
        }
        time = SystemClock.uptimeMillis() - time;
        Log.i(TAG, "request render consumed " + time + "ms");
    }
    
    
    private ScreenNail mCurrentScreenNail;
    private ScreenNail mFirstScreenNail;
    private ScreenNail mSecondScreenNail;
    private ScreenNail mOldCurrentScreenNail;
    private ScreenNail mOldFirstScreenNail;
    private ScreenNail mOldSecondScreenNail;
    
    private boolean mIsMavStereoMode = false;

    private int mCurrentMpoIndex = -1;
    private int mNextMpoIndex = -1;
    public void setImageBitmap(int index, int animationMode) {
        if (mpoFrames == null) {
            MtkLog.v(TAG, "setImageBitmap: the mpoFrames of current entry is null");
            return;
        }
        int nextIndex = 0;
        int arrayLen = 0;
        
        arrayLen = mpoFrames.length;
        if (index >= 0 && index < arrayLen) {
            mCurrentMpoIndex = index;
            MtkLog.d(TAG, "get current mpo frame, index: " + index);
        }
            
        if (mIsMavStereoMode) {
            if (arrayLen > 1) {
                nextIndex = index + 1;
                if (nextIndex < 0) {
                    nextIndex = index;
                } else if (nextIndex > arrayLen - 1) {
                    nextIndex = arrayLen - 1;
                }
            }
        } else {
            nextIndex = index;
        }
        mNextMpoIndex = nextIndex;
        fresh(mCurrentMpoIndex, animationMode);
        MtkLog.d(TAG, "get next mpo frame, index: " + nextIndex);
    }

    public void setMavListener(MavListener listener) {
        mMavListener = listener;
    }

    public int getTotalFrameCount() {
        return mpoTotalCount;
    }

    public void updateMavStereoMode(boolean isMavStereoMode) {
        mIsMavStereoMode = isMavStereoMode;
    }

    public boolean isMavLoadingFinished() {
        return mIsMavLoadingFinished;
    }
    
    public boolean isMavLoadingSuccess() {
        return mIsMavLoadingSuccess;
    }
    
    public void cancelCurrentMavDecodeTask() {
        if (mIsMavSupported && mMpoDecoderTask != null) {
            mMpoDecoderTask.cancel();
            mIsMavLoadingFinished = false;
            mpoTotalCount = 0;
        }
    }

    private void fresh(int lastIndex, int animationMode) {
        if (mMavRenderThread != null) {
            if (animationMode == MavRenderThread.CONTINUOUS_FRAME_ANIMATION 
                    && Math.abs(mMavRenderThread.getAnimationTagetIndex() - lastIndex) < MavRenderThread.CONTINUOUS_FRAME_ANIMATION_CHANGE_THRESHOLD) {
                return ;
            }
            mMavRenderThread.initAnimation(lastIndex, animationMode);
            mMavRenderThread.setRenderRequester(true);
        }
    }

    private void updateMavcontent(int index) {
        if(mIsMavStereoMode) {
            // TODO if is stereo display, show mav frame as 3d mode
        } else {
            if(index < 0 || mpoFrames == null || index > mpoFrames.length) {
                MtkLog.d(TAG, "[renderCurrentFrame]mCurrentMpoIndex[" + index + "] out of bounds");
                return;
            }
            
            // as max texture size is 2048, we have to make sure that decoded
            // mav frame does not exceeds that size
            // TODO: 2048 should be replaced with meaningful constant variable
            // curBitmap = BitmapUtils.resizeDownBySideLength(curBitmap, 2048, true);
            mCurrentScreenNail = mpoFrames[index];
            
            setScreenNail(mCurrentScreenNail, mCurrentScreenNail.getWidth(), mCurrentScreenNail.getHeight());
            mPhotoView.notifyImageChange(0);
        }
    }

    public void drawMavFrame(int index) {
        // TODO Auto-generated method stub
        mHandler.removeMessages(MSG_UPDATE_MAV_FRAME);
        Message m = mHandler.obtainMessage(MSG_UPDATE_MAV_FRAME,
                index, 0);
        m.sendToTarget();
    }
    // M: 6592 panorama add @{
    private FutureListener<ScreenNail> mPanoramaListener = new FutureListener<ScreenNail>() {
        @Override
        public void onFutureDone(Future<ScreenNail> future) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_PANORAMA, future));
        }
    };

    private class PanoramaScreenNailJob implements Job<ScreenNail> {
        private MediaItem mItem;
        private Params mParams;

        public PanoramaScreenNailJob(MediaItem item, Params params) {
            mItem = item;
            mParams = params;
        }

        @Override
        public ScreenNail run(JobContext jc) {
            Job<DataBundle> imageRequest = mItem.requestImage(MediaItem.TYPE_THUMBNAIL, mParams);
            if (imageRequest == null) {
                mPanoramaTask = null;
                return null;
            }
            DataBundle dataBundle = imageRequest.run(jc);
            if (jc.isCancelled() || null == dataBundle || null == dataBundle.originalFrame) {
                mPanoramaTask = null;
                return null;
            }
            Bitmap bitmap = BitmapUtils.rotateBitmap(dataBundle.originalFrame, mItem.getRotation(), true);
            bitmap = PanoramaHelper.resizeBitmapToProperRatio(bitmap, true);
            PanoramaConfig config = new PanoramaConfig(bitmap.getWidth(), bitmap.getHeight(),
                    PanoramaHelper.getPanoramaScreenNailWidth(), PanoramaHelper
                            .getPanoramaScreenNailHeight());
            if (mPanoramaScreenNail != null) {
                ((PanoramaScreenNail) mPanoramaScreenNail).setBitmap(bitmap);
            } else {
                mPanoramaScreenNail =  new PanoramaScreenNail(bitmap, config);
            }
            return mPanoramaScreenNail;
        }
    }

    private void onDecodePanoramaComplete(Future<ScreenNail> future) {
        Log.i(TAG, "<onDecodePanoramaComplete>");
        mPhotoView.notifyImageChange(0);
        mPanoramaTask = null;
    }
    
    /// M: added for open image from local
    public void setIsOpenFromLocal(boolean isOpenFromLocal){
        
    }
    
    public void showWaitingIfNeed(){
        
    }
    // @}
}
