package com.icechn.videorecorder.client;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.icechn.videorecorder.core.audio.AudioCore;
import com.icechn.videorecorder.encoder.MediaMuxerWrapper;
import com.icechn.videorecorder.filter.softaudiofilter.BaseSoftAudioFilter;
import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.RecordConfig;

/**
 * Created by lake on 16-5-24.
 */
public class AudioClient {
    MediaMakerConfig mediaMakerConfig;
    private final Object syncOp = new Object();
    private AudioRecordThread audioRecordThread;
    private AudioRecord audioRecord;
    private byte[] audioBuffer;
    private AudioCore softAudioCore;

    public AudioClient(MediaMakerConfig parameters) {
        mediaMakerConfig = parameters;
    }

    public boolean prepare(RecordConfig recordConfig) {
        synchronized (syncOp) {
            mediaMakerConfig.audioBufferQueueNum = 5;
            softAudioCore = new AudioCore(mediaMakerConfig);
            if (!softAudioCore.prepare(recordConfig)) {
                Log.e("","AudioClient,prepare");
                return false;
            }
            mediaMakerConfig.audioRecorderFormat = AudioFormat.ENCODING_PCM_16BIT;
            mediaMakerConfig.audioRecorderChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            mediaMakerConfig.audioRecorderSliceSize = mediaMakerConfig.mediaCodecAACSampleRate / 10;
            mediaMakerConfig.audioRecorderBufferSize = mediaMakerConfig.audioRecorderSliceSize * 2;
            mediaMakerConfig.audioRecorderSource = MediaRecorder.AudioSource.DEFAULT;
            mediaMakerConfig.audioRecorderSampleRate = mediaMakerConfig.mediaCodecAACSampleRate;
            prepareAudio();
            return true;
        }
    }

    public boolean startRecording(MediaMuxerWrapper muxer) {
        synchronized (syncOp) {
            softAudioCore.startRecording(muxer);
            audioRecord.startRecording();
            audioRecordThread = new AudioRecordThread();
            audioRecordThread.start();
            Log.d("","AudioClient,start()");
            return true;
        }
    }

    public boolean stopRecording() {
        synchronized (syncOp) {
            if (audioRecordThread != null) {
                audioRecordThread.quit();
                try {
                    audioRecordThread.join();
                } catch (InterruptedException ignored) {
                }
                audioRecordThread = null;
            }
            softAudioCore.stop();
            audioRecord.stop();
            return true;
        }
    }

    public boolean destroy() {
        synchronized (syncOp) {
            audioRecord.release();
            return true;
        }
    }
    public void setSoftAudioFilter(BaseSoftAudioFilter baseSoftAudioFilter) {
        softAudioCore.setAudioFilter(baseSoftAudioFilter);
    }
    public BaseSoftAudioFilter acquireSoftAudioFilter() {
        return softAudioCore.acquireAudioFilter();
    }

    public void releaseSoftAudioFilter() {
        softAudioCore.releaseAudioFilter();
    }

    private boolean prepareAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(mediaMakerConfig.audioRecorderSampleRate,
                mediaMakerConfig.audioRecorderChannelConfig,
                mediaMakerConfig.audioRecorderFormat);
        audioRecord = new AudioRecord(mediaMakerConfig.audioRecorderSource,
                mediaMakerConfig.audioRecorderSampleRate,
                mediaMakerConfig.audioRecorderChannelConfig,
                mediaMakerConfig.audioRecorderFormat,
                minBufferSize * 5);
        audioBuffer = new byte[mediaMakerConfig.audioRecorderBufferSize];
        if (AudioRecord.STATE_INITIALIZED != audioRecord.getState()) {
            Log.e("","audioRecord.getState()!=AudioRecord.STATE_INITIALIZED!");
            return false;
        }
        if (AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(mediaMakerConfig.audioRecorderSliceSize)) {
            Log.e("","AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(" + mediaMakerConfig.audioRecorderSliceSize + ")");
            return false;
        }
        return true;
    }

    class AudioRecordThread extends Thread {
        private boolean isRunning = true;

        AudioRecordThread() {
            isRunning = true;
        }

        public void quit() {
            isRunning = false;
        }

        @Override
        public void run() {
            Log.d("","AudioRecordThread,tid=" + Thread.currentThread().getId());
            while (isRunning) {
                int size = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (isRunning && softAudioCore != null && size > 0) {
                    softAudioCore.queueAudio(audioBuffer);
                }
            }
        }
    }
}
