package com.icechn.videorecorder.filter.hardvideofilter;

import com.icechn.videorecorder.core.GLHelper;
import com.icechn.videorecorder.model.Size;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Created by lake on 16-5-31.
 */
public class BaseHardVideoFilter {

    protected int previewWidth;
    protected int previewHeight;
    protected Size previewSize;

    protected int outVideoWidth;
    protected int outVideoHeight;
    protected int directionFlag = -1;
    protected ShortBuffer drawIndexesBuffer;

    public void onInit(int videoWidth, int videoHeight) {
        outVideoWidth = videoWidth;
        outVideoHeight = videoHeight;
        drawIndexesBuffer = GLHelper.getDrawIndexesBuffer();
    }

    public void onDraw(final int cameraTexture, final int targetFrameBuffer,
                       final FloatBuffer shapeVerticesBuffer, final FloatBuffer textureVerticesBuffer) {}

    public void onDestroy() {}

    public void onDirectionUpdate(int _directionFlag) {
        this.directionFlag = _directionFlag;
    }

    public void updatePreviewSize(int width, int height) {
        previewWidth = width;
        previewHeight = height;
        previewSize = new Size(width, height);
    }

    protected boolean isSquare;

    public void updateSquareFlag(boolean isSquare) {
        this.isSquare = isSquare;
    }

    protected float mCropRatio = 0;

    public void updateCropRatio(float cropRatio) {
        mCropRatio = cropRatio;
    }

}
