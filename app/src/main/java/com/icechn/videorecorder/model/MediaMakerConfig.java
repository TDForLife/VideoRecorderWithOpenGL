package com.icechn.videorecorder.model;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class MediaMakerConfig {

    public static final int RENDERING_MODE_OPENGLES = 2;
    /**
     * same with jni
     */
    public static final int FLAG_DIRECTION_FLIP_HORIZONTAL = 0x01;
    public static final int FLAG_DIRECTION_FLIP_VERTICAL = 0x02;
    public static final int FLAG_DIRECTION_ROATATION_0 = 0x10;
    public static final int FLAG_DIRECTION_ROATATION_90 = 0x20;
    public static final int FLAG_DIRECTION_ROATATION_180 = 0x40;
    public static final int FLAG_DIRECTION_ROATATION_270 = 0x80;

    public boolean done;
    public boolean printDetailMsg;
    public int renderingMode;
    public int frontCameraDirectionMode;
    public int backCameraDirectionMode;
    public boolean isPortrait; // 是否竖屏
    public int previewVideoWidth;
    public int previewVideoHeight;
    public int videoWidth;
    public int videoHeight;
    public int videoFPS;
    public int videoGOP;
    public float cropRatio;
    public int previewColorFormat;
    public int previewBufferSize;
    public int mediaCodecAVCColorFormat;
    public int mediaCodecAVCBitRate;
    public int videoBufferQueueNum;
    public int audioBufferQueueNum;
    public int audioRecorderFormat;
    public int audioRecorderSampleRate;
    public int audioRecorderChannelConfig;
    public int audioRecorderSliceSize;
    public int audioRecorderSource;
    public int audioRecorderBufferSize;
    public int previewMaxFps;
    public int previewMinFps;
    public int mediaCodecAVCFrameRate;
    public int mediaCodecAVCIFrameInterval;
    public int mediaCodecAVCProfile;
    public int mediaCodecAVCLevel;

    public int mediaCodecAACProfile;
    public int mediaCodecAACSampleRate;
    public int mediaCodecAACChannelCount;
    public int mediaCodecAACBitRate;
    public int mediaCodecAACMaxInputSize;

    //face detect
    public boolean isFaceDetectEnable = false;
    public boolean isSquare = false;

    public boolean saveVideoEnable = false;
    public String saveVideoPath;

    public MediaMakerConfig() {
        done = false;
        printDetailMsg = false;
        videoWidth = -1;
        videoHeight = -1;
        videoFPS=-1;
        videoGOP=1;
        previewColorFormat = -1;
        mediaCodecAVCColorFormat = -1;
        mediaCodecAVCBitRate = -1;
        videoBufferQueueNum = -1;
        audioBufferQueueNum = -1;
        mediaCodecAVCFrameRate = -1;
        mediaCodecAVCIFrameInterval = -1;
        mediaCodecAVCProfile = -1;
        mediaCodecAVCLevel = -1;
        mediaCodecAACProfile = -1;
        mediaCodecAACSampleRate = -1;
        mediaCodecAACChannelCount = -1;
        mediaCodecAACBitRate = -1;
        mediaCodecAACMaxInputSize = -1;
    }

    public void dump() {
        Log.e("",this.toString());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ResParameter:");
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                sb.append(field.getName());
                sb.append('=');
                sb.append(field.get(this));
                sb.append(';');
            } catch (IllegalAccessException e) {
            }
        }
        return sb.toString();
    }
}
