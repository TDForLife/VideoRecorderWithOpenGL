package com.icechn.videorecorder.filter.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.support.annotation.IntegerRes;

import com.icechn.videorecorder.tools.BitmapUtils;
import com.icechn.videorecorder.tools.GLESTools;

/**
 * Created by ICE on 2017/11/6.
 */

public class ImageTexture {

    private final int[] imageSize;
    private final int outWidth;
    private final int outHeight;
    private RectF convertRectF;

    private int imageTextureId;
    private int frameBufferTextureId;
    private int frameBuffer;


    public ImageTexture(int outWidth, int outHeight) {
        imageSize = new int[2];
        this.outWidth = outWidth;
        this.outHeight = outHeight;
        this.convertRectF = new RectF();
    }

    public ImageTexture load(Context context, String filePath, boolean isAssetsFile) {
        if (isAssetsFile) {
            return loadFromBitmap(BitmapUtils.loadBitmapFromAssets(context, filePath));
        } else {
            return loadFromBitmap(BitmapUtils.loadBitmapFromDisk(context, filePath));
        }
    }

    public ImageTexture load(Context context, @IntegerRes int resId) {
        return loadFromBitmap(BitmapUtils.loadBitmapFromRaw(context, resId));
    }

    public ImageTexture loadFromBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            imageTextureId = GLESTools.loadTexture(bitmap, GLESTools.NO_TEXTURE);
            imageSize[0] = bitmap.getWidth();
            imageSize[1] = bitmap.getHeight();
            int[] frameBufferArray = new int[1];
            int[] frameBufferTextureArray = new int[1];
            GLESTools.createFrameBuffer(frameBufferArray, frameBufferTextureArray, outWidth, outHeight);
            frameBuffer = frameBufferArray[0];
            frameBufferTextureId = frameBufferTextureArray[0];
            bitmap.recycle();
        }
        return this;
    }

    public int getImageTextureId() {
        return imageTextureId;
    }

    public int getFrameBufferTextureId() {
        return frameBufferTextureId;
    }

    public int getFrameBuffer() {
        return frameBuffer;
    }

    public int getImageWidth() {
        return imageSize[0];
    }

    public int getImageHeight() {
        return imageSize[1];
    }

    public float getImageRatio() {
        return 1.0f * imageSize[0] / imageSize[1];
    }

    public void destroy() {
        GLES20.glDeleteTextures(2, new int[]{imageTextureId, frameBufferTextureId}, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
    }


    /**
     * 按与显示大小比例转换 Image 的矩形尺寸
     * 为了防止不断创建 RectF 所以放到这里，让 ImageTexture 持有
     * @param iconRect
     * @return
     */
    public RectF convertToRectF(Rect iconRect) {
        convertRectF.top = iconRect.top / (float) outHeight;
        convertRectF.bottom = iconRect.bottom / (float) outHeight;
        convertRectF.left = iconRect.left / (float) outWidth;
        convertRectF.right = iconRect.right / (float) outWidth;
        return convertRectF;
    }
}
