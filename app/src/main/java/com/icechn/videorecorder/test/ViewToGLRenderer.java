package com.icechn.videorecorder.test;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.Surface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 获取当前 EGLContext EGL14.eglGetCurrentContext()
 *
 * Created by user on 3/12/15.
 */
public class ViewToGLRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = ViewToGLRenderer.class.getSimpleName();

    private static final int DEFAULT_TEXTURE_WIDTH = 500;
    private static final int DEFAULT_TEXTURE_HEIGHT = 500;

    private int mGlSurfaceTexture;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Canvas mSurfaceCanvas;
    private EGLContext mEGLContext;

    private int mTextureWidth = DEFAULT_TEXTURE_WIDTH;
    private int mTextureHeight = DEFAULT_TEXTURE_HEIGHT;

    private ISurface mISurface;


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        Log.d(TAG, extensions);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        releaseSurface();
        mGlSurfaceTexture = createTexture();
        if (mGlSurfaceTexture > 0) {
            //attach the texture to a surface.
            //It's a clue class for rendering an android view to gl level
            mSurfaceTexture = new SurfaceTexture(mGlSurfaceTexture);
            mSurfaceTexture.setDefaultBufferSize(width, height);
            mSurface = new Surface(mSurfaceTexture);
            Canvas canvas = mSurface.lockCanvas(null);
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT);
                mSurface.unlockCanvasAndPost(canvas);
            }
            mEGLContext = EGL14.eglGetCurrentContext();
            if (mISurface != null) {
                mISurface.onSurfaceTextureCreated();
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            // update texture
            mSurfaceTexture.updateTexImage();
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


    private int createTexture() {
        int[] textures = new int[1];

        // Generate the texture to where android view will be rendered
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, textures, 0);
        checkGlError("Texture generate");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        checkGlError("Texture bind");
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return textures[0];
    }

    public int getGLSurfaceTextureID() {
        return mGlSurfaceTexture;
    }

    public SurfaceTexture getGLSurfaceTexture() {
        return mSurfaceTexture;
    }

    public EGLContext getEGLContext() {
        return mEGLContext;
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


    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + GLUtils.getEGLErrorString(error));
        }
    }


    public int getTextureWidth() {
        return mTextureWidth;
    }

    public void setTextureWidth(int textureWidth) {
        mTextureWidth = textureWidth;
    }

    public int getTextureHeight() {
        return mTextureHeight;
    }

    public void setTextureHeight(int textureHeight) {
        mTextureHeight = textureHeight;
    }

    public void setISurface(ISurface iSurface) {
        mISurface = iSurface;
    }
    public interface ISurface {
        void onSurfaceTextureCreated();
    }

}