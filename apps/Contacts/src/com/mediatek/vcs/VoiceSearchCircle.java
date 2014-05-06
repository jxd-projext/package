package com.mediatek.vcs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.util.Log;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Iterator;

import com.android.contacts.R;
import com.mediatek.xlog.Xlog;

public class VoiceSearchCircle extends View {

    private static final String TAG = "VoiceSearchCircle";

    private final Context mContext;
    private ArrayList<SubCircle> mSubCircleList;

    private int mWidth;
    private int mHeight;
    private float mBitmapRadius;
    private float mOriginalRadius;
    private boolean mDrawLastCircle;
    private boolean mStopDrawCircle;

    private static final int TOTAL_TIME = 500; // ms
    private static final int DELAY = 33; // ms
    private static final int MAX_RADIUS = 70; // dp
    private static final int CIRCLE_STROKE_WIDTH = 3;

    private float mMaxRadius;
    private static final int MSG_DRAW = 0;

    CircleDrawListener mDrawListener;

    private float mVelocity;

    public VoiceSearchCircle(Context context) {
        this(context, null);
    }

    public VoiceSearchCircle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mSubCircleList = new ArrayList<SubCircle>();
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_voice_search);
        mBitmapRadius = bitmap.getWidth() / 2;
        mOriginalRadius = mBitmapRadius + dip2px(4);
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        Xlog.v(TAG, "onWindowFocusChanged");
        mWidth = getWidth();
        mHeight = getHeight();
        mMaxRadius = dip2px(MAX_RADIUS);
        mVelocity = (mMaxRadius / TOTAL_TIME);
        Xlog.v(TAG, "onDraw: + mMaxRadius: " + mMaxRadius + "getWidth(): " + getWidth() + "getHeight(): " + getHeight()
                + "mVelocity: " + mVelocity);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mStopDrawCircle) {
            Xlog.v(TAG, "Stop draw..");
            return;
        }
        
        if (mWidth > 0 && mHeight > 0) {
            Xlog.v(TAG, "MyCircle() mRadius: " + mOriginalRadius + " ,getWidth()" + mWidth + " ,getHeight(): " + mHeight);
            if (mSubCircleList.size() <= 0) {
                mSubCircleList.add(generateSubCircle());
            }
            for (int i = 0; i < mSubCircleList.size(); i++) {
                SubCircle circle = mSubCircleList.get(i);
                int count = (TOTAL_TIME / DELAY) / 2;

                float radius = mOriginalRadius + count * mVelocity * DELAY;
                Xlog.v(TAG, "onDraw: circle.mCount : " + circle.mCount + " count: " + count + "radius: " + radius);
                circle.onDraw(canvas);
                if (circle.mRadius >= mMaxRadius + mOriginalRadius) {
                    mSubCircleList.remove(circle);
                } else if (circle.mRadius == radius && !mDrawLastCircle) {
                    Xlog.v(TAG, "onDraw: circle.mRadius == radius " + radius);
                    mSubCircleList.add(generateSubCircle());
                }
            }
        }

        if (mDrawLastCircle && mSubCircleList.size() <= 0) {
            // tell search panel moved
            setVisibility(View.INVISIBLE);
            mDrawLastCircle = false;
            mDrawListener.circleDrawDone();
        } else {
            mHander.sendEmptyMessageDelayed(MSG_DRAW, DELAY);
        }
    }

    interface CircleDrawListener {
        /**
         * Means the last circle draw done
         */
        void circleDrawDone();
    }

    private SubCircle generateSubCircle() {
        float searchImageMargin = mContext.getResources().getDimension(R.dimen.vcs_search_image_margin);
        float searchPanelWidth = mContext.getResources().getDimension(R.dimen.vcs_people_row_width);
        float cx = (getWidth() - searchPanelWidth) / 2 + searchImageMargin + mBitmapRadius;
        float cy = getHeight() / 2;
        SubCircle circle = new SubCircle(mContext);
        circle.mCx = cx;
        circle.mCy = cy;
        circle.mRadius = mOriginalRadius;
        //Xlog.v(TAG, "cx: " + cx + " ,cy:" + cy);
        return circle;
    }

    public Handler mHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (MSG_DRAW == msg.what) {
                invalidate();
            }
        }
    };

    protected void show() {
        mStopDrawCircle = false;
        setVisibility(View.VISIBLE);
    }

    protected void dismiss() {
        Xlog.v(TAG, "dismiss()");
        mStopDrawCircle = true;
        setVisibility(View.INVISIBLE);
    }

    protected void drawLastCircle() {
        mDrawLastCircle = true;
    }
    
    protected int dip2px(float dpValue) {
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    class SubCircle {

        public int mCount;
        private Paint mPaint;
        private float mCx;
        private float mCy;
        private float mRadius;

        public SubCircle(Context context) {
            mPaint = new Paint();
            mPaint.setColor(mContext.getResources().getColor(R.color.vcs_people_name));
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(CIRCLE_STROKE_WIDTH);
        }

        protected void onDraw(Canvas canvas) {
            float alpha0 = 255f - mCount * (255f / TOTAL_TIME * DELAY);
            int alpha = (int) alpha0;
            Xlog.v(TAG, "alpha0: " + alpha0 + " ,alpha:" + alpha);
            mPaint.setAlpha(alpha >= 0 ? alpha : 0);
            mRadius = mOriginalRadius + mCount * mVelocity * DELAY;
            Xlog.v(TAG, "SubCircle onDraw radius: " + mRadius);
            if (mRadius <= mMaxRadius + mOriginalRadius) {
                canvas.drawCircle(mCx, mCy, mRadius, mPaint);
            }
            mCount++;
        }
    }
}
