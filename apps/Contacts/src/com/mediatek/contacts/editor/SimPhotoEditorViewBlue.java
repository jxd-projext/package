package com.mediatek.contacts.editor;

import android.content.Context;
import android.util.AttributeSet;

import com.android.contacts.R;

public class SimPhotoEditorViewBlue extends SimPhotoEditorView {

    public SimPhotoEditorViewBlue(Context context) {
        super(context);
    }

    public SimPhotoEditorViewBlue(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int getPhotoImageResource() {
        return R.drawable.mtk_ic_contact_picture_sim_contact_blue;
    }
}