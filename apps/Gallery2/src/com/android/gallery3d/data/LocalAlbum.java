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

import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Trace;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.BucketNames;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;

import java.io.File;
import java.util.ArrayList;

import com.mediatek.gallery3d.conshots.ContainerImage;
import com.mediatek.gallery3d.drm.DrmHelper;
import com.mediatek.gallery3d.mpo.MpoHelper;
import com.mediatek.gallery3d.stereo.StereoHelper;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;
import com.mediatek.gallery3d.util.MtkUtils;
import com.mediatek.common.mpodecoder.IMpoDecoder;

// LocalAlbumSet lists all media items in one bucket on local storage.
// The media items need to be all images or all videos, but not both.
public class LocalAlbum extends MediaSet {
    private static final String TAG = "Gallery2/LocalAlbum";
    private static final String[] COUNT_PROJECTION = { "count(*)" };

    private static final int INVALID_COUNT = -1;
    private String mWhereClause;
    private final String mOrderClause;
    private final Uri mBaseUri;
    private final String[] mProjection;

    private String[] mWhereClauseArgs; // M: remove final for conshots
    /// M: added for ConShots
    private String[] mWhereClauseArgsForDelete; 
    private String mWhereClauseForDelete;
    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final int mBucketId;
    private String mName;
    private final boolean mIsImage;
    private final ChangeNotifier mNotifier;
    private final Path mItemPath;
    private int mCachedCount = INVALID_COUNT;

    //added to support Mediatek features
    private static final boolean mIsDrmSupported = 
                                          MediatekFeature.isDrmSupported();
    private static final boolean mIsMpoSupported = 
                                          MediatekFeature.isMpoSupported();
    private static final boolean mIsStereoDisplaySupported = 
                                          MediatekFeature.isStereoDisplaySupported();
    /// M: added for ConShots
    private static final boolean mIsConShotsSupported =
                                          MediatekFeature.isConShotsImagesSupported();
    
    private static final boolean mIsMotionTrackSupported =
                                          MediatekFeature.isMotionTrackSupported();
    
    public LocalAlbum(Path path, GalleryApp application, int bucketId,
            boolean isImage, String name) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mBucketId = bucketId;
        mName = name;
        mIsImage = isImage;

        if (isImage) {
            //added to query all stereo image if needed
            if (mIsStereoDisplaySupported && 
                StereoHelper.INVALID_BUCKET_ID == mBucketId) {
                mWhereClause = MediatekFeature.getOnlyStereoWhereClause(
                        MediatekFeature.ALL_DRM_MEDIA & mPath.getMtkInclusion());
                mWhereClauseArgs = null;
            } else {
                String mpoDrmWhereClause = MediatekFeature.getWhereClause(
                                                    mPath.getMtkInclusion());
                if (null == mpoDrmWhereClause) {
                    mWhereClause = ImageColumns.BUCKET_ID + " = ?";
                } else {
                    mWhereClause = "(" + ImageColumns.BUCKET_ID + " = ?) AND (" +
                                   mpoDrmWhereClause + ")";
                }
                mWhereClauseArgs = new String[]{String.valueOf(mBucketId)};
            }
            
            /// M: if set wallpaper, exclude mav file
            String whereClauseMav = MpoHelper.getMavWhereClause(mPath.getMtkInclusion());
            if (null != whereClauseMav) {
                mWhereClause = (null == mWhereClause) ? whereClauseMav :
                             "(" + mWhereClause + ") AND (" + whereClauseMav +")";
            }
            
            mOrderClause = ImageColumns.DATE_TAKEN + " DESC, "
                    + ImageColumns._ID + " DESC";
            mBaseUri = Images.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalImage.PROJECTION;
            if (mIsDrmSupported) {
                mItemPath = LocalImage.getItemPath(path.getMtkInclusion());
            } else {
                mItemPath = LocalImage.ITEM_PATH;
            }
            
            /// M: added for ConShots @{
            mWhereClauseForDelete = mWhereClause;
            mWhereClauseArgsForDelete = mWhereClauseArgs;
            if (mIsConShotsSupported) {
                StringBuilder sb = new StringBuilder();
                sb.append(" AND ( " + Images.Media.GROUP_ID + " = 0");
                sb.append(" OR ("+ Images.Media.GROUP_ID + " IS NOT NULL"+" AND " + ImageColumns.TITLE + " NOT LIKE 'IMG%CS')");
                sb.append(" OR " + Images.Media.GROUP_ID + " IS NULL)");
                sb.append(" OR ");
                sb.append("_id in (SELECT min(_id) FROM images WHERE ");
                sb.append(Images.Media.GROUP_ID + " != 0");
                sb.append(" AND "+Images.Media.TITLE + " LIKE 'IMG%CS'");
                sb.append(" AND ");
                sb.append(ImageColumns.BUCKET_ID + " = ?");
                sb.append(" GROUP BY " + Images.Media.GROUP_ID + ")");
                mWhereClause += sb.toString();
                mWhereClauseArgs = new String[] { String.valueOf(mBucketId), String.valueOf(mBucketId) };
            }
            /// @}
        } else {
            //added to query all stereo video if needed
            if (mIsStereoDisplaySupported && 
                StereoHelper.INVALID_BUCKET_ID == mBucketId) {
                mWhereClause = MediatekFeature.getOnlyStereoWhereClause(
                        MediatekFeature.ALL_DRM_MEDIA & mPath.getMtkInclusion());
                mWhereClauseArgs = null;
            } else {
                //as no video's mime_type is "image/mpo", only drm is needed to check
                String whereClause = null;
                if (mIsDrmSupported) {
                    whereClause =  MediatekFeature.getWhereClause(
                                            mPath.getMtkInclusion());
                }
                if (null == whereClause) {
                    mWhereClause = VideoColumns.BUCKET_ID + " = ?";
                } else {
                    mWhereClause = "(" + VideoColumns.BUCKET_ID + " = ?) AND (" +
                                   whereClause + ")";
                }
                mWhereClauseArgs = new String[]{String.valueOf(mBucketId)};
            }

            mOrderClause = VideoColumns.DATE_TAKEN + " DESC, "
                    + VideoColumns._ID + " DESC";
            mBaseUri = Video.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalVideo.PROJECTION;
            if (mIsDrmSupported) {
                mItemPath = LocalVideo.getItemPath(path.getMtkInclusion());
            } else {
                mItemPath = LocalVideo.ITEM_PATH;
            }
        }

        mNotifier = new ChangeNotifier(this, mBaseUri, application);
    }
    public LocalAlbum(Path path, GalleryApp application, int bucketId,
            boolean isImage) {
        this(path, application, bucketId, isImage,
                BucketHelper.getBucketName(
                application.getContentResolver(), bucketId));
    }
    
    @Override
    public boolean isCameraRoll() {
            /// M: get default storage path in run time
            //return mBucketId == MediaSetUtils.CAMERA_BUCKET_ID;
            String defaultPath = MtkUtils.getMtkDefaultPath()+"/DCIM/Camera";
            return mBucketId == GalleryUtils.getBucketId(defaultPath);
    }

    @Override
    public Uri getContentUri() {
        if (mIsImage) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                            String.valueOf(mBucketId)).build();
        } else {
            return MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                            String.valueOf(mBucketId)).build();
        }
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("limit", start + "," + count).build();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        GalleryUtils.assertNotInRenderThread();
        Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-query");
        Cursor cursor = mResolver.query(
                uri, mProjection, MediatekFeature.getMediatekWhereClause(mWhereClause),// M: add for 2k video feature
                mWhereClauseArgs, //new String[]{String.valueOf(mBucketId)},
                mOrderClause);
        Trace.traceEnd(Trace.TRACE_TAG_APP);
        if (cursor == null) {
            Log.w(TAG, "query fail: " + uri);
            return list;
        }
        Path childPath = null;
        boolean dataDirty = false;
        try {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column
                if (mIsDrmSupported) {
                    childPath = mItemPath.getChild(id,mItemPath.getMtkInclusion());
                } else {
                    childPath = mItemPath.getChild(id);
                }
                Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem");
                MediaItem item = loadOrUpdateItem(childPath, cursor,
                        dataManager, mApplication, mIsImage);
                Trace.traceEnd(Trace.TRACE_TAG_APP);
                list.add(item);
                //add check for data updated from database
                if (null != item && ((LocalMediaItem)item).dataDirty) {
                    dataDirty = true;
                    ((LocalMediaItem)item).dataDirty = false;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "exception in creating media object: " + childPath, t);
        } finally {
            cursor.close();
        }

        //add check for data updatd from database
        if (dataDirty) {
            Log.i(TAG,"getMediaItem:data changed in database.");
            notifyContentChanged();
        }

        return list;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor,
            DataManager dataManager, GalleryApp app, boolean isImage) {
        synchronized (DataManager.LOCK) { 
            Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem-peekMediaObject");
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            Trace.traceEnd(Trace.TRACE_TAG_APP);
            if (item == null) {
                if (isImage) {
                    /// M:added for ConShots
                    if(ContainerImage.isContainerItem(app, cursor) && (mIsConShotsSupported || mIsMotionTrackSupported)){
                        Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem-new ContainerImage");
                        item = new ContainerImage(path, app, cursor);
                        Trace.traceEnd(Trace.TRACE_TAG_APP);
                    }else{                    
                        Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem-new LocalImage");
                        item = new LocalImage(path, app, cursor);
                        Trace.traceEnd(Trace.TRACE_TAG_APP);
                    }
                } else {
                    Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem-new LocalVideo");
                    item = new LocalVideo(path, app, cursor);
                    Trace.traceEnd(Trace.TRACE_TAG_APP);
                }
            } else {
                Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-loadOrUpdateItem-updateContent");
                item.updateContent(cursor);
                Trace.traceEnd(Trace.TRACE_TAG_APP);
            }
            return item;
        }
    }

    // The pids array are sorted by the (path) id.
    public static MediaItem[] getMediaItemById(
            GalleryApp application, boolean isImage, ArrayList<Integer> ids) {
        return getMediaItemById(application, isImage, ids, 0);
    }

    // The pids array are sorted by the (path) id.
    public static MediaItem[] getMediaItemById(GalleryApp application, 
            boolean isImage, ArrayList<Integer> ids, int mtkInclusion) {
        // get the lower and upper bound of (path) id
        MediaItem[] result = new MediaItem[ids.size()];
        if (ids.isEmpty()) return result;
        int idLow = ids.get(0);
        int idHigh = ids.get(ids.size() - 1);

        // prepare the query parameters
        Uri baseUri;
        String[] projection;
        Path itemPath;
        if (isImage) {
            baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            projection = LocalImage.PROJECTION;
            itemPath = LocalImage.ITEM_PATH;
            if (0 != mtkInclusion) {
                itemPath = LocalImage.getItemPath(mtkInclusion);
            }
        } else {
            baseUri = Video.Media.EXTERNAL_CONTENT_URI;
            projection = LocalVideo.PROJECTION;
            itemPath = LocalVideo.ITEM_PATH;
            if (0 != mtkInclusion) {
                itemPath = LocalVideo.getItemPath(mtkInclusion);
            }
        }

        ContentResolver resolver = application.getContentResolver();
        DataManager dataManager = application.getDataManager();
        Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-getMediaItemById-query");
        Cursor cursor = resolver.query(baseUri, projection, "_id BETWEEN ? AND ?",
                new String[]{String.valueOf(idLow), String.valueOf(idHigh)},
                "_id");
        Trace.traceEnd(Trace.TRACE_TAG_APP);
        if (cursor == null) {
            Log.w(TAG, "query fail" + baseUri);
            return result;
        }
        try {
            int n = ids.size();
            int i = 0;

            while (i < n && cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column

                // Match id with the one on the ids list.
                if (ids.get(i) > id) {
                    continue;
                }

                while (ids.get(i) < id) {
                    if (++i >= n) {
                        return result;
                    }
                }

                Path childPath = null;
                if (mIsDrmSupported) {
                    childPath = itemPath.getChild(id,itemPath.getMtkInclusion());
                } else {
                    childPath = itemPath.getChild(id);
                }
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager,
                        application, isImage);
                result[i] = item;
                ++i;
            }
            return result;
        } finally {
            cursor.close();
        }
    }

    public static Cursor getItemCursor(ContentResolver resolver, Uri uri,
            String[] projection, int id) {
        Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-getItemCursor-query");
        Cursor cursor = resolver.query(uri, projection, "_id=?",
                new String[]{String.valueOf(id)}, null);
        Trace.traceEnd(Trace.TRACE_TAG_APP);
        return cursor;
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            Trace.traceBegin(Trace.TRACE_TAG_APP, ">>>>LocalAlbum-getMediaItemCount-query");
            // M: When SdCard eject, query may throw IllegalStateException, catch it here
            Cursor cursor = null;
            try {
                cursor = mResolver.query(
                        mBaseUri, COUNT_PROJECTION,
                    MediatekFeature.getMediatekWhereClause(mWhereClause),// M: add for 2k video feature
                        mWhereClauseArgs,//new String[]{String.valueOf(mBucketId)}, 
                        null);
            } catch (IllegalStateException e) {
                Log.w(TAG, "query exception:" + e.getMessage());
                return 0;
            }
            Trace.traceEnd(Trace.TRACE_TAG_APP);
            if (cursor == null) {
                Log.w(TAG, "query fail");
                return 0;
            }
            try {
                Utils.assertTrue(cursor.moveToNext());
                mCachedCount = cursor.getInt(0);
            } finally {
                cursor.close();
            }
        }
        Log.d(TAG, "getMediaItemCount:"+mCachedCount);
        return mCachedCount;
    }

    @Override
    public String getName() {
        return getLocalizedName(mApplication.getResources(), mBucketId, mName);
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
            Log.d(TAG, "reload isDirty");
        }
        return mDataVersion;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_DELETE | SUPPORT_SHARE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        GalleryUtils.assertNotInRenderThread();
        if(mIsImage && mIsConShotsSupported ){
          ///M : delete albums. 
            GalleryUtils.deleteItems(mResolver,mBaseUri, mWhereClauseForDelete,mWhereClauseArgsForDelete);
            mResolver.delete(mBaseUri, mWhereClauseForDelete,mWhereClauseArgsForDelete);
        }else{
          ///M : delete albums. 
            GalleryUtils.deleteItems(mResolver,mBaseUri, mWhereClause,mWhereClauseArgs);
            mResolver.delete(mBaseUri, mWhereClause,mWhereClauseArgs/*new String[]{String.valueOf(mBucketId)}*/);
        }
        mApplication.getDataManager().broadcastUpdatePicture();
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    public static String getLocalizedName(Resources res, int bucketId,
            String name) {
        /// M: fix bug: camera folder doesn't change to corresponding language.e.g.chinese simple
        // when unmount and re-mount sdcard
        MediaSetUtils.refreshBucketId();
        if (bucketId == MediaSetUtils.CAMERA_BUCKET_ID) {
            return res.getString(R.string.folder_camera);
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            return res.getString(R.string.folder_download);
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            return res.getString(R.string.folder_imported);
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            return res.getString(R.string.folder_screenshot);
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            return res.getString(R.string.folder_edited_online_photos);
        } else {
            return name;
        }
    }
    // Relative path is the absolute path minus external storage path
    public static String getRelativePath(int bucketId) {
        String relativePath = "/";
        if (bucketId == MediaSetUtils.CAMERA_BUCKET_ID) {
            relativePath += BucketNames.CAMERA;
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            relativePath += BucketNames.DOWNLOAD;
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            relativePath += BucketNames.IMPORTED;
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            relativePath += BucketNames.SCREENSHOTS;
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            relativePath += BucketNames.EDITED_ONLINE_PHOTOS;
        } else {
            // If the first few cases didn't hit the matching path, do a
            // thorough search in the local directories.
            File extStorage = Environment.getExternalStorageDirectory();
            String path = GalleryUtils.searchDirForPath(extStorage, bucketId);
            if (path == null) {
                Log.w(TAG, "Relative path for bucket id: " + bucketId + " is not found.");
                relativePath = null;
            } else {
                relativePath = path.substring(extStorage.getAbsolutePath().length());
            }
        }
        return relativePath;
    }
}
