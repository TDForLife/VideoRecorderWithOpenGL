package com.icechn.videorecorder.filter.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.View;

import com.icechn.videorecorder.filter.hardvideofilter.BaseHardVideoFilter;
import com.icechn.videorecorder.tools.GLESTools;

import java.nio.FloatBuffer;
import java.util.ArrayList;

public class AnimMultiImageFilter extends BaseHardVideoFilter {

    private static final String TAG = "anim";

    protected int glProgram;
    protected int glCamTextureLoc;
    protected int glCamPositionLoc;
    protected int glCamTextureCoordLoc;
    protected int glImageTextureLoc;
    protected int glImageRectLoc;
    protected int glImageAngelLoc;

    protected Context mContext;
    private final ArrayList<View> mAreaViewList = new ArrayList<>();
    private final ArrayList<ImageAnimationData> mImageAnimInfoList = new ArrayList<>();
    private final ArrayList<ImageTexture> mImageTextureList = new ArrayList<>();

    public AnimMultiImageFilter(Context context, ArrayList<View> areaViewList, ArrayList<ImageAnimationData> imageInfoList) {
        super();
        mContext = context;
        if (imageInfoList == null || imageInfoList.size() == 0) {
            throw new RuntimeException("DrawMultiImageFilter's imageInfoList must not be empty");
        }
        for (View areaView : areaViewList) {
            areaView.setDrawingCacheEnabled(true);
        }
        this.mAreaViewList.addAll(areaViewList);
        this.mImageAnimInfoList.addAll(imageInfoList);
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
        mImageTextureList.clear();
        for (int i = 0; i < mImageAnimInfoList.size(); i++) {
            Bitmap initBitmap = getViewCacheBitmap(mAreaViewList.get(i));
            if (initBitmap != null) {
                ImageTexture imageTexture = new ImageTexture(outVideoWidth, outVideoHeight);
                imageTexture.loadFromBitmap(initBitmap);
                mImageTextureList.add(imageTexture);
            }
        }
    }

    @Override
    public void onDraw(int cameraTexture, int targetFrameBuffer, FloatBuffer shapeVerticesBuffer, FloatBuffer textureVerticesBuffer) {
        GLES20.glViewport(0, 0, outVideoWidth, outVideoHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int backgroundTextureId;
        int frameBuffer;
        ImageTexture preImageTexture = null;

        int size = mImageAnimInfoList.size();
        for (int i = 0; i < size; i++) {

            View areaView = mAreaViewList.get(i);
            ImageAnimationData imageDrawInfo = mImageAnimInfoList.get(i);
            ImageTexture imageTexture = mImageTextureList.get(i);

            Rect rect = imageDrawInfo.rect;
            if (rect.left == rect.right || rect.top == rect.bottom) {
                continue;
            }

            Bitmap bitmap = getViewCacheBitmap(areaView);
            if (bitmap != null) {
                if (imageTexture == null) {
                    imageTexture = new ImageTexture(outVideoWidth, outVideoHeight);
                    imageTexture.loadFromBitmap(bitmap);
                } else {
                    imageTexture.updateTextureBitmap(bitmap);
                }
            }

            if (preImageTexture == null) {
                backgroundTextureId = cameraTexture;
            } else {
                backgroundTextureId = preImageTexture.getFrameBufferTextureId();
            }
            if (i == size - 1) {
                frameBuffer = targetFrameBuffer;
            } else {
                frameBuffer = imageTexture.getFrameBuffer();
            }

            drawImage(imageTexture.convertToRectF(rect), imageTexture.getImageTextureId(),
                    backgroundTextureId, frameBuffer,
                    shapeVerticesBuffer, textureVerticesBuffer);

            preImageTexture = mImageTextureList.get(i);
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
        for (ImageTexture imageTexture : mImageTextureList) {
            imageTexture.destroy();
        }
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