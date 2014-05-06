/*T
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

import android.graphics.Bitmap;
import android.os.Message;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumSetDataLoader;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataSourceType;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TextureUploader;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.JobLimiter;
import com.android.gallery3d.util.ThreadPool;

import android.os.SystemClock;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;
import com.mediatek.gallery3d.videothumbnail.VideoThumbnailSourceWindow;

public class AlbumSetSlidingWindow implements AlbumSetDataLoader.DataListener,
        VideoThumbnailSourceWindow {
    private static final String TAG = "Gallery2/AlbumSetSlidingWindow";
    private static final int MSG_UPDATE_ALBUM_ENTRY = 1;

    public static interface Listener {
        public void onSizeChanged(int size);
        public void onContentChanged();
    }

    private final AlbumSetDataLoader mSource;
    private int mSize;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private Listener mListener;

    private final AlbumSetEntry mData[];
    private final SynchronizedHandler mHandler;
    private final ThreadPool mThreadPool;
    /// M: mVideoMicroThumbDecoder specializes on video thumbnail decoding
    private final JobLimiter mVideoMicroThumbDecoder;
    private final AlbumLabelMaker mLabelMaker;
    private final String mLoadingText;

    private final TiledTexture.Uploader mContentUploader;
    private final TextureUploader mLabelUploader;

    private int mActiveRequestCount = 0;
    private boolean mIsActive = false;
    private BitmapTexture mLoadingLabel;

    private int mSlotWidth;

    public static class AlbumSetEntry implements VideoThumbnailSourceWindow.DataEntry {
        public MediaSet album;
        public MediaItem coverItem;
        public Texture content;
        public BitmapTexture labelTexture;
        public TiledTexture bitmapTexture;
        public Path setPath;
        public String title;
        public int totalCount;
        public int sourceType;
        public int cacheFlag;
        public int cacheStatus;
        public int rotation;
        public int mediaType;
        public boolean isPanorama;
        public boolean isWaitLoadingDisplayed;
        public long setDataVersion;
        public long coverDataVersion;
        private BitmapLoader labelLoader;
        private BitmapLoader coverLoader;
        
        // M: added for sub-type
        public int subType;

        /// M: vtsp @{
        public MediaItem getPlayItem() {
            return coverItem;
        }
        /// @}
    }

    public AlbumSetSlidingWindow(AbstractGalleryActivity activity,
            AlbumSetDataLoader source, AlbumSetSlotRenderer.LabelSpec labelSpec, int cacheSize) {
        source.setModelListener(this);
        mSource = source;
        mData = new AlbumSetEntry[cacheSize];
        mSize = source.size();
        mThreadPool = activity.getThreadPool();
        /// M: mVideoMicroThumbDecoder specializes on video thumbnail decoding @{
        final int VIDEO_MICRO_THUMB_DECODER_JOB_LIMIT = 2;
        mVideoMicroThumbDecoder = new JobLimiter(activity.getThreadPool(),
                VIDEO_MICRO_THUMB_DECODER_JOB_LIMIT);
        /// @}

        mLabelMaker = new AlbumLabelMaker(activity.getAndroidContext(), labelSpec);
        mLoadingText = activity.getAndroidContext().getString(R.string.loading);
        mContentUploader = new TiledTexture.Uploader(activity.getGLRoot());
        mLabelUploader = new TextureUploader(activity.getGLRoot());

        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_ALBUM_ENTRY);
                ((EntryUpdater) message.obj).updateEntry();
            }
        };
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public AlbumSetEntry get(int slotIndex) {
        if (!isActiveSlot(slotIndex)) {
            Utils.fail("invalid slot: %s outsides (%s, %s)",
                    slotIndex, mActiveStart, mActiveEnd);
        }
        return mData[slotIndex % mData.length];
    }

    public int size() {
        return mSize;
    }

    public boolean isActiveSlot(int slotIndex) {
        return slotIndex >= mActiveStart && slotIndex < mActiveEnd;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd) return;

        if (contentStart >= mContentEnd || mContentStart >= contentEnd) {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        } else {
            for (int i = mContentStart; i < contentStart; ++i) {
                freeSlotContent(i);
            }
            for (int i = contentEnd, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart, n = mContentStart; i < n; ++i) {
                prepareSlotContent(i);
            }
            for (int i = mContentEnd; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        }

        mContentStart = contentStart;
        mContentEnd = contentEnd;
    }

    public void setActiveWindow(int start, int end) {
        if (!(start <= end && end - start <= mData.length && end <= mSize)) {
            Utils.fail("start = %s, end = %s, length = %s, size = %s",
                    start, end, mData.length, mSize);
        }

        AlbumSetEntry data[] = mData;
        mActiveStart = start;
        mActiveEnd = end;
        int contentStart = Utils.clamp((start + end) / 2 - data.length / 2,
                0, Math.max(0, mSize - data.length));
        int contentEnd = Math.min(contentStart + data.length, mSize);
        setContentWindow(contentStart, contentEnd);

        if (mIsActive) {
            updateTextureUploadQueue();
            updateAllImageRequests();
        }
    }

    // We would like to request non active slots in the following order:
    // Order:    8 6 4 2                   1 3 5 7
    //         |---------|---------------|---------|
    //                   |<-  active  ->|
    //         |<-------- cached range ----------->|
    private void requestNonactiveImages() {
        int range = Math.max(
                mContentEnd - mActiveEnd, mActiveStart - mContentStart);
        for (int i = 0 ;i < range; ++i) {
            requestImagesInSlot(mActiveEnd + i);
            requestImagesInSlot(mActiveStart - 1 - i);
        }
    }

    private void cancelNonactiveImages() {
        int range = Math.max(
                mContentEnd - mActiveEnd, mActiveStart - mContentStart);
        for (int i = 0 ;i < range; ++i) {
            cancelImagesInSlot(mActiveEnd + i);
            cancelImagesInSlot(mActiveStart - 1 - i);
        }
    }

    private void requestImagesInSlot(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd) return;
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.startLoad();
        if (entry.labelLoader != null) entry.labelLoader.startLoad();
    }

    private void cancelImagesInSlot(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd) return;
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.cancelLoad();
        if (entry.labelLoader != null) entry.labelLoader.cancelLoad();
    }

    private static long getDataVersion(MediaObject object) {
        return object == null
                ? MediaSet.INVALID_DATA_VERSION
                : object.getDataVersion();
    }

    private void freeSlotContent(int slotIndex) {
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.recycle();
        if (entry.labelLoader != null) entry.labelLoader.recycle();
        if (entry.labelTexture != null) entry.labelTexture.recycle();
        if (entry.bitmapTexture != null) entry.bitmapTexture.recycle();
        mData[slotIndex % mData.length] = null;
    }

    private boolean isLabelChanged(
            AlbumSetEntry entry, String title, int totalCount, int sourceType) {
        return !Utils.equals(entry.title, title)
                || entry.totalCount != totalCount
                || entry.sourceType != sourceType;
    }

    private void updateAlbumSetEntry(AlbumSetEntry entry, int slotIndex) {
        MediaSet album = mSource.getMediaSet(slotIndex);
        MediaItem cover = mSource.getCoverItem(slotIndex);
        int totalCount = mSource.getTotalCount(slotIndex);

        entry.album = album;
        entry.setDataVersion = getDataVersion(album);
        entry.cacheFlag = identifyCacheFlag(album);
        entry.cacheStatus = identifyCacheStatus(album);
        entry.setPath = (album == null) ? null : album.getPath();

        String title = (album == null) ? "" : Utils.ensureNotNull(album.getName());
        int sourceType = DataSourceType.identifySourceType(album);
        if (isLabelChanged(entry, title, totalCount, sourceType)) {
            entry.title = title;
            entry.totalCount = totalCount;
            entry.sourceType = sourceType;
            if (entry.labelLoader != null) {
                entry.labelLoader.recycle();
                entry.labelLoader = null;
                entry.labelTexture = null;
            }
            if (album != null) {
                entry.labelLoader = new AlbumLabelLoader(
                        slotIndex, title, totalCount, sourceType);
            }
        }

        entry.coverItem = cover;
        if (getDataVersion(cover) != entry.coverDataVersion) {
            entry.coverDataVersion = getDataVersion(cover);
            // M: 6592 panorama add @{
            if (MediatekFeature.isPanorama3DSupported()) {
                entry.isPanorama = cover != null ? cover.isPanorama() : false;
            } else {
            // @}
                entry.isPanorama = GalleryUtils.isPanorama(cover);
            }
            entry.rotation = (cover == null) ? 0 : cover.getRotation();
            entry.mediaType = (cover == null) ? 0 : cover.getMediaType();
            if (entry.coverLoader != null) {
                entry.coverLoader.recycle();
                entry.coverLoader = null;
                entry.bitmapTexture = null;
                entry.content = null;
            }
            if (cover != null) {
                entry.coverLoader = new AlbumCoverLoader(slotIndex, cover);
                // M: added for sub-type
                entry.subType = cover.getSubType();
            }
        }
    }

    private void prepareSlotContent(int slotIndex) {
        AlbumSetEntry entry = new AlbumSetEntry();
        updateAlbumSetEntry(entry, slotIndex);
        mData[slotIndex % mData.length] = entry;
    }

    private static boolean startLoadBitmap(BitmapLoader loader) {
        if (loader == null) return false;
        loader.startLoad();
        return loader.isRequestInProgress();
    }

    private void uploadBackgroundTextureInSlot(int index) {
        if (index < mContentStart || index >= mContentEnd) return;
        AlbumSetEntry entry = mData[index % mData.length];
        if (entry.bitmapTexture != null) {
            mContentUploader.addTexture(entry.bitmapTexture);
        }
        if (entry.labelTexture != null) {
            mLabelUploader.addBgTexture(entry.labelTexture);
        }
    }

    private void updateTextureUploadQueue() {
        if (!mIsActive) return;
        mContentUploader.clear();
        mLabelUploader.clear();

        // Upload foreground texture
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            if (entry.bitmapTexture != null) {
                mContentUploader.addTexture(entry.bitmapTexture);
            }
            if (entry.labelTexture != null) {
                mLabelUploader.addFgTexture(entry.labelTexture);
            }
        }

        // add background textures
        int range = Math.max(
                (mContentEnd - mActiveEnd), (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            uploadBackgroundTextureInSlot(mActiveEnd + i);
            uploadBackgroundTextureInSlot(mActiveStart - i - 1);
        }
    }

    private void updateAllImageRequests() {
        mActiveRequestCount = 0;
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            if (startLoadBitmap(entry.coverLoader)) ++mActiveRequestCount;
            if (startLoadBitmap(entry.labelLoader)) ++mActiveRequestCount;
        }
        if (mActiveRequestCount == 0) {
            requestNonactiveImages();
        } else {
            cancelNonactiveImages();
        }
    }

    @Override
    public void onSizeChanged(int size) {
        if (mIsActive && mSize != size) {
            mSize = size;
            if (mListener != null) mListener.onSizeChanged(mSize);
            if (mContentEnd > mSize) mContentEnd = mSize;
            if (mActiveEnd > mSize) mActiveEnd = mSize;
        }
    }

    @Override
    public void onContentChanged(int index) {
        if (!mIsActive) {
            // paused, ignore slot changed event
            return;
        }

        // If the updated content is not cached, ignore it
        if (index < mContentStart || index >= mContentEnd) {
            Log.w(TAG, String.format(
                    "invalid update: %s is outside (%s, %s)",
                    index, mContentStart, mContentEnd) );
            return;
        }

        AlbumSetEntry entry = mData[index % mData.length];
        updateAlbumSetEntry(entry, index);
        updateAllImageRequests();
        updateTextureUploadQueue();
        if (mListener != null && isActiveSlot(index)) {
            mListener.onContentChanged();
        }
    }

    public BitmapTexture getLoadingTexture() {
        if (mLoadingLabel == null) {
            Bitmap bitmap = mLabelMaker.requestLabel(
                    mLoadingText, "", DataSourceType.TYPE_NOT_CATEGORIZED)
                    .run(ThreadPool.JOB_CONTEXT_STUB);
            mLoadingLabel = new BitmapTexture(bitmap);
            mLoadingLabel.setOpaque(false);
        }
        return mLoadingLabel;
    }

    public void pause() {
        mIsActive = false;
        mLabelUploader.clear();
        mContentUploader.clear();
        TiledTexture.freeResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            freeSlotContent(i);
        }
    }

    public void resume() {
        mIsActive = true;
        TiledTexture.prepareResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            prepareSlotContent(i);
        }
        updateAllImageRequests();
    }

    private static interface EntryUpdater {
        public void updateEntry();
    }

    private class AlbumCoverLoader extends BitmapLoader implements EntryUpdater {
        private MediaItem mMediaItem;
        private final int mSlotIndex;

        public AlbumCoverLoader(int slotIndex, MediaItem item) {
            mSlotIndex = slotIndex;
            mMediaItem = item;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            /// M: mVideoMicroThumbDecoder specializes on video thumbnail decoding @{
            if (MediaObject.MEDIA_TYPE_VIDEO == mMediaItem.getMediaType()) {
                return mVideoMicroThumbDecoder.submit(
                          mMediaItem.requestImage(MediaItem.TYPE_MICROTHUMBNAIL), l);
            }
            /// @}
            return mThreadPool.submit(mMediaItem.requestImage(
                    MediaItem.TYPE_MICROTHUMBNAIL), l);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ALBUM_ENTRY, this).sendToTarget();
        }

        @Override
        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null) return; // error or recycled

            AlbumSetEntry entry = mData[mSlotIndex % mData.length];
            TiledTexture texture = new TiledTexture(bitmap);
            // M: 6592 panorama add @{
            if (MediatekFeature.isPanorama3DSupported() && entry.isPanorama) {
                texture.setOpaque(false);
            }
            // @}
            entry.bitmapTexture = texture;
            entry.content = texture;

            if (isActiveSlot(mSlotIndex)) {
                mContentUploader.addTexture(texture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0) requestNonactiveImages();
                if (mListener != null) mListener.onContentChanged();
            } else {
                mContentUploader.addTexture(texture);
            }
            
            // M: for performance auto test
            mBitmapLoaded = true;
            if (isActiveSlot(mSlotIndex) && mActiveRequestCount == 0) {
                mDecodeFinished = true;
                mDecodeFinishTime = System.currentTimeMillis();
            }
        }
    }

    private static int identifyCacheFlag(MediaSet set) {
        if (set == null || (set.getSupportedOperations()
                & MediaSet.SUPPORT_CACHE) == 0) {
            return MediaSet.CACHE_FLAG_NO;
        }

        return set.getCacheFlag();
    }

    private static int identifyCacheStatus(MediaSet set) {
        if (set == null || (set.getSupportedOperations()
                & MediaSet.SUPPORT_CACHE) == 0) {
            return MediaSet.CACHE_STATUS_NOT_CACHED;
        }

        return set.getCacheStatus();
    }

    private class AlbumLabelLoader extends BitmapLoader implements EntryUpdater {
        private final int mSlotIndex;
        private final String mTitle;
        private final int mTotalCount;
        private final int mSourceType;

        public AlbumLabelLoader(
                int slotIndex, String title, int totalCount, int sourceType) {
            mSlotIndex = slotIndex;
            mTitle = title;
            mTotalCount = totalCount;
            mSourceType = sourceType;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(mLabelMaker.requestLabel(
                    mTitle, String.valueOf(mTotalCount), mSourceType), l);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ALBUM_ENTRY, this).sendToTarget();
        }

        @Override
        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null) return; // Error or recycled

            AlbumSetEntry entry = mData[mSlotIndex % mData.length];
            BitmapTexture texture = new BitmapTexture(bitmap);
            texture.setOpaque(false);
            entry.labelTexture = texture;

            if (isActiveSlot(mSlotIndex)) {
                mLabelUploader.addFgTexture(texture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0) requestNonactiveImages();
                if (mListener != null) mListener.onContentChanged();
            } else {
                mLabelUploader.addBgTexture(texture);
            }
            
            // M: for performance auto test
            mBitmapLoaded = true;
            if (isActiveSlot(mSlotIndex) && mActiveRequestCount == 0) {
                mDecodeFinished = true;
                mDecodeFinishTime = System.currentTimeMillis();
            }
        }
    }

    public void onSlotSizeChanged(int width, int height) {
        if (mSlotWidth == width) return;

        mSlotWidth = width;
        mLoadingLabel = null;
        mLabelMaker.setLabelWidth(mSlotWidth);

        if (!mIsActive) return;

        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            if (entry.labelLoader != null) {
                entry.labelLoader.recycle();
                entry.labelLoader = null;
                entry.labelTexture = null;
            }
            if (entry.album != null) {
                entry.labelLoader = new AlbumLabelLoader(i,
                        entry.title, entry.totalCount, entry.sourceType);
            }
        }
        updateAllImageRequests();
        updateTextureUploadQueue();
    }
    
    // M: for performance auto test
    public boolean mDecodeFinished = false;
    public long mDecodeFinishTime = 0;
    
    // M: returns true if all active/visible slots are filled
    public boolean isAllActiveSlotsFilled() {
        // M: check if active range is available
        int start = mActiveStart;
        int end = mActiveEnd;
        
        if (start < 0 || start >= end) {
            MtkLog.w(TAG, "isAllActiveSlotFilled: active range not ready yet");
            return false;
        }
        
        // M: check individual slots for load status
        for (int i = start; i < end; ++i) {
            AlbumSetEntry entry = mData[i & mData.length];
            boolean isLoaded = (entry != null && entry.coverLoader != null && entry.labelLoader != null);
            if (isLoaded) {
                isLoaded &= entry.coverLoader.mBitmapLoaded && entry.labelLoader.mBitmapLoaded;
            }
            
            if (!isLoaded) {
                return false;
            }
        }
        
        return true;
    }

    /// M: Video thumbnail play @{
    public int getActiveStart() {
        return mActiveStart;
    }
    public int getActiveEnd() {
        return mActiveEnd;
    }
    public int getContentStart() {
        return mContentStart;
    }
    public int getContentEnd() {
        return mContentEnd;
    }

    public int getActiveRequestCount() {
        return mActiveRequestCount;
    }

    public AlbumSetEntry getThumbnailEntryAt(int slotIndex) {
        if (isActiveSlot(slotIndex)) {
            return mData[slotIndex % mData.length];
        }
        return null;
    }

    public boolean isAllActiveSlotsStaticThumbnailReady() {
        int start = mActiveStart;
        int end = mActiveEnd;

        if (start < 0 || start >= end) {
            MtkLog.w(TAG, "isAllActiveSlotsStaticThumbnailReady: active range not ready yet");
            return false;
        }

        for (int i = start; i < end; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            boolean isReady = (entry != null && entry.labelLoader != null);
            if (isReady) {
                isReady &= entry.labelLoader.mBitmapLoaded;
            }

            if (!isReady) {
                return false;
            }
        }

        return true;
    }
    /// @}
}
