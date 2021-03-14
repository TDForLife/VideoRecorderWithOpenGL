package com.icechn.videorecorder.core.video;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.icechn.videorecorder.tools.CallbackDelivery;
import com.icechn.videorecorder.core.GLHelper;
import com.icechn.videorecorder.core.MediaCodecHelper;
import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.core.listener.IVideoChange.VideoChangeRunable;
import com.icechn.videorecorder.encoder.MediaMuxerWrapper;
import com.icechn.videorecorder.filter.hardvideofilter.BaseHardVideoFilter;
import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.MediaCodecGLWrapper;
import com.icechn.videorecorder.model.MediaConfig;
import com.icechn.videorecorder.model.OffScreenGLWrapper;
import com.icechn.videorecorder.model.RecordConfig;
import com.icechn.videorecorder.model.ScreenGLWrapper;
import com.icechn.videorecorder.model.Size;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 每个窗口一个上下文，优点是可以保证状态机不互相影响。但多个窗口需要使用同一份纹理，如何避免重复的资源申请呢？
 * 答案是上下文之间的图形资源可以共享，先创建上下文 A，再以 A 为输入，创建上下文 B，则 B 可访问在 A 上下文下创建的纹理资源。
 * 纹理、shader、Buffer 等资源是可以共享的，但 Frame Buffer Object(FBO)、Vertex Array Object（VAO）等容器对象不可共享，
 * 但可将共享的纹理和 VBO 绑定到各自上下文的容器对象上。
 * https://cloud.tencent.com/developer/news/190234
 */
public class VideoCore implements IVideoCore {

    private static final String TAG = "VideoCore";

    private final Object mSyncObj = new Object();
    private final MediaMakerConfig mMediaMakerConfig;

    private final Object syncVideoChangeListener = new Object();
    private final Object syncPreview = new Object();
    private final Object syncIsLooping = new Object();
    private final Lock lockVideoFilter = new ReentrantLock(false);

    private BaseHardVideoFilter mHardVideoFilter;
    private MediaCodec mDSTVideoEncoder;
    private MediaFormat mDSTVideoFormat;

    private HandlerThread videoGLHandlerThread;
    private VideoGLHandler videoGLHandler;
    private IVideoChange mVideoChangeListener;

    private boolean isPreviewing;
    private boolean isStreaming;
    private int loopingInterval;

    public VideoCore(MediaMakerConfig parameters) {
        mMediaMakerConfig = parameters;
    }

    public void onFrameAvailable() {
        if (videoGLHandlerThread != null) {
            videoGLHandler.addFrameNum();
        }
    }

    @Override
    public boolean prepare(RecordConfig resConfig) {
        synchronized (mSyncObj) {
            mMediaMakerConfig.renderingMode = resConfig.getRenderingMode();
            mMediaMakerConfig.mediaCodecAVCBitRate = resConfig.getBitRate();
            mMediaMakerConfig.videoBufferQueueNum = resConfig.getVideoBufferQueueNum();
            mMediaMakerConfig.mediaCodecAVCIFrameInterval = resConfig.getVideoGOP();
            mMediaMakerConfig.mediaCodecAVCFrameRate = mMediaMakerConfig.videoFPS;
            loopingInterval = 1000 / mMediaMakerConfig.videoFPS;
            // TODO "video/avc" 应该由业务方来设置
            mDSTVideoFormat = MediaFormat.createVideoFormat("video/avc",
                    mMediaMakerConfig.previewVideoHeight, mMediaMakerConfig.previewVideoWidth);
            videoGLHandlerThread = new HandlerThread("GLThread");
            videoGLHandlerThread.start();
            videoGLHandler = new VideoGLHandler(videoGLHandlerThread.getLooper());
            videoGLHandler.sendEmptyMessage(VideoGLHandler.WHAT_INIT);
            return true;
        }
    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        synchronized (mSyncObj) {
            videoGLHandler.sendMessage(videoGLHandler.obtainMessage(VideoGLHandler.WHAT_START_PREVIEW,
                    visualWidth, visualHeight, surfaceTexture));
            synchronized (syncIsLooping) {
                if (!isPreviewing && !isStreaming) {
                    videoGLHandler.removeMessages(VideoGLHandler.WHAT_DRAW);
                    videoGLHandler.sendMessageDelayed(videoGLHandler.obtainMessage(VideoGLHandler.WHAT_DRAW,
                            SystemClock.uptimeMillis() + loopingInterval), loopingInterval);
                }
                isPreviewing = true;
            }
        }
    }

    @Override
    public void updatePreview(int visualWidth, int visualHeight) {
        synchronized (mSyncObj) {
            synchronized (syncPreview) {
                videoGLHandler.updatePreviewSize(visualWidth, visualHeight);
            }
        }
    }

    @Override
    public void stopPreview(boolean releaseTexture) {
        synchronized (mSyncObj) {
            videoGLHandler.sendMessage(videoGLHandler.obtainMessage(VideoGLHandler.WHAT_STOP_PREVIEW, releaseTexture));
            synchronized (syncIsLooping) {
                isPreviewing = false;
            }
        }
    }

    @Override
    public boolean startRecording(MediaMuxerWrapper muxer) {
        synchronized (mSyncObj) {
            videoGLHandler.sendMessage(videoGLHandler.obtainMessage(VideoGLHandler.WHAT_START_RECORDING, muxer));
            synchronized (syncIsLooping) {
                if (!isPreviewing && !isStreaming) {
                    videoGLHandler.removeMessages(VideoGLHandler.WHAT_DRAW);
                    videoGLHandler.sendMessageDelayed(videoGLHandler.obtainMessage(VideoGLHandler.WHAT_DRAW,
                            SystemClock.uptimeMillis() + loopingInterval), loopingInterval);
                }
                isStreaming = true;
            }
        }
        return true;
    }

    @Override
    public void updateCameraTexture(SurfaceTexture camTex) {
        synchronized (mSyncObj) {
            if (videoGLHandler != null) {
                videoGLHandler.updateCameraTexture(camTex);
            }
        }
    }

    @Override
    public boolean stopRecording() {
        synchronized (mSyncObj) {
            videoGLHandler.sendEmptyMessage(VideoGLHandler.WHAT_STOP_RECORDING);
            synchronized (syncIsLooping) {
                isStreaming = false;
            }
        }
        return true;
    }

    @Override
    public boolean destroy() {
        synchronized (mSyncObj) {
            videoGLHandler.sendEmptyMessage(VideoGLHandler.WHAT_UNINIT);
            if (videoGLHandlerThread != null) {
                videoGLHandlerThread.quitSafely();
                try {
                    videoGLHandlerThread.join();
                } catch (InterruptedException ignored) {
                }
            }
            videoGLHandlerThread = null;
            videoGLHandler = null;
            return true;
        }
    }

    @Override
    public void setCurrentCamera(int cameraIndex) {
        synchronized (mSyncObj) {
            if (videoGLHandler != null) {
                videoGLHandler.updateCameraIndex(cameraIndex);
            }
        }
    }

    public void setVideoFilter(BaseHardVideoFilter baseHardVideoFilter) {
        lockVideoFilter.lock();
        mHardVideoFilter = baseHardVideoFilter;
        if (mHardVideoFilter != null) {
            int previewWidth;
            int previewHeight;
            if (mMediaMakerConfig.isPortrait) {
                previewWidth = mMediaMakerConfig.previewVideoWidth;
                previewHeight = mMediaMakerConfig.previewVideoHeight;
            } else {
                previewWidth = mMediaMakerConfig.previewVideoHeight;
                previewHeight = mMediaMakerConfig.previewVideoWidth;
            }
            Log.d(TAG, "VideoFilter preView size is " + previewWidth + " x " + previewHeight);
            mHardVideoFilter.updatePreviewSize(previewWidth, previewHeight);
            mHardVideoFilter.updateSquareFlag(mMediaMakerConfig.isSquare);
            mHardVideoFilter.updateCropRatio(mMediaMakerConfig.cropRatio);
        }
        lockVideoFilter.unlock();
    }

    @Override
    public void setVideoChangeListener(IVideoChange listener) {
        synchronized (syncVideoChangeListener) {
            mVideoChangeListener = listener;
        }
    }

    private class VideoGLHandler extends Handler {

        static final int WHAT_INIT = 0x001;
        static final int WHAT_UNINIT = 0x002;
        static final int WHAT_FRAME = 0x003;
        static final int WHAT_DRAW = 0x004;
        static final int WHAT_RESET_VIDEO = 0x005;
        static final int WHAT_START_PREVIEW = 0x010;
        static final int WHAT_STOP_PREVIEW = 0x020;
        static final int WHAT_START_STREAMING = 0x100;
        static final int WHAT_STOP_STREAMING = 0x200;
        static final int WHAT_RESET_BITRATE = 0x300;
        static final int WHAT_START_RECORDING = 0x500;
        static final int WHAT_STOP_RECORDING = 0x600;

        static final int FILTER_LOCK_TOLERATION = 3; // 3ms

        private final Object syncFrameNumObj = new Object();
        private final Object syncCameraTexObj = new Object();

        private int frameNum;
        private Size screenSize;

        // gl stuff
        private SurfaceTexture cameraTexture;
        private SurfaceTexture previewScreenTexture;

        private MediaCodecGLWrapper mediaCodecGLWrapper;
        private ScreenGLWrapper previewScreenGLWrapper;
        private OffScreenGLWrapper offScreenGLWrapper;

        private int sample2DFrameBuffer;
        private int sample2DFrameBufferTexture;
        private int frameBuffer;
        private int frameBufferTexture;

        private FloatBuffer shapeVerticesBuffer;
        private FloatBuffer mediaCodecTextureVerticesBuffer;
        private FloatBuffer screenTextureVerticesBuffer;
        private FloatBuffer camera2dTextureVerticesBuffer;
        private FloatBuffer cameraTextureVerticesBuffer;
        private ShortBuffer drawIndexesBuffer;

        private int currCamera;
        private final Object syncCameraBufferObj = new Object();

        private BaseHardVideoFilter innerVideoFilter;
        private int directionFlag;

        // sender
        private VideoSenderThread videoSenderThread;

        boolean hasNewFrame = false;
        boolean dropNextFrame = false;

        public VideoGLHandler(Looper looper) {
            super(looper);
            previewScreenGLWrapper = null;
            mediaCodecGLWrapper = null;
            screenSize = new Size(1, 1);
            initBuffer();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_FRAME: {
                    GLHelper.makeCurrent(offScreenGLWrapper);
                    synchronized (syncFrameNumObj) {
                        synchronized (syncCameraTexObj) {
                            if (cameraTexture == null) {
                                break;
                            }
                            while (frameNum != 0) {
                                cameraTexture.updateTexImage();
//                                if (viewTexture != null) {
//                                    viewTexture.updateTexImage();
//                                }
                                --frameNum;
                                if (!dropNextFrame) {
                                    hasNewFrame = true;
                                } else {
                                    dropNextFrame = false;
                                    hasNewFrame = false;
                                }
                            }
                        }
                    }
                    drawSample2DFrameBuffer(cameraTexture);
                }
                break;
                case WHAT_DRAW: {
                    long time = (Long) msg.obj;
                    long interval = time + loopingInterval - SystemClock.uptimeMillis();
                    synchronized (syncIsLooping) {
                        if (isPreviewing || isStreaming) {
                            if (interval > 0) {
                                videoGLHandler.sendMessageDelayed(videoGLHandler.obtainMessage(
                                        VideoGLHandler.WHAT_DRAW,
                                        SystemClock.uptimeMillis() + interval),
                                        interval);
                            } else {
                                videoGLHandler.sendMessage(videoGLHandler.obtainMessage(
                                        VideoGLHandler.WHAT_DRAW,
                                        SystemClock.uptimeMillis() + loopingInterval));
                            }
                        }
                    }
                    if (hasNewFrame) {
                        drawFrameBuffer();
                        drawMediaCodec(time * 1000000);
                        drawPreviewScreen();
                        hasNewFrame = false;
                    }
                }
                break;
                case WHAT_INIT: {
                    initOffScreenGL();
                }
                break;
                case WHAT_UNINIT: {
                    lockVideoFilter.lock();
                    if (innerVideoFilter != null) {
                        innerVideoFilter.onDestroy();
                        innerVideoFilter = null;
                    }
                    lockVideoFilter.unlock();
                    destroyOffScreenGL();
                }
                break;
                case WHAT_START_PREVIEW: {
                    initPreviewScreenGL((SurfaceTexture) msg.obj);
                    updatePreviewSize(msg.arg1, msg.arg2);
                }
                break;
                case WHAT_STOP_PREVIEW: {
                    destroyPreviewScreenGL();
                    boolean releaseTexture = (boolean) msg.obj;
                    if (releaseTexture) {
                        previewScreenTexture.release();
                        previewScreenTexture = null;
                    }
                }
                break;
                case WHAT_START_RECORDING: {
                    if (mDSTVideoEncoder == null) {
                        mDSTVideoEncoder = MediaCodecHelper.createHardVideoMediaCodec(mMediaMakerConfig, mDSTVideoFormat);
                        if (mDSTVideoEncoder == null) {
                            throw new RuntimeException("create Video MediaCodec failed");
                        }
                    }
                    mDSTVideoEncoder.configure(mDSTVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    initMediaCodecGL(mDSTVideoEncoder.createInputSurface());
                    mDSTVideoEncoder.start();
                    MediaMuxerWrapper muxer = (MediaMuxerWrapper) msg.obj;
                    videoSenderThread = new VideoSenderThread("VideoSenderThread", mDSTVideoEncoder, muxer);
                    videoSenderThread.start();
                }
                break;
                case WHAT_STOP_RECORDING:
                case WHAT_STOP_STREAMING: {
                    videoSenderThread.quit();
                    try {
                        videoSenderThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    videoSenderThread = null;
                    destroyMediaCodecGL();
                    mDSTVideoEncoder.stop();
                    mDSTVideoEncoder.release();
                    mDSTVideoEncoder = null;
                }
                break;
                case WHAT_RESET_BITRATE: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && mediaCodecGLWrapper != null) {
                        Bundle bitrateBundle = new Bundle();
                        bitrateBundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, msg.arg1);
                        mDSTVideoEncoder.setParameters(bitrateBundle);
                    }
                }
                break;
                case WHAT_RESET_VIDEO: {
                    MediaMakerConfig newParameters = (MediaMakerConfig) msg.obj;
                    mMediaMakerConfig.videoWidth = newParameters.videoWidth;
                    mMediaMakerConfig.videoHeight = newParameters.videoHeight;
                    mMediaMakerConfig.cropRatio = newParameters.cropRatio;
                    updateCameraIndex(currCamera);
                    resetFrameBuff();
                    if (mediaCodecGLWrapper != null) {
                        destroyMediaCodecGL();
                        mDSTVideoEncoder.stop();
                        mDSTVideoEncoder.release();
                        mDSTVideoEncoder = MediaCodecHelper.createHardVideoMediaCodec(mMediaMakerConfig, mDSTVideoFormat);
                        if (mDSTVideoEncoder == null) {
                            throw new RuntimeException("create Video MediaCodec failed");
                        }
                        mDSTVideoEncoder.configure(mDSTVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        initMediaCodecGL(mDSTVideoEncoder.createInputSurface());
                        mDSTVideoEncoder.start();
                        videoSenderThread.updateMediaCodec(mDSTVideoEncoder);
                    }
                    synchronized (syncVideoChangeListener) {
                        if (mVideoChangeListener != null) {
                            CallbackDelivery.getInstance().post(new VideoChangeRunable(mVideoChangeListener,
                                    mMediaMakerConfig.videoWidth,
                                    mMediaMakerConfig.videoHeight));
                        }
                    }
                }
                break;
                default:
            }
        }

        /**
         * 将 Camera 预览数据通过 cameraTexture 纹理绘制到 OffScreen FrameBuffer
         *
         * @param cameraTexture
         */
        private void drawSample2DFrameBuffer(SurfaceTexture cameraTexture) {
            // 将 sample2DFrameBuffer FBO 绑定到当前帧缓冲，此处为读写绑定，由于当前帧缓冲并非绑定到默认的帧缓冲，
            // 所以对当前帧缓冲的渲染并不会对窗口的视频输出产生任何影响，称为离屏渲染
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sample2DFrameBuffer);
            // 启用 cam2Program 作为可执行的程序
            GLES20.glUseProgram(offScreenGLWrapper.camera2dProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // 把一个纹理附加到帧缓冲上的时候，所有渲染命令会写入到纹理上
            // 在此之前 OVERWATCH_TEXTURE_ID 纹理 ID 已经作为纹理传入 CameraTexture 接收 Camera 的预览数据了
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, OVERWATCH_CAMERA_TEXTURE_ID);
            GLES20.glUniform1i(offScreenGLWrapper.cam2dTextureLocation, 0);
            synchronized (syncCameraBufferObj) {
                GLHelper.enableVertex(offScreenGLWrapper.cam2dPositionLocation, offScreenGLWrapper.cam2dTextureCoordsLocation,
                        shapeVerticesBuffer, camera2dTextureVerticesBuffer);
            }
            float[] cameraTextureMatrix = new float[16];
            cameraTexture.getTransformMatrix(cameraTextureMatrix);
            GLES20.glUniformMatrix4fv(offScreenGLWrapper.cam2dTextureMatrixLocation, 1, false, cameraTextureMatrix, 0);
            GLES20.glViewport(0, 0, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);

            doGLDraw();

            GLES20.glFinish();
            GLHelper.disableVertex(offScreenGLWrapper.cam2dPositionLocation, offScreenGLWrapper.cam2dTextureCoordsLocation);

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            // 卸载当前执行的 Program
            GLES20.glUseProgram(0);
            // 执行完所需操作后，通过绑定为 0 来使默认帧缓冲被激活
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private void drawOriginFrameBuffer() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
            GLES20.glUseProgram(offScreenGLWrapper.cameraProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sample2DFrameBufferTexture);
            GLES20.glUniform1i(offScreenGLWrapper.camTextureLocation, 0);
            synchronized (syncCameraBufferObj) {
                GLHelper.enableVertex(offScreenGLWrapper.camPositionLocation, offScreenGLWrapper.camTextureCoordsLocation,
                        shapeVerticesBuffer, cameraTextureVerticesBuffer);
            }
            GLES20.glViewport(0, 0, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
            doGLDraw();
            GLHelper.disableVertex(offScreenGLWrapper.camPositionLocation, offScreenGLWrapper.camTextureCoordsLocation);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glUseProgram(0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private void drawFrameBuffer() {
            GLHelper.makeCurrent(offScreenGLWrapper);
            boolean isFilterLocked = lockVideoFilter();
            if (isFilterLocked) {
                if (mHardVideoFilter != innerVideoFilter) {
                    if (innerVideoFilter != null) {
                        innerVideoFilter.onDestroy();
                    }
                    innerVideoFilter = mHardVideoFilter;
                    if (innerVideoFilter != null) {
                        innerVideoFilter.onInit(mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
                    }
                }
                if (innerVideoFilter != null) {
                    synchronized (syncCameraBufferObj) {
                        innerVideoFilter.onDirectionUpdate(directionFlag);
                        innerVideoFilter.onDraw(sample2DFrameBufferTexture, frameBuffer,
                                shapeVerticesBuffer, cameraTextureVerticesBuffer);
                    }
                } else {
                    drawOriginFrameBuffer();
                }
                unlockVideoFilter();
            } else {
                drawOriginFrameBuffer();
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private void drawMediaCodec(long currTime) {
            if (mediaCodecGLWrapper != null) {
                GLHelper.makeCurrent(mediaCodecGLWrapper);
                GLES20.glUseProgram(mediaCodecGLWrapper.drawProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture);
                GLES20.glUniform1i(mediaCodecGLWrapper.drawTextureLoc, 0);
                GLHelper.enableVertex(mediaCodecGLWrapper.drawPositionLoc, mediaCodecGLWrapper.drawTextureCoordLoc,
                        shapeVerticesBuffer, mediaCodecTextureVerticesBuffer);
                doGLDraw();
                GLHelper.disableVertex(mediaCodecGLWrapper.drawPositionLoc, mediaCodecGLWrapper.drawTextureCoordLoc);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glUseProgram(0);
                EGLExt.eglPresentationTimeANDROID(mediaCodecGLWrapper.eglDisplay, mediaCodecGLWrapper.eglSurface, currTime);
                if (!EGL14.eglSwapBuffers(mediaCodecGLWrapper.eglDisplay, mediaCodecGLWrapper.eglSurface)) {
                    throw new RuntimeException("eglSwapBuffers,failed!");
                }
            }
        }

        private void drawPreviewScreen() {
            if (previewScreenGLWrapper != null) {
                GLHelper.makeCurrent(previewScreenGLWrapper);
                GLES20.glUseProgram(previewScreenGLWrapper.drawProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture);
                GLES20.glUniform1i(previewScreenGLWrapper.drawTextureLoc, 0);
                GLHelper.enableVertex(previewScreenGLWrapper.drawPositionLoc, previewScreenGLWrapper.drawTextureCoordLoc,
                        shapeVerticesBuffer, screenTextureVerticesBuffer);
                GLES20.glViewport(0, 0, screenSize.getWidth(), screenSize.getHeight());
                doGLDraw();
                GLHelper.disableVertex(previewScreenGLWrapper.drawPositionLoc, previewScreenGLWrapper.drawTextureCoordLoc);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glUseProgram(0);
                if (!EGL14.eglSwapBuffers(previewScreenGLWrapper.eglDisplay, previewScreenGLWrapper.eglSurface)) {
                    throw new RuntimeException("eglSwapBuffers,failed!");
                }
            }
        }

        private void doGLDraw() {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawIndexesBuffer.limit(), GLES20.GL_UNSIGNED_SHORT, drawIndexesBuffer);
        }

        /**
         * @return ture if filter locked & filter!=null
         */
        private boolean lockVideoFilter() {
            try {
                return lockVideoFilter.tryLock(FILTER_LOCK_TOLERATION, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        private void unlockVideoFilter() {
            lockVideoFilter.unlock();
        }

        private void initOffScreenGL() {
            if (offScreenGLWrapper == null) {
                offScreenGLWrapper = new OffScreenGLWrapper();
                GLHelper.initOffScreenGL(offScreenGLWrapper);
                GLHelper.makeCurrent(offScreenGLWrapper);
                // camera
                offScreenGLWrapper.cameraProgram = GLHelper.createCameraProgram();
                GLES20.glUseProgram(offScreenGLWrapper.cameraProgram);
                offScreenGLWrapper.camPositionLocation = GLES20.glGetAttribLocation(offScreenGLWrapper.cameraProgram, "aPosition");
                offScreenGLWrapper.camTextureCoordsLocation = GLES20.glGetAttribLocation(offScreenGLWrapper.cameraProgram, "aTextureCoord");
                offScreenGLWrapper.camTextureLocation = GLES20.glGetUniformLocation(offScreenGLWrapper.cameraProgram, "uTexture");
                // camera 2d
                offScreenGLWrapper.camera2dProgram = GLHelper.createCamera2DProgram();
                GLES20.glUseProgram(offScreenGLWrapper.camera2dProgram);
                offScreenGLWrapper.cam2dPositionLocation = GLES20.glGetAttribLocation(offScreenGLWrapper.camera2dProgram, "aPosition");
                offScreenGLWrapper.cam2dTextureCoordsLocation = GLES20.glGetAttribLocation(offScreenGLWrapper.camera2dProgram, "aTextureCoord");
                offScreenGLWrapper.cam2dTextureLocation = GLES20.glGetUniformLocation(offScreenGLWrapper.camera2dProgram, "uTexture");
                offScreenGLWrapper.cam2dTextureMatrixLocation = GLES20.glGetUniformLocation(offScreenGLWrapper.camera2dProgram, "uTextureMatrix");

                int[] fb = new int[1];
                int[] fbTexture = new int[1];
                GLHelper.createFrameBuffer(fb, fbTexture, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
                sample2DFrameBuffer = fb[0];
                sample2DFrameBufferTexture = fbTexture[0];
                GLHelper.createFrameBuffer(fb, fbTexture, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
                frameBuffer = fb[0];
                frameBufferTexture = fbTexture[0];
            } else {
                throw new IllegalStateException("initOffScreenGL without destroyOffScreenGL");
            }
        }

        private void destroyOffScreenGL() {
            if (offScreenGLWrapper != null) {
                GLHelper.makeCurrent(offScreenGLWrapper);
                GLES20.glDeleteProgram(offScreenGLWrapper.cameraProgram);
                GLES20.glDeleteProgram(offScreenGLWrapper.camera2dProgram);
                GLES20.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
                GLES20.glDeleteTextures(1, new int[]{frameBufferTexture}, 0);
                GLES20.glDeleteFramebuffers(1, new int[]{sample2DFrameBuffer}, 0);
                GLES20.glDeleteTextures(1, new int[]{sample2DFrameBufferTexture}, 0);
                EGL14.eglDestroySurface(offScreenGLWrapper.eglDisplay, offScreenGLWrapper.eglSurface);
                EGL14.eglDestroyContext(offScreenGLWrapper.eglDisplay, offScreenGLWrapper.eglContext);
                EGL14.eglTerminate(offScreenGLWrapper.eglDisplay);
                EGL14.eglMakeCurrent(offScreenGLWrapper.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            } else {
                throw new IllegalStateException("destroyOffScreenGL without initOffScreenGL");
            }
        }

        private void initPreviewScreenGL(SurfaceTexture screenSurfaceTexture) {
            if (previewScreenGLWrapper == null) {
                previewScreenTexture = screenSurfaceTexture;
                previewScreenGLWrapper = new ScreenGLWrapper();
                // Share offScreenGLWrapper.eglContext 资源共享，状态独立
                GLHelper.initScreenGL(previewScreenGLWrapper, offScreenGLWrapper.eglContext, screenSurfaceTexture);
                GLHelper.makeCurrent(previewScreenGLWrapper);
                previewScreenGLWrapper.drawProgram = GLHelper.createScreenProgram();
                GLES20.glUseProgram(previewScreenGLWrapper.drawProgram);
                previewScreenGLWrapper.drawPositionLoc = GLES20.glGetAttribLocation(previewScreenGLWrapper.drawProgram, "aPosition");
                previewScreenGLWrapper.drawTextureCoordLoc = GLES20.glGetAttribLocation(previewScreenGLWrapper.drawProgram, "aTextureCoord");
                previewScreenGLWrapper.drawTextureLoc = GLES20.glGetUniformLocation(previewScreenGLWrapper.drawProgram, "uTexture");
            } else {
                throw new IllegalStateException("initScreenGL without destroyScreenGL");
            }
        }

        private void destroyPreviewScreenGL() {
            if (previewScreenGLWrapper != null) {
                GLHelper.makeCurrent(previewScreenGLWrapper);
                GLES20.glDeleteProgram(previewScreenGLWrapper.drawProgram);
                EGL14.eglDestroySurface(previewScreenGLWrapper.eglDisplay, previewScreenGLWrapper.eglSurface);
                EGL14.eglDestroyContext(previewScreenGLWrapper.eglDisplay, previewScreenGLWrapper.eglContext);
                EGL14.eglTerminate(previewScreenGLWrapper.eglDisplay);
                EGL14.eglMakeCurrent(previewScreenGLWrapper.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                previewScreenGLWrapper = null;
            } else {
                throw new IllegalStateException("destroyScreenGL without initScreenGL");
            }
        }

        private void initMediaCodecGL(Surface mediaCodecSurface) {
            if (mediaCodecGLWrapper == null) {
                mediaCodecGLWrapper = new MediaCodecGLWrapper();
                GLHelper.initMediaCodecGL(mediaCodecGLWrapper, offScreenGLWrapper.eglContext, mediaCodecSurface);
                GLHelper.makeCurrent(mediaCodecGLWrapper);
                GLES20.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                mediaCodecGLWrapper.drawProgram = GLHelper.createMediaCodecProgram();
                GLES20.glUseProgram(mediaCodecGLWrapper.drawProgram);
                mediaCodecGLWrapper.drawPositionLoc = GLES20.glGetAttribLocation(mediaCodecGLWrapper.drawProgram, "aPosition");
                mediaCodecGLWrapper.drawTextureCoordLoc = GLES20.glGetAttribLocation(mediaCodecGLWrapper.drawProgram, "aTextureCoord");
                mediaCodecGLWrapper.drawTextureLoc = GLES20.glGetUniformLocation(mediaCodecGLWrapper.drawProgram, "uTexture");
            } else {
                throw new IllegalStateException("initMediaCodecGL without destroyMediaCodecGL");
            }
        }

        private void destroyMediaCodecGL() {
            if (mediaCodecGLWrapper != null) {
                GLHelper.makeCurrent(mediaCodecGLWrapper);
                GLES20.glDeleteProgram(mediaCodecGLWrapper.drawProgram);
                EGL14.eglDestroySurface(mediaCodecGLWrapper.eglDisplay, mediaCodecGLWrapper.eglSurface);
                EGL14.eglDestroyContext(mediaCodecGLWrapper.eglDisplay, mediaCodecGLWrapper.eglContext);
                EGL14.eglTerminate(mediaCodecGLWrapper.eglDisplay);
                EGL14.eglMakeCurrent(mediaCodecGLWrapper.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                mediaCodecGLWrapper = null;
            } else {
                throw new IllegalStateException("destroyMediaCodecGL without initMediaCodecGL");
            }
        }

        private void resetFrameBuff() {
            GLHelper.makeCurrent(offScreenGLWrapper);
            GLES20.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
            GLES20.glDeleteTextures(1, new int[]{frameBufferTexture}, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{sample2DFrameBuffer}, 0);
            GLES20.glDeleteTextures(1, new int[]{sample2DFrameBufferTexture}, 0);
            int[] fb = new int[1], fbt = new int[1];
            GLHelper.createFrameBuffer(fb, fbt, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
            sample2DFrameBuffer = fb[0];
            sample2DFrameBufferTexture = fbt[0];
            GLHelper.createFrameBuffer(fb, fbt, mMediaMakerConfig.videoWidth, mMediaMakerConfig.videoHeight);
            frameBuffer = fb[0];
            frameBufferTexture = fbt[0];
        }

        private void initBuffer() {
            shapeVerticesBuffer = GLHelper.getShapeVerticesBuffer();
            mediaCodecTextureVerticesBuffer = GLHelper.getMediaCodecTextureVerticesBuffer();
            screenTextureVerticesBuffer = GLHelper.getScreenTextureVerticesBuffer();
            updateCameraIndex(currCamera);
            drawIndexesBuffer = GLHelper.getDrawIndexesBuffer();
            cameraTextureVerticesBuffer = GLHelper.getCameraTextureVerticesBuffer();
        }

        void updateCameraIndex(int cameraIndex) {
            synchronized (syncCameraBufferObj) {
                currCamera = cameraIndex;
                if (currCamera == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    directionFlag = mMediaMakerConfig.frontCameraDirectionMode ^ MediaConfig.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL;
                } else {
                    directionFlag = mMediaMakerConfig.backCameraDirectionMode;
                }
                camera2dTextureVerticesBuffer = GLHelper.getCamera2DTextureVerticesBuffer(directionFlag, mMediaMakerConfig.cropRatio);
            }
        }


        void updateCameraTexture(SurfaceTexture surfaceTexture) {
            synchronized (syncCameraTexObj) {
                if (surfaceTexture != cameraTexture) {
                    cameraTexture = surfaceTexture;
                    frameNum = 0;
                    dropNextFrame = true;
                }
            }
        }


        void addFrameNum() {
            synchronized (syncFrameNumObj) {
                ++frameNum;
                this.removeMessages(WHAT_FRAME);
                this.sendMessageAtFrontOfQueue(this.obtainMessage(VideoGLHandler.WHAT_FRAME));
            }
        }

        void updatePreviewSize(int w, int h) {
            screenSize = new Size(w, h);
        }
    }
}
