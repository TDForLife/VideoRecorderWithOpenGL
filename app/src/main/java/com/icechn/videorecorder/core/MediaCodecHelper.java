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
    public static MediaCodec createSoftVideoMediaCodec(MediaMakerConfig config, MediaFormat videoFormat) {
        videoFormat.setString(MediaFormat.KEY_MIME, "video/avc");
        videoFormat.setInteger(MediaFormat.KEY_WIDTH, config.videoWidth);
        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, config.videoHeight);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, config.mediaCodecAVCBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, config.mediaCodecAVCFrameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.mediaCodecAVCIFrameInterval);
        videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        MediaCodec result = null;
        try {
            result = MediaCodec.createEncoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
            //select color
            int[] colorful = result.getCodecInfo().getCapabilitiesForType(videoFormat.getString(MediaFormat.KEY_MIME)).colorFormats;
            int dstVideoColorFormat = -1;
            //select mediacodec colorformat
            if (isArrayContain(colorful, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                dstVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                config.mediaCodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            }
            if (dstVideoColorFormat == -1 && isArrayContain(colorful, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                dstVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                config.mediaCodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
            }
            if (dstVideoColorFormat == -1) {
                Log.e("", "!!!!!!!!!!!UnSupport,mediaCodecColorFormat");
                return null;
            }
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, dstVideoColorFormat);
            //selectprofile
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                MediaCodecInfo.CodecProfileLevel[] profileLevels = result.getCodecInfo().getCapabilitiesForType(videoFormat.getString(MediaFormat.KEY_MIME)).profileLevels;
//                if (isProfileContain(profileLevels, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)) {
//                    config.mediacodecAVCProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
//                    config.mediacodecAVClevel = MediaCodecInfo.CodecProfileLevel.AVCLevel31;
//                } else {
//                    config.mediacodecAVCProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
//                    config.mediacodecAVClevel = MediaCodecInfo.CodecProfileLevel.AVCLevel31;
//                }
//                videoFormat.setInteger(MediaFormat.KEY_PROFILE, config.mediacodecAVCProfile);
//                //level must be set even below M
//                videoFormat.setInteger(MediaFormat.KEY_LEVEL, config.mediacodecAVClevel);
//            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }

    public static MediaCodec createAudioMediaCodec(MediaMakerConfig config, MediaFormat audioFormat) {
        //Audio
        MediaCodec result;
        audioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, config.mediaCodecAACProfile);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, config.mediaCodecAACSampleRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.mediaCodecAACChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, config.mediaCodecAACBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, config.mediaCodecAACMaxInputSize);
        Log.d("", "creatingAudioEncoder,format=" + audioFormat.toString());
        try {
            result = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }

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