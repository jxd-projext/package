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

package com.android.gallery3d.data;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;

public class ClusterAlbum extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/ClusterAlbum";
    private ArrayList<Path> mPaths = new ArrayList<Path>();
    private String mName = "";
    private DataManager mDataManager;
    private MediaSet mClusterAlbumSet;
    private MediaItem mCover;
    private MediaItem mCoverBackUp;
    /// M: note the number of deleted image. 
    private int mNumberOfDeletedImage;

    public ClusterAlbum(Path path, DataManager dataManager,
            MediaSet clusterAlbumSet) {
        super(path, nextVersionNumber());
        mDataManager = dataManager;
        mClusterAlbumSet = clusterAlbumSet;
        mClusterAlbumSet.addContentListener(this);
    }

    public void setCoverMediaItem(MediaItem cover) {
        mCover = cover;
    }

    @Override
    public MediaItem getCoverMediaItem() {
        if (mCover != null) {
            return mCover;
        }
        try {
            mCoverBackUp = super.getCoverMediaItem();
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
            return mCoverBackUp;
        } 
        return  mCoverBackUp;
    }

    void setMediaItems(ArrayList<Path> paths) {
        mPaths = paths;
    }

    /// M: add paths ordered. @{
    void addMediaItems(Path paths, int index) {
        if (paths != null) {
            mPaths.add(index, paths);
            nextVersion();
        }
    }
    /// @}

    ArrayList<Path> getMediaItems() {
        return mPaths;
    }

    public void setName(String name) {
        mName = name;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public int getMediaItemCount() {
        return mPaths.size();
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        return getMediaItemFromPath(mPaths, start, count, mDataManager);
    }

    public static ArrayList<MediaItem> getMediaItemFromPath(
            ArrayList<Path> paths, int start, int count,
            DataManager dataManager) {
        if (start >= paths.size()) {
            return new ArrayList<MediaItem>();
        }
        int end = Math.min(start + count, paths.size());
        ArrayList<Path> subset = new ArrayList<Path>(paths.subList(start, end));
        final MediaItem[] buf = new MediaItem[end - start];
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                buf[index] = item;
            }
        };
        dataManager.mapMediaItems(subset, consumer, 0);
        ArrayList<MediaItem> result = new ArrayList<MediaItem>(end - start);
        for (int i = 0; i < buf.length; i++) {
            if (buf[i] != null) {
                // M: we only add available items
                result.add(buf[i]);
            }
        }
        return result;
    }

    @Override
    protected int enumerateMediaItems(ItemConsumer consumer, int startIndex) {
        mDataManager.mapMediaItems(mPaths, consumer, startIndex);
        return mPaths.size();
    }

    @Override
    public int getTotalMediaItemCount() {
        /// M: For Cluster album delete operation, do not fresh clusterContent and use mNumberOfDeletedImage note 
        // the deleted images. 
        return mPaths.size() - mNumberOfDeletedImage;
    }

    @Override
    public long reload() {
        Log.d(TAG, "reload-->>>>>>>>>>>>>>>>>>>>>>>>>mClusterAlbumSet.synchronizedAlbumData() ");
        /// M: assign this object to ClusterAlbumSet.
        mClusterAlbumSet.mCurrentClusterAlbum = this;
        /// M: note the offset to stack top
        mClusterAlbumSet.offsetInStack = offsetInStack +1;
        if (mClusterAlbumSet.reload() > mDataVersion) {
            mDataVersion = nextVersionNumber();
            Log.d(TAG, "mClusterAlbumSet.synchronizedAlbumData() > mDataVersion");
        }
        offsetInStack = 0;
        return mDataVersion;
    }

    /// M: if the album is been update, should modify mDataVersion. @{
    public long nextVersion() {
            mDataVersion = nextVersionNumber();
        return mDataVersion;
    }
    /// @}
    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_SHARE | SUPPORT_DELETE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if ((item.getSupportedOperations() & SUPPORT_DELETE) != 0) {
                    item.delete();
                    /// M: note the number of deleted image. for Cluster album delete operation.
                    mNumberOfDeletedImage++;
                }
            }
        };
        mDataManager.mapMediaItems(mPaths, consumer, 0);
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    /// M: set and get delete number operation
    public void setNumberOfDeletedImage (int number) {
        mNumberOfDeletedImage = number;
    }

    public int getNumberOfDeletedImage () {
        return mNumberOfDeletedImage;
    }
    /// @}
}
