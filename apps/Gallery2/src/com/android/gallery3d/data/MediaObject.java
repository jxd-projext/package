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

import android.net.Uri;

public abstract class MediaObject {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/MediaObject";
    public static final long INVALID_DATA_VERSION = -1;

    // These are the bits returned from getSupportedOperations():
    public static final int SUPPORT_DELETE = 1 << 0;
    public static final int SUPPORT_ROTATE = 1 << 1;
    public static final int SUPPORT_SHARE = 1 << 2;
    public static final int SUPPORT_CROP = 1 << 3;
    public static final int SUPPORT_SHOW_ON_MAP = 1 << 4;
    public static final int SUPPORT_SETAS = 1 << 5;
    public static final int SUPPORT_FULL_IMAGE = 1 << 6;
    public static final int SUPPORT_PLAY = 1 << 7;
    public static final int SUPPORT_CACHE = 1 << 8;
    public static final int SUPPORT_EDIT = 1 << 9;
    public static final int SUPPORT_INFO = 1 << 10;
    public static final int SUPPORT_TRIM = 1 << 11;
    //added for GIF animation
    public static final int SUPPORT_GIF_ANIMATION = 1 << 12;
    //added for consume drm rights
    public static final int SUPPORT_CONSUME_DRM = 1 << 13;
    //add Blue tooth print feature
    public static final int SUPPORT_PRINT = 1 << 14;
    //add for drm protection info
    public static final int SUPPORT_DRM_INFO = 1 << 15;
    //add to display stereo image
    public static final int SUPPORT_STEREO_DISPLAY = 1 << 16;
    // add for stereo image display mode switch
    public static final int SUPPORT_SWITCHTO_2D = 1 << 17;/////////??????????????????????
    public static final int SUPPORT_SWITCHTO_3D = 1 << 18;
    //add to display stereo image
    public static final int SUPPORT_CONVERT_TO_3D = 1 << 19;
    /// M: added for ConShots
    public static final int SUPPORT_CONSHOTS_DELETE = 1 << 26;
    public static final int SUPPORT_CONSHOTS_SHARE = 1 << 27;
    public static final int SUPPORT_CONSHOTS_SAVE = 1 << 28;
    /// M: add to display PQ menu
    public static final int SUPPORT_PQ = 1 << 29;
    public static final int SUPPORT_MUTE = 1 << 20;
    public static final int SUPPORT_UNLOCK = 1 << 21;
    public static final int SUPPORT_BACK = 1 << 22;
    public static final int SUPPORT_ACTION = 1 << 23;
    public static final int SUPPORT_CAMERA_SHORTCUT = 1 << 24;
    //added for mav playback
    public static final int SUPPORT_MAV_PLAYBACK = 1 << 25;
    //M: add for stereo3d feature
    public static final int SUPPORT_CONV_TUNING = 1 << 26;
    public static final int SUPPORT_AUTO_CONV = 1 << 27;
    public static final int SUPPORT_AUTO_CONV_ON = 1 << 28;

    public static final int SUPPORT_ALL = 0xffffffff;

    // added for supporting different image/video sub-type
    public static final int SUBTYPE_PANORAMA = 1 << 0;
    public static final int SUBTYPE_MPO_MAV = 1 << 1;
    // added for supporting drm media
    public static final int SUBTYPE_DRM_NO_RIGHT = 1 << 2;
    public static final int SUBTYPE_DRM_HAS_RIGHT = 1 << 3;
    // added for supporting stereo3D
    public static final int SUBTYPE_MPO_3D = 1 << 4;
    public static final int SUBTYPE_MPO_3D_PAN = 1 << 5;
    public static final int SUBTYPE_STEREO_JPS = 1 << 6;
    public static final int SUBTYPE_STEREO_VIDEO = 1 << 7;
    // added for special effect when drawing
    public static final int SUBTYPE_WHITE_BGD = 1 << 8;
    // added for display small image at it origin size
    public static final int SUBTYPE_ORIGIN_SIZE = 1 << 9;
    /// M: added for ConShots
    public static final int SUBTYPE_CONSHOT = 1 << 10;
    public static final int SUBTYPE_MOTION = 1 << 11;
    public static final int SUBTYPE_CONTAINER = 1 << 12;
    /// M: added for LivePhoto
    public static final int SUBTYPE_LIVEPHOTO = 1 << 13;
    /// M: added for slowmotion
    public static final int SUBTYPE_SLOWMOTION = 1 << 14;

    // These are the bits returned from getMediaType():
    public static final int MEDIA_TYPE_UNKNOWN = 1;
    public static final int MEDIA_TYPE_IMAGE = 2;
    public static final int MEDIA_TYPE_VIDEO = 4;
    public static final int MEDIA_TYPE_ALL = MEDIA_TYPE_IMAGE | MEDIA_TYPE_VIDEO;

    public static final String MEDIA_TYPE_IMAGE_STRING = "image";
    public static final String MEDIA_TYPE_VIDEO_STRING = "video";
    public static final String MEDIA_TYPE_ALL_STRING = "all";

    // These are flags for cache() and return values for getCacheFlag():
    public static final int CACHE_FLAG_NO = 0;
    public static final int CACHE_FLAG_SCREENNAIL = 1;
    public static final int CACHE_FLAG_FULL = 2;

    // These are return values for getCacheStatus():
    public static final int CACHE_STATUS_NOT_CACHED = 0;
    public static final int CACHE_STATUS_CACHING = 1;
    public static final int CACHE_STATUS_CACHED_SCREENNAIL = 2;
    public static final int CACHE_STATUS_CACHED_FULL = 3;

    private static long sVersionSerial = 0;

    protected long mDataVersion;

    protected final Path mPath;
    
    public interface PanoramaSupportCallback {
        void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                boolean isPanorama360);
    }

    public MediaObject(Path path, long version) {
        path.setObject(this);
        mPath = path;
        mDataVersion = version;
    }

    public Path getPath() {
        return mPath;
    }

    public int getSupportedOperations() {
        return 0;
    }

    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        callback.panoramaInfoAvailable(this, false, false);
    }

    public void clearCachedPanoramaSupport() {
    }


    public void delete() {
        throw new UnsupportedOperationException();
    }

    public void rotate(int degrees) {
        throw new UnsupportedOperationException();
    }

    public Uri getContentUri() {
        String className = getClass().getName();
        Log.e(TAG, "Class " + className + "should implement getContentUri.");
        Log.e(TAG, "The object was created from path: " + getPath());
        throw new UnsupportedOperationException();
    }

    public Uri getPlayUri() {
        throw new UnsupportedOperationException();
    }

    public int getMediaType() {
        return MEDIA_TYPE_UNKNOWN;
    }

    public MediaDetails getDetails() {
        MediaDetails details = new MediaDetails();
        return details;
    }

    public long getDataVersion() {
        return mDataVersion;
    }

    public int getCacheFlag() {
        return CACHE_FLAG_NO;
    }

    public int getCacheStatus() {
        throw new UnsupportedOperationException();
    }

    public long getCacheSize() {
        throw new UnsupportedOperationException();
    }

    public void cache(int flag) {
        throw new UnsupportedOperationException();
    }

    public static synchronized long nextVersionNumber() {
        return ++MediaObject.sVersionSerial;
    }

    public static int getTypeFromString(String s) {
        if (MEDIA_TYPE_ALL_STRING.equals(s)) return MediaObject.MEDIA_TYPE_ALL;
        if (MEDIA_TYPE_IMAGE_STRING.equals(s)) return MediaObject.MEDIA_TYPE_IMAGE;
        if (MEDIA_TYPE_VIDEO_STRING.equals(s)) return MediaObject.MEDIA_TYPE_VIDEO;
        throw new IllegalArgumentException(s);
    }

    public static String getTypeString(int type) {
        switch (type) {
            case MEDIA_TYPE_IMAGE: return MEDIA_TYPE_IMAGE_STRING;
            case MEDIA_TYPE_VIDEO: return MEDIA_TYPE_VIDEO_STRING;
            case MEDIA_TYPE_ALL: return MEDIA_TYPE_ALL_STRING;
        }
        throw new IllegalArgumentException();
    }

    /// M: add for ClusterAlbum reload. @{
    public long synchronizedAlbumData(){
        return mDataVersion;
    }
    /// @}
}
