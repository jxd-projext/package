/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.app.Application;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Process;

import com.android.launcher.R;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.launcher2.ext.LauncherLog;

import java.lang.ref.WeakReference;
import java.util.List;

public class LauncherApplication extends Application {
    private static final String TAG = "LauncherApplication";
    private LauncherModel mModel;
    private IconCache mIconCache;
    private WidgetPreviewLoader.CacheDb mWidgetPreviewCacheDb;
    private static boolean sIsScreenLarge;
    private static float sScreenDensity;
    private static int sLongPressTimeout = 300;
    private static final String sSharedPreferencesKey = "com.android.launcher2.prefs";
    WeakReference<LauncherProvider> mLauncherProvider;

    /// M: added for unread feature.
    private MTKUnreadLoader mUnreadLoader;

    /// M: flag for starting Launcher from application
    private boolean mTotallyStart = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // set sIsScreenXLarge and sScreenDensity *before* creating icon cache
        sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
        sScreenDensity = getResources().getDisplayMetrics().density;

        mWidgetPreviewCacheDb = new WidgetPreviewLoader.CacheDb(this);
        mIconCache = new IconCache(this);
        mModel = new LauncherModel(this, mIconCache);

        // Register intent receivers
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
        registerReceiver(mModel, filter);

        /// M: register unread broadcast.
        if (FeatureOption.MTK_LAUNCHER_UNREAD_SUPPORT) {
            mUnreadLoader = new MTKUnreadLoader(getApplicationContext());
            // Register unread change broadcast.
            filter = new IntentFilter();
            filter.addAction(Intent.MTK_ACTION_UNREAD_CHANGED);
            registerReceiver(mUnreadLoader, filter);
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "LauncherApplication onCreate: mIconCache = " + mIconCache + ", mModel = "
                    + mModel + ", mUnreadLoader = " + mUnreadLoader + ", this = " + this);
        }

        // Register for changes to the favorites
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true,
                mFavoritesObserver);

        /// M: set start flag
        setTotalStartFlag();
    }

    /**
     * There's no guarantee that this function is ever called.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();

        unregisterReceiver(mModel);
        /// M: added for unread feature, unregister unread receiver.
        if (FeatureOption.MTK_LAUNCHER_UNREAD_SUPPORT) {
            unregisterReceiver(mUnreadLoader);
        }

        ContentResolver resolver = getContentResolver();
        resolver.unregisterContentObserver(mFavoritesObserver);
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private final ContentObserver mFavoritesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "mFavoritesObserver onChange: selfChange = " + selfChange);
            }

            /// M: Ignore the loading database process when is installing shortcut,
            ///    trigger it manually later due to the process may have more than one installation.
            ///    Just decrease the installed shortcut for successful one @{
            if (InstallShortcutHelper.isInstallingShortcut()) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "mFavoritesObserver onChange: is installing shortcut, so decrease the install count and return");
                }
                InstallShortcutHelper.decreaseInstallingCount(getApplicationContext(), true);
                return;
            }
            /// M: }@

            // If the database has ever changed, then we really need to force a reload of the
            // workspace on the next load
            mModel.resetLoadedState(false, true);
            mModel.startLoaderFromBackground();
        }
    };

    LauncherModel setLauncher(Launcher launcher) {
        mModel.initialize(launcher);
        /// M: added for unread feature, initialize unread loader.
        if (FeatureOption.MTK_LAUNCHER_UNREAD_SUPPORT) {
            mUnreadLoader.initialize(launcher);
        }
        return mModel;
    }

    IconCache getIconCache() {
        return mIconCache;
    }

    LauncherModel getModel() {
        return mModel;
    }

    WidgetPreviewLoader.CacheDb getWidgetPreviewCacheDb() {
        return mWidgetPreviewCacheDb;
    }

   void setLauncherProvider(LauncherProvider provider) {
        mLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    LauncherProvider getLauncherProvider() {
        return mLauncherProvider.get();
    }

    public static String getSharedPreferencesKey() {
        return sSharedPreferencesKey;
    }

    public static boolean isScreenLarge() {
        return sIsScreenLarge;
    }

    public static boolean isScreenLandscape(Context context) {
        return context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }

    public static float getScreenDensity() {
        return sScreenDensity;
    }

    public static int getLongPressTimeout() {
        return sLongPressTimeout;
    }

    /**
     * M: Get unread loader, added for unread feature.
     */
    public MTKUnreadLoader getUnreadLoader() {
        return mUnreadLoader;
    }

    /// M: trigger the loading database manually @{
    public void triggerLoadingDatabaseManually() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "triggerLoadingDatabaseManually");
        }
        mModel.resetLoadedState(false, true);
        mModel.startLoaderFromBackground();
    }
    /// M: }@

    /// M: Add for smart book feature. Re-init static variables. @{
    public void reInitStatics() {
        sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
        sScreenDensity = getResources().getDisplayMetrics().density;
    }
    /// M: }@

    /// M: LauncherApplication start flag @{
    public void setTotalStartFlag() {
        mTotallyStart = true;
    }

    public void resetTotalStartFlag() {
        mTotallyStart = false;
    }

    public boolean isTotalStart() {
        return mTotallyStart;
    }
    /// M: }@
}
