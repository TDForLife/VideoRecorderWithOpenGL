package com.icechn.videorecorder.core.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;

import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.encoder.MediaMuxerWrapper;
import com.icechn.videorecorder.model.RecordConfig;

/**
 * Created by lake on 16-5-25.
 */
public interface IVideoCore {

    // 最好不要超过 16
    int OVERWATCH_CAMERA_TEXTURE_ID = 10;
    int OVERWATCH_VIEW_TEXTURE_ID = 11;

    boolean prepare(RecordConfig resConfig);

    void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight);

    void updatePreview(int visualWidth, int visualHeight);

    void stopPreview(boolean releaseTexture);

    boolean startRecording(MediaMuxerWrapper muxer);

    boolean stopRecording();

    boolean destroy();

    void setCurrentCamera(int cameraIndex);

    void updateCameraTexture(SurfaceTexture camTex);

    void setVideoChangeListener(IVideoChange listener);
}
