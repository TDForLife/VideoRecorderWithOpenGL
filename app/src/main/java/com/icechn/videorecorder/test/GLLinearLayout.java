package com.icechn.videorecorder.test;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

/**
 * Created by user on 3/15/15.
 */
public class GLLinearLayout extends LinearLayout implements  GLRenderable {

    private ViewToGLRenderer mViewToGLRenderer;

    // default constructors

    public GLLinearLayout(Context context) {
        super(context);
    }

    public GLLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public GLLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // drawing magic
    @Override
    public void draw(Canvas canvas) {
        Log.d("zwt", "GLLinearLayout draw...");
        if (mViewToGLRenderer == null) {
            super.draw(canvas);
            return;
        }
        Canvas glAttachedCanvas = mViewToGLRenderer.onDrawViewBegin();
        if(glAttachedCanvas != null) {
            glAttachedCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//            float xScale = glAttachedCanvas.getWidth() / (float)canvas.getWidth();
//            glAttachedCanvas.scale(xScale, xScale);
            super.draw(glAttachedCanvas);
        }
        mViewToGLRenderer.onDrawViewEnd();
    }

    public void setViewToGLRenderer(ViewToGLRenderer viewToGLRenderer){
        mViewToGLRenderer = viewToGLRenderer;
    }
}
