package com.mediatek.contacts.calloption;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.mediatek.calloption.SimPickerAdapter;
import com.mediatek.contacts.util.LogUtils;

public class ContactsSimPickerAdapter extends SimPickerAdapter {
    private static final String TAG = "ContactsSimPickerAdapter";
    
    public ContactsSimPickerAdapter(Context context, long suggestedSimId, boolean isMultiSim) {
        super(context, suggestedSimId, isMultiSim);
    }

    protected View createView(SimPickerAdapter.ViewHolder holder, final int viewType) {
        LogUtils.d(TAG, "[createView]");
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = null;
        if (viewType == SimPickerAdapter.ITEM_TYPE_SIM) {
            view = inflater.inflate(R.layout.mtk_sim_picker_item, null);
            holder.mSimSignal = (TextView)view.findViewById(R.id.simSignal);
            holder.mSimStatus = (ImageView)view.findViewById(R.id.simStatus);
            holder.mShortPhoneNumber = (TextView)view.findViewById(R.id.shortPhoneNumber);
            holder.mDisplayName = (TextView)view.findViewById(R.id.displayName);
            holder.mPhoneNumber = (TextView)view.findViewById(R.id.phoneNumber);
            holder.mSimIcon = view.findViewById(R.id.simIcon);
            holder.mSuggested = (ImageView)view.findViewById(R.id.suggested);
        } else if (viewType == SimPickerAdapter.ITEM_TYPE_INTERNET) {
            view = inflater.inflate(R.layout.mtk_sim_picker_item_internet, null);
            holder.mInternetIcon = (ImageView)view.findViewById(R.id.internetIcon);
        } else if (viewType == SimPickerAdapter.ITEM_TYPE_TEXT
                || viewType == SimPickerAdapter.ITEM_TYPE_ACCOUNT) {
            view = inflater.inflate(R.layout.mtk_sim_picker_item_text, null);
            holder.mText = (TextView)view.findViewById(R.id.text);
        }
        return view;
    }
}
