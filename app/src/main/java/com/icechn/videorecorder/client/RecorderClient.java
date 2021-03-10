package com.icechn.videorecorder.client;


import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;

import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.encoder.MediaMuxerWrapper;
import com.icechn.videorecorder.filter.hardvideofilter.BaseHardVideoFilter;
import com.icechn.videorecorder.filter.softaudiofilter.BaseSoftAudioFilter;
import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.RecordConfig;
import com.icechn.videorecorder.model.Size;

import java.io.IOException;

public class RecorderClient {

    private static final String TAG = "RecorderClient";

    private final Object mSyncObj;

    private VideoClient mVideoClient;
    private AudioClient mAudioClient;
    private MediaMakerConfig mMediaMakerConfig;

    public RecorderClient() {
        mSyncObj = new Object();
        mMediaMakerConfig = new MediaMakerConfig();
    }

    /**
     * prepare to stream
     *
     * @param config config
     * @return true if prepare success
     */
    public boolean prepare(Context context, RecordConfig config) {
        synchronized (mSyncObj) {
            try {
                checkCameraDirection(config);
            } catch (RuntimeException e) {
                e.printStackTrace();
                return false;
            }
            mMediaMakerConfig.printDetailMsg = config.isPrintDetailMsg();
            mMediaMakerConfig.isSquare = config.isSquare();
            mMediaMakerConfig.saveVideoEnable = config.isSaveVideoEnable();
            mMediaMakerConfig.saveVideoPath = config.getSaveVideoPath();

            mVideoClient = new VideoClient(context, mMediaMakerConfig);
            mAudioClient = new AudioClient(mMediaMakerConfig);

            if (!mVideoClient.prepare(config)) {
                Log.e(TAG, "RecorderClient prepare VideoClient failed - " + mMediaMakerConfig.toString());
                return false;
            }

            if (!mAudioClient.prepare(config)) {
                Log.e(TAG, "RecorderClient prepare AudioClient failed - " + mMediaMakerConfig.toString());
                return false;
            }

            mMediaMakerConfig.done = true;
            Log.d(TAG, "RecorderClient prepare has DONE - " + mMediaMakerConfig.toString());
            return true;
        }
    }

    /**
     * clean up
     */
    public void destroy() {
        synchronized (mSyncObj) {
            mVideoClient.destroy();
            mAudioClient.destroy();
            mVideoClient = null;
            mAudioClient = null;
        }
    }

    /**
     * start recording
     */
    public void startRecording() {
        synchronized (mSyncObj) {
            prepareMuxer();
            mVideoClient.startRecording(mMuxer);
            mAudioClient.startRecording(mMuxer);
            Log.d("", "RecorderClient,startRecording()");
        }
    }

    /**
     * stop recording
     */
    public void stopRecording() {
        synchronized (mSyncObj) {
            mVideoClient.stopRecording();
            mAudioClient.stopRecording();
            Log.d("", "RecorderClient,stopRecording()");
        }
    }

    /**
     * call it AFTER {@link #prepare}
     *
     * @param surfaceTexture to rendering preview
     */
    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        mVideoClient.startPreview(surfaceTexture, visualWidth, visualHeight);
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        mVideoClient.updatePreview(visualWidth, visualHeight);
    }

    /**
     * @param releaseTexture true if you won`t reuse this surfaceTexture later
     */
    public void stopPreview(boolean releaseTexture) {
        if (mVideoClient != null) {
            mVideoClient.stopPreview(releaseTexture);
        }
    }

    public void updateVideoSavePath(String path) {
        mMediaMakerConfig.saveVideoPath = path;
    }

    public String getVideoSavePath() {
        return mMediaMakerConfig.saveVideoEnable ? mMediaMakerConfig.saveVideoPath : null;
    }

    /**
     * change camera on running.<br/>
     */
    public boolean swapCamera() {
        synchronized (mSyncObj) {
            return mVideoClient.swapCamera();
        }
    }

    public boolean isFrontCamera() {
        return mVideoClient.isFrontCamera();
    }

    /**
     * get the real video size,call after prepare()
     *
     * @return
     */
    public Size getVideoSize() {
        return new Size(mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
    }

    /**
     * only for hard filter mode.<br/>
     * set videofilter.<br/>
     * can be called Repeatedly.<br/>
     *
     * @param baseHardVideoFilter videofilter to apply
     */
    public void setHardVideoFilter(BaseHardVideoFilter baseHardVideoFilter) {
        mVideoClient.setHardVideoFilter(baseHardVideoFilter);
    }

    /**
     * set AudioFilter
     * can be called Repeatedly
     *
     * @param baseSoftAudioFilter Audiofilter to apply
     */
    public void setSoftAudioFilter(BaseSoftAudioFilter baseSoftAudioFilter) {
        mAudioClient.setSoftAudioFilter(baseSoftAudioFilter);
    }

    /**
     * listener for video size change
     *
     * @param videoChangeListener
     */
    public void setVideoChangeListener(IVideoChange videoChangeListener) {
        mVideoClient.setVideoChangeListener(videoChangeListener);
    }

    /**
     * toggle flash light
     *
     * @return true if operation success
     */
    public boolean toggleFlashLight() {
        return mVideoClient.toggleFlashLight();
    }

    public boolean toggleFlashLight(boolean on) {
        return mVideoClient.toggleFlashLight(on);
    }

    /**
     * 配置前置、后置摄像头的方向的相关信息
     **/
    private void checkCameraDirection(RecordConfig config) {
        int frontFlag = config.getFrontCameraDirectionMode();
        int backFlag = config.getBackCameraDirectionMode();
        int fbit = 0;
        int bbit = 0;
        // check or set default value
        if ((frontFlag >> 4) == 0) {
            frontFlag |= MediaMakerConfig.FLAG_DIRECTION_ROATATION_0;
        }
        if ((backFlag >> 4) == 0) {
            backFlag |= MediaMakerConfig.FLAG_DIRECTION_ROATATION_0;
        }
        // make sure only one direction
        for (int i = 4; i <= 8; ++i) {
            if (((frontFlag >> i) & 0x1) == 1) {
                fbit++;
            }
            if (((backFlag >> i) & 0x1) == 1) {
                bbit++;
            }
        }
        if (fbit != 1 || bbit != 1) {
            throw new RuntimeException("Invalid direction rotation flag : frontFlagNum = " + fbit + ", backFlagNum = " + bbit);
        }
        if (((frontFlag & MediaMakerConfig.FLAG_DIRECTION_ROATATION_0) != 0) || ((frontFlag & MediaMakerConfig.FLAG_DIRECTION_ROATATION_180) != 0)) {
            fbit = 0;
        } else {
            fbit = 1;
        }
        if (((backFlag & MediaMakerConfig.FLAG_DIRECTION_ROATATION_0) != 0) || ((backFlag & MediaMakerConfig.FLAG_DIRECTION_ROATATION_180) != 0)) {
            bbit = 0;
        } else {
            bbit = 1;
        }
        if (bbit != fbit) {
            if (bbit == 0) {
                throw new RuntimeException("invalid direction rotation flag:back camera is landscape but front camera is portrait");
            } else {
                throw new RuntimeException("invalid direction rotation flag:back camera is portrait but front camera is landscape");
            }
        }
        mMediaMakerConfig.isPortrait = fbit == 1;
        mMediaMakerConfig.backCameraDirectionMode = backFlag;
        mMediaMakerConfig.frontCameraDirectionMode = frontFlag;
    }

    private MediaMuxerWrapper mMuxer = null;

    private void prepareMuxer() {
        if (!mMediaMakerConfig.saveVideoEnable) {
            return;
        }
        try {
            mMuxer = new MediaMuxerWrapper(mMediaMakerConfig.saveVideoPath);
            mMuxer.setTrackCount(2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
