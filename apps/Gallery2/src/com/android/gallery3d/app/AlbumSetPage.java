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
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.settings.GallerySettings;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSetSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.HelpUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.mediatek.gallery3d.drm.DrmHelper;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.gallery3d.util.MtkLog;
import com.mediatek.gallery3d.util.MtkUtils;

public class AlbumSetPage extends ActivityState implements
        SelectionManager.SelectionListener, GalleryActionBar.ClusterRunner,
        EyePosition.EyePositionListener, MediaSet.SyncListener {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/AlbumSetPage";

    private static final int MSG_PICK_ALBUM = 1;

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_SET_TITLE = "set-title";
    public static final String KEY_SET_SUBTITLE = "set-subtitle";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";

    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_DO_ANIMATION = 1;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;

    private static final boolean IS_DRM_SUPPORTED = 
                                          MediatekFeature.isDrmSupported();
    private static final boolean IS_STEREO_DISPLAY_SUPPORTED = 
                        MediatekFeature.isStereoDisplaySupported();

    private int mMtkInclusion = 0;


    private boolean mIsActive = false;
    private SlotView mSlotView;
    private AlbumSetSlotRenderer mAlbumSetView;
    private Config.AlbumSetPage mConfig;

    private MediaSet mMediaSet;
    private String mTitle;
    private String mSubtitle;
    private boolean mShowClusterMenu;
    private GalleryActionBar mActionBar;
    private int mSelectedAction;

    protected SelectionManager mSelectionManager;
    private AlbumSetDataLoader mAlbumSetDataAdapter;

    private boolean mGetContent;
    private boolean mGetAlbum;
    private ActionModeHandler mActionModeHandler;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private boolean mShowDetails;
    private EyePosition mEyePosition;
    private Handler mHandler;

    // The eyes' position of the user, the origin is at the center of the
    // device and the unit is in pixels.
    private float mX;
    private float mY;
    private float mZ;

    private Future<Integer> mSyncTask = null;

    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;
  /// M: if get the mTitle/mSubTitle,they will not change when switch language.
    private int mClusterType = -1;

    private Button mCameraButton;
    private boolean mShowedEmptyToastForSelf = false;

    // save selection for onPause/onResume
    private boolean mNeedUpdateSelection = false;
    
    /// M: flag to specify whether mSelectionManager.restoreSelection task has done
    private boolean mRestoreSelectionDone;
    /// M: If restore selection not done in selection mode, after click one slot, show 'wait' toast
    private Toast mWaitToast = null;
    
    @Override
    protected int getBackgroundColorId() {
        return R.color.albumset_background;
    }

    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mEyePosition.resetPosition();

            int slotViewTop = mActionBar.getHeight() + mConfig.paddingTop;
            int slotViewBottom = bottom - top - mConfig.paddingBottom;
            int slotViewRight = right - left;

            if (mShowDetails) {
                mDetailsHelper.layout(left, slotViewTop, right, bottom);
            } else {
                mAlbumSetView.setHighlightItemPath(null);
            }

            mSlotView.layout(0, slotViewTop, slotViewRight, slotViewBottom);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            GalleryUtils.setViewPointMatrix(mMatrix,
                    getWidth() / 2 + mX, getHeight() / 2 + mY, mZ);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };

    @Override
    public void onEyePositionChanged(float x, float y, float z) {
        mRootPane.lockRendering();
        mX = x;
        mY = y;
        mZ = z;
        mRootPane.unlockRendering();
        mRootPane.invalidate();
    }

    @Override
    public void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    private void getSlotCenter(int slotIndex, int center[]) {
        Rect offset = new Rect();
        mRootPane.getBoundsOf(mSlotView, offset);
        Rect r = mSlotView.getSlotRect(slotIndex);
        int scrollX = mSlotView.getScrollX();
        int scrollY = mSlotView.getScrollY();
        center[0] = offset.left + (r.left + r.right) / 2 - scrollX;
        center[1] = offset.top + (r.top + r.bottom) / 2 - scrollY;
    }

    public void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;

        if (mSelectionManager.inSelectionMode()) {
            MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
            if (targetSet == null) return; // Content is dirty, we shall reload soon
            if (mRestoreSelectionDone) {
                mSelectionManager.toggle(targetSet.getPath());
                mSlotView.invalidate();
            } else {
                if (mWaitToast == null) {
                    mWaitToast = Toast.makeText(mActivity, com.android.internal.R.string.wait,
                            Toast.LENGTH_SHORT);
                }
                mWaitToast.show();
            }
        } else {
            // Show pressed-up animation for the single-tap.
            mAlbumSetView.setPressedIndex(slotIndex);
            mAlbumSetView.setPressedUp();
            /// M: send Message without 180ms delay for improving performance. @{
//          mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0), FadeTexture.DURATION);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0));
            /// @}
        }
    }

    private static boolean albumShouldOpenInFilmstrip(MediaSet album) {
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> list = (itemCount == 1) ? album.getMediaItem(0, 1) : null;
        // open in film strip only if there's one item in the album and the item exists
        return (list != null && !list.isEmpty());
    }

    WeakReference<Toast> mEmptyAlbumToast = null;

    private void showEmptyAlbumToast(int toastLength) {
        Toast toast;
        if (mEmptyAlbumToast != null) {
            toast = mEmptyAlbumToast.get();
            if (toast != null) {
                toast.show();
                return;
            }
        }
        toast = Toast.makeText(mActivity, R.string.empty_album, toastLength);
        mEmptyAlbumToast = new WeakReference<Toast>(toast);
        toast.show();
    }

    private void hideEmptyAlbumToast() {
        if (mEmptyAlbumToast != null) {
            Toast toast = mEmptyAlbumToast.get();
            if (toast != null) toast.cancel();
        }
    }

    private void pickAlbum(int slotIndex) {
        if (!mIsActive) return;

        MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon
        if (targetSet.getTotalMediaItemCount() == 0) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
            return;
        }
        hideEmptyAlbumToast();

        String mediaPath = targetSet.getPath().toString();

        Bundle data = new Bundle(getData());
        int[] center = new int[2];
        getSlotCenter(slotIndex, center);
        data.putIntArray(AlbumPage.KEY_SET_CENTER, center);
        if (mGetAlbum && targetSet.isLeafAlbum()) {
            Activity activity = mActivity;
            Intent result = new Intent()
                    .putExtra(AlbumPicker.KEY_ALBUM_PATH, targetSet.getPath().toString());
            MediatekFeature.insertBucketIdForPickActions(targetSet, result);
            activity.setResult(Activity.RESULT_OK, result);
            activity.finish();
        } else if (targetSet.getSubMediaSetCount() > 0) {
            data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
                //add for DRM feature: pass drm inclusio info to next ActivityState
                if (IS_DRM_SUPPORTED || IS_STEREO_DISPLAY_SUPPORTED) {
                    data.putInt(DrmHelper.DRM_INCLUSION, mMtkInclusion);
                }
            mActivity.getStateManager().startStateForResult(
                    AlbumSetPage.class, REQUEST_DO_ANIMATION, data);
        } else {
            if (!mGetContent && albumShouldOpenInFilmstrip(targetSet)) {
                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
                        mSlotView.getSlotRect(slotIndex, mRootPane));
                data.putInt(PhotoPage.KEY_INDEX_HINT, 0);
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                        mediaPath);
                data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, true);
                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetSet.isCameraRoll());
                /// M: add for DRM feature: pass drm inclusio info to next ActivityState
                if (IS_DRM_SUPPORTED || IS_STEREO_DISPLAY_SUPPORTED) {
                    data.putInt(DrmHelper.DRM_INCLUSION, mMtkInclusion);
                }
                mActivity.getStateManager().startStateForResult(
                        FilmstripPage.class, AlbumPage.REQUEST_PHOTO, data);
                return;
            }
            data.putString(AlbumPage.KEY_MEDIA_PATH, mediaPath);

            // We only show cluster menu in the first AlbumPage in stack
            boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
            data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum);
                //add for DRM feature: pass drm inclusio info to next ActivityState
                if (IS_DRM_SUPPORTED || IS_STEREO_DISPLAY_SUPPORTED) {
                    data.putInt(DrmHelper.DRM_INCLUSION, mMtkInclusion);
                }
            mActivity.getStateManager().startStateForResult(
                    AlbumPage.class, REQUEST_DO_ANIMATION, data);
            ///M: avoid show selected icon when back from album page
            mAlbumSetView.setPressedIndex(-1);
        }
    }

    private void onDown(int index) {
        mAlbumSetView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSetView.setPressedIndex(-1);
        } else {
            mAlbumSetView.setPressedUp();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent || mGetAlbum) return;
        MediaSet set = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (set == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(set.getPath());
        mSlotView.invalidate();
    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        Log.d(TAG, "doCluster, the new path is: " + newPath);
        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(KEY_SELECTED_CLUSTER_TYPE, clusterType);
        //add for DRM feature: pass drm inclusio info to next ActivityState
        if (IS_DRM_SUPPORTED || IS_STEREO_DISPLAY_SUPPORTED) {
            data.putInt(DrmHelper.DRM_INCLUSION, mMtkInclusion);
        }
        mActivity.getStateManager().switchState(this, AlbumSetPage.class, data);
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        MtkUtils.traceStart("AlbumSetPage:onCreate");
        super.onCreate(data, restoreState);
        initializeViews();
        initializeData(data);

        // M: for performance auto test
        mInitialized = true;
        
        Context context = mActivity.getAndroidContext();
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mGetAlbum = data.getBoolean(GalleryActivity.KEY_GET_ALBUM, false);
        mTitle = data.getString(AlbumSetPage.KEY_SET_TITLE);
      /// M: get clustertype here,if get the mTitle/mSubTitle,they will not change when switch language. @{
//        mSubtitle = data.getString(AlbumSetPage.KEY_SET_SUBTITLE);
        mClusterType = data.getInt(AlbumSetPage.KEY_SET_SUBTITLE);
        /// }@
        mEyePosition = new EyePosition(context, this);
        mDetailsSource = new MyDetailsSource();
        mActionBar = mActivity.getGalleryActionBar();
        mSelectedAction = data.getInt(AlbumSetPage.KEY_SELECTED_CLUSTER_TYPE,
                FilterUtils.CLUSTER_BY_ALBUM);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_ALBUM: {
                        pickAlbum(message.arg1);
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };
        MtkUtils.traceEnd("AlbumSetPage:onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupCameraButton();
        mActionModeHandler.destroy();
    }

    private boolean setupCameraButton() {
        if (!GalleryUtils.isCameraAvailable(mActivity)) return false;
        RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity)
                .findViewById(R.id.gallery_root);
        if (galleryRoot == null) return false;

        mCameraButton = new Button(mActivity);
        mCameraButton.setText(R.string.camera_label);
        mCameraButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.frame_overlay_gallery_camera, 0, 0);
        mCameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                GalleryUtils.startCameraActivity(mActivity);
            }
        });
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        galleryRoot.addView(mCameraButton, lp);
        return true;
    }

    private void cleanupCameraButton() {
        if (mCameraButton == null) return;
        RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity)
                .findViewById(R.id.gallery_root);
        if (galleryRoot == null) return;
        galleryRoot.removeView(mCameraButton);
        mCameraButton = null;
    }

    private void showCameraButton() {
        if (mCameraButton == null && !setupCameraButton()) return;
        mCameraButton.setVisibility(View.VISIBLE);
    }

    private void hideCameraButton() {
        if (mCameraButton == null) return;
        mCameraButton.setVisibility(View.GONE);
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumSetDataAdapter.size() == 0) {
                // If this is not the top of the gallery folder hierarchy,
                // tell the parent AlbumSetPage instance to handle displaying
                // the empty album toast, otherwise show it within this
                // instance
                if (mActivity.getStateManager().getStateCount() > 1) {
                    Intent result = new Intent();
                    result.putExtra(AlbumPage.KEY_EMPTY_ALBUM, true);
                    setStateResult(Activity.RESULT_OK, result);
                    mActivity.getStateManager().finishState(this);
                } else {
                    mShowedEmptyToastForSelf = true;
                    showEmptyAlbumToast(Toast.LENGTH_LONG);
                    mSlotView.invalidate();
                    showCameraButton();
                }
                return;
            }
        }
        // Hide the empty album toast if we are in the root instance of
        // AlbumSetPage and the album is no longer empty (for instance,
        // after a sync is completed and web albums have been synced)
        if (mShowedEmptyToastForSelf) {
            mShowedEmptyToastForSelf = false;
            hideEmptyAlbumToast();
            hideCameraButton();
        }
    }

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            mSelectionManager.saveSelection();
            mNeedUpdateSelection = false;
        }
        
        mAlbumSetDataAdapter.pause();
        mAlbumSetView.pause();
        mActionModeHandler.pause();
        mEyePosition.pause();
        DetailsHelper.pause();
        // Call disableClusterMenu to avoid receiving callback after paused.
        // Don't hide menu here otherwise the list menu will disappear earlier than
        // the action bar, which is janky and unwanted behavior.
        mActionBar.disableClusterMenu(false);
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
    }

    @Override
    public void onResume() {
        MtkUtils.traceStart("AlbumSetPage:onResume");
        super.onResume();
        mIsActive = true;
        setContentPane(mRootPane);

        // Set the reload bit here to prevent it exit this page in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            mNeedUpdateSelection = true;
            /// M: set mRestoreSelectionDone as false if we need to retore selection
            mRestoreSelectionDone = false;
        } else {
            /// M: set mRestoreSelectionDone as true there is no need to retore selection
            mRestoreSelectionDone = true;
        }
        mAlbumSetDataAdapter.resume();

        mAlbumSetView.resume();
        mEyePosition.resume();
        mActionModeHandler.resume();
        if (mShowClusterMenu) {
            mActionBar.enableClusterMenu(mSelectedAction, this);
        }
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(AlbumSetPage.this);
        }
        MtkUtils.traceEnd("AlbumSetPage:onResume");
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(AlbumSetPage.KEY_MEDIA_PATH);
        if(data.getBoolean(CropExtras.KEY_SET_AS_WALLPAPER, false)) {
            mMtkInclusion = MediatekFeature.getMavInclusionFromData(data);
        }
        if (IS_DRM_SUPPORTED || IS_STEREO_DISPLAY_SUPPORTED) {
            mMtkInclusion |= MediatekFeature.getInclusionFromData(data);
        }
        Log.i(TAG,"initializeDAta:mMtkInclusion="+mMtkInclusion);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath,
                mMtkInclusion);
        
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter = new AlbumSetDataLoader(
                mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSetDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);

        mConfig = Config.AlbumSetPage.get(mActivity);
        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
        mAlbumSetView = new AlbumSetSlotRenderer(
                mActivity, mSelectionManager, mSlotView, mConfig.labelSpec,
                mConfig.placeholderColor);
        mSlotView.setSlotRenderer(mAlbumSetView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSetPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSetPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSetPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumSetPage.this.onLongTap(slotIndex);
            }
        });

        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
            
            public boolean onPopUpItemClicked(int itemId) {
                /// M: return if restoreSelection has done
                return mRestoreSelectionDone;
            }
        });
        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Activity activity = mActivity;
        final boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
        MenuInflater inflater = getSupportMenuInflater();

        if (mGetContent) {
            inflater.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(
                    GalleryActivity.KEY_TYPE_BITS, DataManager.INCLUDE_IMAGE);
            mActionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else  if (mGetAlbum) {
            inflater.inflate(R.menu.pickup, menu);
            mActionBar.setTitle(R.string.select_album);
        } else {
            inflater.inflate(R.menu.albumset, menu);
            boolean wasShowingClusterMenu = mShowClusterMenu;
            mShowClusterMenu = !inAlbum;
            boolean selectAlbums = !inAlbum &&
                    mActionBar.getClusterTypeAction() == FilterUtils.CLUSTER_BY_ALBUM;
            MenuItem selectItem = menu.findItem(R.id.action_select);
            selectItem.setTitle(activity.getString(
                    selectAlbums ? R.string.select_album : R.string.select_group));

            MenuItem cameraItem = menu.findItem(R.id.action_camera);
            cameraItem.setVisible(GalleryUtils.isCameraAvailable(activity));

            FilterUtils.setupMenuItems(mActionBar, mMediaSet.getPath(), false);

            Intent helpIntent = HelpUtils.getHelpIntent(activity);

            MenuItem helpItem = menu.findItem(R.id.action_general_help);
            helpItem.setVisible(helpIntent != null);
            if (helpIntent != null) helpItem.setIntent(helpIntent);
          /// M: if get the mTitle/mSubTitle,they will not change when switch language. @{
            if (mTitle != null) {
            mTitle = mMediaSet.getName();
            mSubtitle = GalleryActionBar.getClusterByTypeString(mActivity, mClusterType);
            }
         /// }@
            mActionBar.setTitle(mTitle);
            mActionBar.setSubtitle(mSubtitle);
            if (mShowClusterMenu != wasShowingClusterMenu) {
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                } else {
                    mActionBar.disableClusterMenu(true);
                }
            }
        }
        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        Activity activity = mActivity;
        switch (item.getItemId()) {
            case R.id.action_cancel:
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_details:
                if (mAlbumSetDataAdapter.size() != 0) {
                    if (mShowDetails) {
                        hideDetails();
                    } else {
                        showDetails();
                    }
                } else {
                    Toast.makeText(activity,
                            activity.getText(R.string.no_albums_alert),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(activity);
                return true;
            }
            // M: comment out unavailable options for non-GMS version Gallery2
//            case R.id.action_manage_offline: {
//                Bundle data = new Bundle();
//                String mediaPath = mActivity.getDataManager().getTopSetPath(
//                    DataManager.INCLUDE_ALL);
//                data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
//                mActivity.getStateManager().startState(ManageCachePage.class, data);
//                return true;
//            }
//            case R.id.action_sync_picasa_albums: {
//                PicasaSource.requestSync(activity);
//                return true;
//            }
//            case R.id.action_settings: {
//                activity.startActivity(new Intent(activity, GallerySettings.class));
//                return true;
//            }
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        ///M: no need show toast
        //if (data != null && data.getBooleanExtra(AlbumPage.KEY_EMPTY_ALBUM, false)) {
        //    showEmptyAlbumToast(Toast.LENGTH_SHORT);
        //}
        switch (requestCode) {
            case REQUEST_DO_ANIMATION: {
                mSlotView.startRisingAnimation();
            }
        }
    }

    public String getSelectedString() {
        int count = mSelectionManager.getSelectedCount();
        int action = mActionBar.getClusterTypeAction();
        int string = action == FilterUtils.CLUSTER_BY_ALBUM
                ? R.plurals.number_of_albums_selected
                : R.plurals.number_of_groups_selected;
        String format = mActivity.getResources().getQuantityString(string, count);
        return String.format(format, count);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionBar.disableClusterMenu(true);
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                }
                mRootPane.invalidate();
                break;
            }
            // M: when click deselect all in menu, not leave selection mode
            case SelectionManager.DESELECT_ALL_MODE:
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation(path, selected);
    }
    
    @Override
    public void onSelectionRestoreDone() {
        mRestoreSelectionDone = true;
        /// M: update selection menu after restore done @{
        mActionModeHandler.updateSupportedOperation();
        mActionModeHandler.updateSelectionMenu();
        /// @}
    }
    
    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumSetView.setHighlightItemPath(null);
        mSlotView.invalidate();
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        if (resultCode == MediaSet.SYNC_RESULT_ERROR) {
            Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                    + resultCode);
        }
        ((Activity) mActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    if (resultCode == MediaSet.SYNC_RESULT_ERROR && mIsActive) {
                        Log.w(TAG, "failed to load album set");
                    }
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            // M: for performance auto test
            mLoadingFinished = false;
            
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            // M: for performance auto test
            mLoadingFinished = true;

            clearLoadingBit(BIT_LOADING_RELOAD);
            
            // M: we have to notify SelectionManager about data change,
            // and this is the most proper place we could find till now
            boolean inSelectionMode = (mSelectionManager != null && mSelectionManager.inSelectionMode());
            int setCount = mMediaSet != null ? mMediaSet.getSubMediaSetCount() : 0;
            MtkLog.d(TAG, "onLoadingFinished: set count=" + setCount);
            MtkLog.d(TAG, "onLoadingFinished: inSelectionMode=" + inSelectionMode);
            mSelectionManager.onSourceContentChanged();
            boolean restore = false;
            if (setCount > 0 && inSelectionMode) {
                if (mNeedUpdateSelection) {
                    mNeedUpdateSelection = false;
                    restore = true;
                    mSelectionManager.restoreSelection();
                }
                mActionModeHandler.updateSupportedOperation();
                mActionModeHandler.updateSelectionMenu();
            }
            if(!restore) {
                mRestoreSelectionDone = true;
            }
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumSetDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mIndex = mAlbumSetDataAdapter.findSet(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mAlbumSetDataAdapter.getMediaSet(mIndex);
            if (item != null) {
                mAlbumSetView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }
    
    // M: for performance auto test
    public boolean mLoadingFinished = false;
    public boolean mInitialized = false;
}
