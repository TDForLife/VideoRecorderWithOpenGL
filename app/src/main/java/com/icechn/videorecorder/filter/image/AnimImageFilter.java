package com.icechn.videorecorder.filter.image;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.icechn.videorecorder.filter.hardvideofilter.BaseHardVideoFilter;
import com.icechn.videorecorder.tools.GLESTools;

import java.nio.FloatBuffer;

/**
 * Created by ICE on 2017/10/12.
 */

public class AnimImageFilter extends BaseHardVideoFilter {

    private static final String TAG = "anim";

    private int glProgram;
    private int glCamTextureLoc;
    private int glCamPositionLoc;
    private int glCamTextureCoordLoc;
    private int glImageTextureLoc;
    private int glImageRectLoc;
    private int glImageAngelLoc;

    private Context mContext;
    private View mAreaView;
    private View mAnimationView;
    private ImageAnimationData mAnimationData;
    private ImageTexture mImageTexture;

    public AnimImageFilter(Context context, View areaView, View animView, ImageAnimationData animationData) {
        super();
        mContext = context;
        mAreaView = areaView;
        mAnimationView = animView;
        mAnimationData = animationData;
        mAreaView.setDrawingCacheEnabled(true);
    }

    @Override
    public void onChildrenInit(int videoWidth, int videoHeight) {
        String vertexShaderCode = GLESTools.getResourceContent(mContext.getResources(), "drawimage_vertex.sh");
        String fragmentShaderCode = GLESTools.getResourceContent(mContext.getResources(), "drawimage_fragment.sh");
        glProgram = GLESTools.createProgram(vertexShaderCode, fragmentShaderCode);
        GLES20.glUseProgram(glProgram);

        glCamPositionLoc = GLES20.glGetAttribLocation(glProgram, "aCamPosition");
        glCamTextureCoordLoc = GLES20.glGetAttribLocation(glProgram, "aCamTextureCoord");

        glCamTextureLoc = GLES20.glGetUniformLocation(glProgram, "uCamTexture");
        glImageTextureLoc = GLES20.glGetUniformLocation(glProgram, "uImageTexture");

        glImageRectLoc = GLES20.glGetUniformLocation(glProgram, "imageRect");
        glImageAngelLoc = GLES20.glGetUniformLocation(glProgram, "imageAngel");

        initImageTexture();
    }

    protected void initImageTexture() {
        Bitmap initBitmap = getViewCacheBitmap(mAreaView);
        if (initBitmap != null) {
            mImageTexture = new ImageTexture(outVideoWidth, outVideoHeight);
            mImageTexture.loadFromBitmap(initBitmap);
        }
    }

    @Override
    public void onDraw(int cameraTexture, int targetFrameBuffer, FloatBuffer shapeVerticesBuffer, FloatBuffer textureVerticesBuffer) {
        GLES20.glViewport(0, 0, outVideoWidth, outVideoHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        Rect rect = mAnimationData.rect;
        if (rect.left == rect.right || rect.top == rect.bottom) {
            return;
        }

        Bitmap bitmap = getViewCacheBitmap(mAreaView);
        if (bitmap != null) {
            if (mImageTexture == null) {
                mImageTexture = new ImageTexture(outVideoWidth, outVideoHeight);
                mImageTexture.loadFromBitmap(bitmap);
            } else {
                mImageTexture.updateTextureBitmap(bitmap);
            }
        }

        if (mImageTexture != null) {
            drawImage(mImageTexture.convertToRectF(rect), mImageTexture.getImageTextureId(),
                    cameraTexture, targetFrameBuffer,
                    shapeVerticesBuffer, textureVerticesBuffer);
        }

    }

    protected void drawImage(RectF rectF, int imageTextureId,
                             int cameraTexture, int targetFrameBuffer,
                             FloatBuffer shapeVerticesBuffer, FloatBuffer textureVerticesBuffer) {

        GLES20.glEnableVertexAttribArray(glCamPositionLoc);
        GLES20.glEnableVertexAttribArray(glCamTextureCoordLoc);
        shapeVerticesBuffer.position(0);
        GLES20.glVertexAttribPointer(glCamPositionLoc, 2, GLES20.GL_FLOAT, false,2 * 4, shapeVerticesBuffer);
        textureVerticesBuffer.position(0);
        GLES20.glVertexAttribPointer(glCamTextureCoordLoc, 2, GLES20.GL_FLOAT, false,2 * 4, textureVerticesBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFrameBuffer);
        GLES20.glUseProgram(glProgram);
        GLES20.glUniform4f(glImageRectLoc, rectF.left, rectF.top, rectF.right, rectF.bottom);
        // 用来更新旋转角度的
        // GLES20.glUniform1f(glImageAngelLoc, (float) (30.0f * Math.PI / 180));
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTexture);
        GLES20.glUniform1i(glCamTextureLoc, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId);
        GLES20.glUniform1i(glImageTextureLoc, 1);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawIndexesBuffer.limit(), GLES20.GL_UNSIGNED_SHORT, drawIndexesBuffer);
        GLES20.glDisableVertexAttribArray(glCamPositionLoc);
        GLES20.glDisableVertexAttribArray(glCamTextureCoordLoc);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glUseProgram(0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteProgram(glProgram);
        destroyImageTexture();
    }

    protected void destroyImageTexture() {
        if (mImageTexture != null) {
            mImageTexture.destroy();
        }
    }

    public static class ImageAnimationData {
        public int resId = 0;
        public Rect rect;
    }

    public static Bitmap getViewCacheBitmap(View view) {
        Bitmap bitmap = view.getDrawingCache();
        if (bitmap == null) {
            Log.d(TAG, "getViewCacheBitmap is null");
            return null;
        }
        Bitmap emptyBitmap = Bitmap.createBitmap(bitmap);
        view.destroyDrawingCache();
        return emptyBitmap;
    }
}
