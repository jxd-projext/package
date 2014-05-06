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

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;

import com.android.gallery3d.R;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.util.GalleryUtils;
import com.mediatek.gallery3d.drm.DrmHelper;

public class PickerActivity extends AbstractGalleryActivity
        implements OnClickListener {

    public static final String KEY_ALBUM_PATH = "album-path";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We show the picker in two ways. One smaller screen we use a full
        // screen window with an action bar. On larger screen we use a dialog.
        boolean isDialog = getResources().getBoolean(R.bool.picker_is_dialog);

        if (!isDialog) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR);
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        }

        setContentView(R.layout.dialog_picker);

        if (isDialog) {
            // In dialog mode, we don't have the action bar to show the
            // "cancel" action, so we show an additional "cancel" button.
            View view = findViewById(R.id.cancel);
            view.setOnClickListener(this);
            view.setVisibility(View.VISIBLE);
            // M: hide the divider line as well
            view = findViewById(R.id.divider);
            view.setVisibility(View.VISIBLE);

            // We need this, otherwise the view will be dimmed because it
            // is "behind" the dialog.
            // M: comment this out since it will overlap the "no albums" hint in AlbumSetPage;
            // temporarily we do not find the dimmed behavior mentioned above
            // even if we do not call this method
            //((GLRootView) findViewById(R.id.gl_root_view)).setZOrderOnTop(true);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pickup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_cancel) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.cancel) finish();
    }
}
