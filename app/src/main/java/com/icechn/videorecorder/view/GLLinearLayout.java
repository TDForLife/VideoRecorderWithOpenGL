package com.icechn.videorecorder.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.icechn.videorecorder.client.channel.ViewChannel;

/**
 * Created by user on 3/15/15.
 */
public class GLLinearLayout extends LinearLayout implements IGLRender {

    private ViewChannel mViewChannel;

    public GLLinearLayout(Context context) {
        super(context);
    }

    public GLLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GLLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mViewChannel == null) {
            return;
        }
        Canvas glAttachedCanvas = mViewChannel.onDrawViewBegin();
        if (glAttachedCanvas != null) {
            glAttachedCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            super.draw(glAttachedCanvas);
        }
        // notify the canvas is updated
        mViewChannel.onDrawViewEnd();
    }

    @Override
    public void setViewViewChannel(ViewChannel viewChannel) {
        mViewChannel = viewChannel;
    }
}
