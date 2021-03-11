package com.icechn.videorecorder.core;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.icechn.videorecorder.model.MediaMakerConfig;

import java.io.IOException;


/**
 * Created by lake on 16-3-16.
 */
public class MediaCodecHelper {

    private static final String TAG = "codec";

    /**
     * 创建视频软件编码器
     *
     * @param config
     * @param videoFormat
     * @return
     */
    public static MediaCodec createSoftVideoMediaCodec(MediaMakerConfig config, MediaFormat videoFormat) {

        videoFormat.setInteger(MediaFormat.KEY_WIDTH, config.videoWidth);
        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, config.videoHeight);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, config.mediaCodecAVCBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, config.mediaCodecAVCFrameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.mediaCodecAVCIFrameInterval);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        }

        try {
            MediaCodec videoCodec = MediaCodec.createEncoderByType("video/avc");
            // select color
            int[] colorful = videoCodec.getCodecInfo().getCapabilitiesForType(videoFormat.getString(MediaFormat.KEY_MIME)).colorFormats;
            int dstVideoColorFormat = -1;

            // select MediaCodec ColorFormat
            if (isArrayContain(colorful, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                dstVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                config.mediaCodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            }
            if (dstVideoColorFormat == -1 && isArrayContain(colorful, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                dstVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                config.mediaCodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
            }
            if (dstVideoColorFormat == -1) {
                Log.e(TAG, "createSoftVideoMediaCodec happen unSupport color format");
                return null;
            }
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, dstVideoColorFormat);
            return videoCodec;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 创建视频硬件编码器
     *
     * @param config
     * @param videoFormat
     * @return
     */
    public static MediaCodec createHardVideoMediaCodec(MediaMakerConfig config, MediaFormat videoFormat) {
        try {

            MediaCodec videoCodec = MediaCodec.createEncoderByType("video/avc");
            videoFormat.setInteger(MediaFormat.KEY_WIDTH, config.previewVideoHeight);
            videoFormat.setInteger(MediaFormat.KEY_HEIGHT, config.previewVideoWidth);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // TODO 码率应该和 Video Content Size 有关
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, config.mediaCodecAVCBitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, config.mediaCodecAVCFrameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.mediaCodecAVCIFrameInterval);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            }

            // config.mediaCodecAVCProfile \ config.mediaCodecAVCLevel ???
            return videoCodec;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 创建音频编码器
     *
     * @param config
     * @param audioFormat
     * @return
     */
    public static MediaCodec createAudioMediaCodec(MediaMakerConfig config, MediaFormat audioFormat) {
        MediaCodec result;
        audioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, config.mediaCodecAACProfile);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, config.mediaCodecAACSampleRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.mediaCodecAACChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, config.mediaCodecAACBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, config.mediaCodecAACMaxInputSize);
        Log.d(TAG, "creatingAudioEncoder audio format = " + audioFormat.toString());
        try {
            result = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }

    private static boolean isArrayContain(int[] src, int target) {
        for (int color : src) {
            if (color == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProfileContain(MediaCodecInfo.CodecProfileLevel[] src, int target) {
        for (MediaCodecInfo.CodecProfileLevel color : src) {
            if (color.profile == target) {
                return true;
            }
        }
        return false;
    }
}