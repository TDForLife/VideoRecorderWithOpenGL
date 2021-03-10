package com.icechn.videorecorder.client;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;

import com.icechn.videorecorder.core.CameraHelper;
import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.core.video.IVideoCore;
import com.icechn.videorecorder.core.video.VideoCore;
import com.icechn.videorecorder.encoder.MediaMuxerWrapper;
import com.icechn.videorecorder.filter.hardvideofilter.BaseHardVideoFilter;
import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.RecordConfig;
import com.icechn.videorecorder.model.Size;

import java.io.IOException;
import java.util.List;

/**
 * Created by lake on 16-5-24.
 */
public class VideoClient {

    private static final String TAG = "VideoClient";

    private final Object mPrepareSyncObj = new Object();
    private final MediaMakerConfig mMediaMakerConfig;
    private IVideoCore mVideoCore;
    private boolean isRecording;
    private boolean isPreviewing;

    // Camera
    private Camera mCamera;
    private SurfaceTexture mCameraTexture;
    private final int mCameraNum;
    private int mCurrentCameraIndex;
    private boolean mIsFrontCamera;

    public VideoClient(Context context, MediaMakerConfig parameters) {
        mMediaMakerConfig = parameters;
        mCameraNum = Camera.getNumberOfCameras();
        mCurrentCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK;
        mIsFrontCamera = false;
        isRecording = false;
        isPreviewing = false;
    }

    public boolean prepare(RecordConfig config) {
        synchronized (mPrepareSyncObj) {
            if ((mCameraNum - 1) >= config.getDefaultCamera()) {
                mCurrentCameraIndex = config.getDefaultCamera();
                mIsFrontCamera = mCurrentCameraIndex == CameraInfo.CAMERA_FACING_FRONT;
                Log.d(TAG, "Prepare camera which is front ? " + mIsFrontCamera);
            }
            if (null == (mCamera = createCamera(mCurrentCameraIndex))) {
                Log.e(TAG, "Prepare can not open camera");
                return false;
            }
            Camera.Parameters parameters = mCamera.getParameters();
            CameraHelper.selectCameraPreviewWH(parameters, mMediaMakerConfig, config.getTargetVideoSize());
            CameraHelper.selectCameraPictureWH(parameters, mMediaMakerConfig, config.getTargetVideoSize());
            CameraHelper.selectCameraFpsRange(parameters, mMediaMakerConfig);
            mMediaMakerConfig.videoFPS = Math.min(config.getVideoFPS(), mMediaMakerConfig.previewMaxFps / 1000);
            resolveResolution(mMediaMakerConfig, config.getTargetVideoSize());
            if (!CameraHelper.selectCameraColorFormat(parameters, mMediaMakerConfig)) {
                Log.e(TAG, "CameraHelper.selectCameraColorFormat,Failed");
                mMediaMakerConfig.dump();
                return false;
            }
            if (!CameraHelper.configCamera(mCamera, mMediaMakerConfig)) {
                Log.e(TAG, "CameraHelper.configCamera,Failed");
                mMediaMakerConfig.dump();
                return false;
            }
            mVideoCore = new VideoCore(mMediaMakerConfig);
            if (!mVideoCore.prepare(config)) {
                return false;
            }
            mVideoCore.setCurrentCamera(mCurrentCameraIndex);
            // prepareVideo();
            return true;
        }
    }

    private Camera createCamera(int cameraId) {
        try {
            mCamera = Camera.open(cameraId);
            mCamera.setDisplayOrientation(0);
        } catch (SecurityException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return mCamera;
    }

    private boolean startVideo() {
        mCameraTexture = new SurfaceTexture(IVideoCore.OVERWATCH_TEXTURE_ID);
        mCameraTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                synchronized (mPrepareSyncObj) {
                    if (mVideoCore != null) {
                        ((VideoCore) mVideoCore).onFrameAvailable();
                    }
                }
            }
        });
        try {
            mCamera.setPreviewTexture(mCameraTexture);
        } catch (IOException e) {
            e.printStackTrace();
            mCamera.release();
            return false;
        }
        mCamera.startPreview();
        return true;
    }

    public boolean startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        synchronized (mPrepareSyncObj) {
            if (!isWorking() && !isPreviewing) {
                if (!startVideo()) {
                    mMediaMakerConfig.dump();
                    Log.e("", "VideoClient,start(),failed");
                    return false;
                }
                mVideoCore.updateCamTexture(mCameraTexture);
            }
            mVideoCore.startPreview(surfaceTexture, visualWidth, visualHeight);
            isPreviewing = true;
            return true;
        }
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        mVideoCore.updatePreview(visualWidth, visualHeight);
    }

    public boolean stopPreview(boolean releaseTexture) {
        synchronized (mPrepareSyncObj) {
            if (isPreviewing) {
                mVideoCore.stopPreview(releaseTexture);
                if (!isWorking()) {
                    mCamera.stopPreview();
                    mVideoCore.updateCamTexture(null);
                    mCameraTexture.release();
                }
            }
            isPreviewing = false;
            return true;
        }
    }

    public boolean startRecording(MediaMuxerWrapper muxer) {
        synchronized (mPrepareSyncObj) {
            if (!isWorking() && !isPreviewing) {
                if (!startVideo()) {
                    mMediaMakerConfig.dump();
                    Log.e("", "VideoClient,start(),failed");
                    return false;
                }
                mVideoCore.updateCamTexture(mCameraTexture);
            }
            mVideoCore.startRecording(muxer);
            if (mMediaMakerConfig.saveVideoEnable) {
                isRecording = true;
            }
            return true;
        }
    }

    public boolean stopRecording() {
        synchronized (mPrepareSyncObj) {
            if (isWorking()) {
                mVideoCore.stopRecording();
                if (!isPreviewing) {
                    mCamera.stopPreview();
                    mVideoCore.updateCamTexture(null);
                    mCameraTexture.release();
                }
            }
            isRecording = false;
            return true;
        }
    }

    private boolean isWorking() {
        return isRecording;
    }

    public boolean destroy() {
        synchronized (mPrepareSyncObj) {
            mCamera.release();
            mVideoCore.destroy();
            mVideoCore = null;
            mCamera = null;
            return true;
        }
    }

    public boolean swapCamera() {
        synchronized (mPrepareSyncObj) {
            Log.d("", "StreamingClient,swapCamera()");
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            if (null == (mCamera = createCamera(mCurrentCameraIndex = (++mCurrentCameraIndex) % mCameraNum))) {
                Log.e("", "can not swap camera");
                return false;
            }
            mIsFrontCamera = mCurrentCameraIndex == CameraInfo.CAMERA_FACING_FRONT;
            mVideoCore.setCurrentCamera(mCurrentCameraIndex);
            CameraHelper.selectCameraFpsRange(mCamera.getParameters(), mMediaMakerConfig);
            if (!CameraHelper.configCamera(mCamera, mMediaMakerConfig)) {
                mCamera.release();
                return false;
            }
            // prepareVideo();
            mCameraTexture.release();
            mVideoCore.updateCamTexture(null);
            startVideo();
            mVideoCore.updateCamTexture(mCameraTexture);
            return true;
        }
    }

    public boolean isFrontCamera() {
        return mIsFrontCamera;
    }

    public boolean toggleFlashLight() {
        synchronized (mPrepareSyncObj) {
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                List<String> flashModes = parameters.getSupportedFlashModes();
                String flashMode = parameters.getFlashMode();
                if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(parameters);
                        return true;
                    }
                } else if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(parameters);
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return false;
        }
    }

    public boolean toggleFlashLight(boolean on) {
        synchronized (mPrepareSyncObj) {
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                List<String> flashModes = parameters.getSupportedFlashModes();
                String flashMode = parameters.getFlashMode();
                if (on && !Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(parameters);
                        return true;
                    }
                } else if (!on && !Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(parameters);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w("VideoClient", "toggleFlashLight,failed" + e.getMessage());
                return false;
            }
            return false;
        }
    }

    public boolean setZoomByPercent(float targetPercent) {
        synchronized (mPrepareSyncObj) {
            targetPercent = Math.min(Math.max(0f, targetPercent), 1f);
            Camera.Parameters p = mCamera.getParameters();
            p.setZoom((int) (p.getMaxZoom() * targetPercent));
            mCamera.setParameters(p);
            return true;
        }
    }

    public void setHardVideoFilter(BaseHardVideoFilter baseHardVideoFilter) {
        ((VideoCore) mVideoCore).setVideoFilter(baseHardVideoFilter);
    }

    public void setVideoChangeListener(IVideoChange listener) {
        synchronized (mPrepareSyncObj) {
            if (mVideoCore != null) {
                mVideoCore.setVideoChangeListener(listener);
            }
        }
    }

    private void resolveResolution(MediaMakerConfig config, Size targetVideoSize) {
        float pw, ph, vw, vh;
        if (config.isPortrait) {
            config.videoHeight = targetVideoSize.getWidth();
            config.videoWidth = targetVideoSize.getHeight();
            pw = config.previewVideoHeight;
            ph = config.previewVideoWidth;
        } else {
            config.videoWidth = targetVideoSize.getWidth();
            config.videoHeight = targetVideoSize.getHeight();
            pw = config.previewVideoWidth;
            ph = config.previewVideoHeight;
        }
        vw = config.videoWidth;
        vh = config.videoHeight;
        float pr = ph / pw, vr = vh / vw;
        if (pr == vr) {
            config.cropRatio = 0.0f;
        } else if (pr > vr) {
            config.cropRatio = (1.0f - vr / pr) / 2.0f;
        } else {
            config.cropRatio = -(1.0f - pr / vr) / 2.0f;
        }
        Log.d(TAG, "resolveResolution preview size - " + pw + " x " + ph + ", video size - " + vw + " x " + vh);
        Log.d(TAG, "resolveResolution preview aspect ratio - " + pr + " and video aspect ratio - " + vr + " then cropRatio - " + config.cropRatio);
    }


}
