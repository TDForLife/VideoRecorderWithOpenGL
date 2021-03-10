package com.icechn.videorecorder.core;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;

import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.Size;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by lake on 16-3-16.
 */
public class CameraHelper {
    private static final String TAG = "camera";
    public static int targetFps = 30000;
    private static final int[] supportedSrcVideoFrameColorType = new int[]{ImageFormat.NV21, ImageFormat.YV12};

    public static boolean configCamera(Camera camera, MediaMakerConfig config) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
        }
        parameters.setPreviewSize(config.previewVideoWidth, config.previewVideoHeight);
        parameters.setPreviewFpsRange(config.previewMinFps, config.previewMaxFps);
        try {
            camera.setParameters(parameters);
        } catch (Exception e) {
            camera.release();
            return false;
        }
        return true;
    }

    public static void selectCameraFpsRange(Camera.Parameters parameters, MediaMakerConfig config) {
        List<int[]> fpsRanges = parameters.getSupportedPreviewFpsRange();
        Collections.sort(fpsRanges, new Comparator<int[]>() {
            @Override
            public int compare(int[] lhs, int[] rhs) {
                int r = Math.abs(lhs[0] - targetFps) + Math.abs(lhs[1] - targetFps);
                int l = Math.abs(rhs[0] - targetFps) + Math.abs(rhs[1] - targetFps);
                if (r > l) {
                    return 1;
                } else if (r < l) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
        config.previewMinFps = fpsRanges.get(0)[0];
        config.previewMaxFps = fpsRanges.get(0)[1];
    }

    public static void selectCameraPreviewWH(Camera.Parameters parameters, MediaMakerConfig config, Size targetSize) {
        List<Camera.Size> previewsSizes = parameters.getSupportedPreviewSizes();
        // TODO 待优化选择更为恰当的不占用太多存储空间的 Size
        Camera.Size size = getProperCameraSize(previewsSizes, targetSize.getWidth(), targetSize.getHeight(), 0.1f);
        config.previewVideoWidth = size.width;
        config.previewVideoHeight = size.height;
        // setPreviewSize 由外部统一设置
    }

    public static void selectCameraPictureWH(Camera.Parameters parameters, MediaMakerConfig config, Size targetSize) {
        List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
        Camera.Size size = getProperCameraSize(pictureSizes, targetSize.getWidth(), targetSize.getHeight(), 0.1f);
        parameters.setPictureSize(size.width, size.height);
    }

    public static boolean selectCameraColorFormat(Camera.Parameters parameters, MediaMakerConfig config) {
        List<Integer> srcColorTypes = new LinkedList<>();
        List<Integer> supportedPreviewFormatList = parameters.getSupportedPreviewFormats();
        for (int colorType : supportedSrcVideoFrameColorType) {
            if (supportedPreviewFormatList.contains(colorType)) {
                srcColorTypes.add(colorType);
            }
        }
        // select preview colorFormat
        if (srcColorTypes.contains(config.previewColorFormat = ImageFormat.NV21)) {
            config.previewColorFormat = ImageFormat.NV21;
        } else if ((srcColorTypes.contains(config.previewColorFormat = ImageFormat.YV12))) {
            config.previewColorFormat = ImageFormat.YV12;
        } else {
            Log.e(TAG, "selectCameraColorFormat unSupport");
            return false;
        }
        return true;
    }

    private static Camera.Size getProperCameraSize(List<Camera.Size> cameraSizeList, int width, int height, double diff) {

        if (cameraSizeList == null || cameraSizeList.isEmpty()) {
            return null;
        }

        if (width < height) {
            int temp = height;
            height = width;
            width = temp;
        }

        Collections.sort(cameraSizeList, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                if ((lhs.width * lhs.height) > (rhs.width * rhs.height)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        double ratio = (double) width / height;
        Camera.Size outputSize = null;
        for (Camera.Size currentSize : cameraSizeList) {
            double currentRatio = (double) currentSize.width / currentSize.height;
            double currentDiff = Math.abs(currentRatio - ratio);
            if (currentDiff > diff) {
                continue;
            }
            if (outputSize == null) {
                outputSize = currentSize;
            } else {
                if (outputSize.width * outputSize.height < currentSize.width * currentSize.height) {
                    outputSize = currentSize;
                }
            }
            diff = currentDiff;
        }

        if (outputSize == null) {
            diff += 0.1f;
            if (diff > 1.0f) {
                outputSize = cameraSizeList.get(0);
            } else {
                outputSize = getProperCameraSize(cameraSizeList, width, height, diff);
            }
        }
        return outputSize;
    }
}
