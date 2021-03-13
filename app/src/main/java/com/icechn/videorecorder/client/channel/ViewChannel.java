package com.icechn.videorecorder.client.channel;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

public class ViewChannel {

    private static final String TAG = "view";

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Canvas mSurfaceCanvas;

    public void setSurfaceTexture(SurfaceTexture surfaceTexture, int width, int height) {
        this.mSurfaceTexture = surfaceTexture;
        this.mSurfaceTexture.setDefaultBufferSize(width, height);
        this.mSurface = new Surface(mSurfaceTexture);
        Canvas canvas = mSurface.lockCanvas(null);
        if (canvas != null) {
            canvas.drawColor(Color.TRANSPARENT);
            mSurface.unlockCanvasAndPost(canvas);
        }
    }

    public void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;
    }

    public Canvas onDrawViewBegin() {
        mSurfaceCanvas = null;
        if (mSurface != null) {
            try {
                mSurfaceCanvas = mSurface.lockCanvas(null);
            } catch (Exception e) {
                Log.e(TAG, "error while rendering view to gl: " + e);
            }
        }
        return mSurfaceCanvas;
    }

    public void onDrawViewEnd() {
        if (mSurfaceCanvas != null) {
            mSurface.unlockCanvasAndPost(mSurfaceCanvas);
        }
        mSurfaceCanvas = null;
    }

}
