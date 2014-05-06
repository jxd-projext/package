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

package com.android.gallery3d.ui;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumDataLoader;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;

// M: Mediatek import
import com.mediatek.gallery3d.conshots.ContainerHelper;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;
/// M: Video thumbnail play @{
import com.mediatek.gallery3d.videothumbnail.VideoThumbnailSourceWindow;
import com.mediatek.gallery3d.videothumbnail.VideoThumbnailDirector;
import com.mediatek.gallery3d.videothumbnail.VideoThumbnailFeatureOption;
import com.mediatek.gallery3d.videothumbnail.VideoThumbnailTestUtil;
/// @}

public class AlbumSlotRenderer extends AbstractSlotRenderer implements
        VideoThumbnailSourceWindow.StageContext {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/AlbumSlotRenderer";

    public interface SlotFilter {
        public boolean acceptSlot(int index);
    }

    private final int mPlaceholderColor;
    private static final int CACHE_SIZE = 96;

    private AlbumSlidingWindow mDataWindow;
    private final AbstractGalleryActivity mActivity;
    private final ColorTexture mWaitLoadingTexture;
    private final SlotView mSlotView;
    private final SelectionManager mSelectionManager;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    private SlotFilter mSlotFilter;

    /// M: added for performance auto test
    public static long mWaitFinishedTime = 0;
    
    public AlbumSlotRenderer(AbstractGalleryActivity activity, SlotView slotView,
            SelectionManager selectionManager, int placeholderColor) {
        super(activity);
        mActivity = activity;
        mSlotView = slotView;
        mSelectionManager = selectionManager;
        mPlaceholderColor = placeholderColor;

        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
        /// M: Video thumbnail play @{
        if (VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE) {
            mVideoThumbnailDirector = new VideoThumbnailDirector(this);
        }
        /// @}
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index) return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1) return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path) return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    public void setModel(AlbumDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mSlotView.setSlotCount(0);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new AlbumSlidingWindow(mActivity, model, CACHE_SIZE);
            mDataWindow.setListener(new MyDataModelListener());
            mSlotView.setSlotCount(model.size());
        }
    }

    private static Texture checkTexture(Texture texture) {
        return (texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady()
                ? null
                : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        if (mSlotFilter != null && !mSlotFilter.acceptSlot(index)) return 0;

        AlbumSlidingWindow.AlbumEntry entry = mDataWindow.get(index);

        int renderRequestFlags = 0;

        Texture content = checkTexture(entry.content);
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitDisplayed = true;
        } else if (entry.isWaitDisplayed) {
            entry.isWaitDisplayed = false;
            //FadeInTexture will be transparent when launch gallery
            //content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture);
            content = entry.bitmapTexture;
            entry.content = content;
            /// M: added for performance auto test
            mWaitFinishedTime = System.currentTimeMillis();
        }
        /// M: Video thumbnail play @{
        if ((!VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE)
                || (!mVideoThumbnailDirector.renderThumbnail(entry, canvas,
                        width, height))) {
        /// @}
            if (MediatekFeature.permitShowThumb(entry.subType)) {
                drawContent(canvas, content, width, height, entry.rotation);
            } else {
                drawContent(canvas, mWaitLoadingTexture, width, height, entry.rotation);
            }
            if ((content instanceof FadeInTexture) &&
                    ((FadeInTexture) content).isAnimating()) {
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
            }
        /// M: Video thumbnail play @{
        }
        /// @}

        /// M: livephoto doesn't show videoOverlay.
        if (entry.mediaType == MediaObject.MEDIA_TYPE_VIDEO && entry.subType != MediaObject.SUBTYPE_LIVEPHOTO) {
            drawVideoOverlay(canvas, width, height);
        }

        /// M: only image shows panorama icon
        if (entry.isPanorama && entry.mediaType == MediaObject.MEDIA_TYPE_IMAGE) {
            drawPanoramaIcon(canvas, width, height);
        }
        
        MediatekFeature.renderSubTypeOverlay(mActivity.getAndroidContext(),
                                             canvas, width, height, entry.subType);
        /// M: added for ConShots
        ContainerHelper.renderOverLay(mActivity.getAndroidContext(),
                                             canvas, width, height, entry.item);
        renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);

        return renderRequestFlags;
    }

    // M: add for performance test case @{
    private boolean mHasShowLog = false;
    public static boolean sPerformanceCaseRunning = false; 
    // @}
    private int renderOverlay(GLCanvas canvas, int index,
            AlbumSlidingWindow.AlbumEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                // M: add for performance test case @{
                if (!mHasShowLog && sPerformanceCaseRunning) {
                    MtkLog.d(TAG, "[CMCC Performance test][Gallery2][Gallery] load 1M image time start ["
                                    + System.currentTimeMillis() + "]");
                    mHasShowLog = true;
                }
                // @}
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((entry.path != null) && (mHighlightItemPath == entry.path)) {
            drawSelectedFrame(canvas, width, height);
        } else if (mInSelectionMode && mSelectionManager.isItemSelected(entry.path)) {
            drawSelectedFrame(canvas, width, height);
        }
        return renderRequestFlags;
    }

    private class MyDataModelListener implements AlbumSlidingWindow.Listener {
        @Override
        public void onContentChanged() {
            /// M: Video thumbnail play @{
            //After AlbumEntry update, we should re-collection new cover item
            if (VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE) {
                mVideoThumbnailDirector.pumpLiveThumbnails();
            }
            /// @}
            mSlotView.invalidate();
        }

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
            // M: don't forget to invalidate, or UI will not refresh
            // after deleting the image from the back
            mSlotView.invalidate();
        }
    }

    public void resume() {
        mDataWindow.resume();
        /// M: Video thumbnail play @{
        if (VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE) {
            mVideoThumbnailDirector.resume(mDataWindow);
        }
        /// @}
    }

    public void pause() {
        mDataWindow.pause();
        /// M: Video thumbnail play @{
        if (VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE) {
            mVideoThumbnailDirector.pause();
        }
        /// @}
    }

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mSelectionManager.inSelectionMode();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
            /// M: Video thumbnail play @{
            if (VideoThumbnailFeatureOption.OPTION_ENABLE_THIS_FEATRUE) {
                mVideoThumbnailDirector.updateStage();
            }
            /// @}
        }
    }

    @Override
    public void onSlotSizeChanged(int width, int height) {
        // Do nothing
    }

    public void setSlotFilter(SlotFilter slotFilter) {
        mSlotFilter = slotFilter;
    }

/// M: Video thumbnail play @{
    private VideoThumbnailDirector mVideoThumbnailDirector;

    public boolean isStageChanging() {
        return !mSlotView.isScollingFinished();
        // return false; // assume not scrolling
    }

    public AbstractGalleryActivity getGalleryActivity() {
        return mActivity;
    }
/// @}
}
