package com.mediatek.contacts.editor;

import android.content.Context;
import android.util.AttributeSet;

import com.android.contacts.R;
import com.android.contacts.editor.PhotoEditorView;

public class SimPhotoEditorView extends PhotoEditorView {

    public SimPhotoEditorView(Context context) {
        super(context);
    }

    public SimPhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected int getPhotoImageResource() {
        return R.drawable.mtk_ic_contact_picture_sim_contact;
    }
    
    @Override
    protected int onInflatePhotoImageId() {
        return R.id.sim_photo;
    }
    
    @Override
    protected boolean getTriangleAffordance() {
        return false;
    }
}