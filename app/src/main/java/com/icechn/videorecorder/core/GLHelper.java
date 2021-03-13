package com.icechn.videorecorder.core;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGL10;

import com.icechn.videorecorder.model.MediaMakerConfig;
import com.icechn.videorecorder.model.MediaCodecGLWrapper;
import com.icechn.videorecorder.model.OffScreenGLWrapper;
import com.icechn.videorecorder.model.ScreenGLWrapper;
import com.icechn.videorecorder.tools.GLESTools;

/**
 * Created by lake on 16-5-25.
 * for inner use
 */
public class GLHelper {

    // Android-specific extension.
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private static final String VERTEX_SHADER = "" +
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main(){\n" +
            "    gl_Position= aPosition;\n" +
            "    vTextureCoord = aTextureCoord;\n" +
            "}";

    private static final String FRAGMENT_SHADER_2D = "" +
            "precision highp float;\n" +
            "varying highp vec2 vTextureCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main(){\n" +
            "    vec4 color = texture2D(uTexture, vTextureCoord);\n" +
            "    gl_FragColor = color;\n" +
            "}";

    private static final String FRAGMENT_SHADER_CAMERA = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying highp vec2 vTextureCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main(){\n" +
            "    vec4 color = texture2D(uTexture, vTextureCoord);\n" +
            "    gl_FragColor = color;\n" +
            "}";


    private static final String VERTEX_SHADER_CAMERA2D = "" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main(){\n" +
            "    gl_Position= aPosition;\n" +
            "    vTextureCoord = (uTextureMatrix * aTextureCoord).xy;\n" +
            "}";

    /**
     * Android 相机输出的原始数据一般都为 YUV 数据，而在 OpenGL 中使用的绝大部分纹理 ID 都是 RGBA 的格式，
     * 所以原始数据都是无法直接用 OpenGL 来渲染的。所以我们添加了一个扩展 GL_OES_EGL_image_external
     * 使用 OES 纹理后，我们不需要在片段着色器中自己做 YUV to RGBA 的转换，因为 OES 纹理可以直接接收 YUV 数据或者直接输出 YUV 数据
     * samplerExternalOES 是 Android 用来渲染相机数据，相应的，需要绑定到 GL_TEXTURE_EXTERNAL_OES
     * sampler2D 则用于渲染 RGB 的，绑定 GL_TEXTURE_2D
     * https://juejin.cn/post/6844903834523811853
     */
    private static final String FRAGMENT_SHADER_CAMERA2D = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying highp vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(uTexture, vTextureCoord);\n" +
            "    gl_FragColor = color;\n" +
            "}";

    private static final short[] drawIndices = {
            0, 1, 2,
            0, 2, 3
    };

    private static final float[] SquareVertices = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f
    };
    private static final float[] CamTextureVertices = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };
    private static final float[] Camera2dTextureVertices = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };
    private static final float[] Cam2dTextureVertices_90 = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f
    };
    private static final float[] Cam2dTextureVertices_180 = {
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.0f
    };
    private static final float[] Cam2dTextureVertices_270 = {
            1.0f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };
    private static final float[] MediaCodecTextureVertices = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };
    private static final float[] ScreenTextureVertices = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };

    public static int FLOAT_SIZE_BYTES = 4;
    public static int SHORT_SIZE_BYTES = 2;
    public static int COORDS_PER_VERTEX = 2;
    public static int TEXTURE_COORDS_PER_VERTEX = 2;

    public static void initOffScreenGL(OffScreenGLWrapper wrapper) {
        wrapper.eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (EGL14.EGL_NO_DISPLAY == wrapper.eglDisplay) {
            throw new RuntimeException("initOffScreenGL get eglGetDisplay has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] versions = new int[2];
        if (!EGL14.eglInitialize(wrapper.eglDisplay, versions, 0, versions, 1)) {
            throw new RuntimeException("initOffScreenGL eglInitialize has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = new int[]{
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
        EGL14.eglChooseConfig(wrapper.eglDisplay, configSpec, 0, configs, 0, 1, configsCount, 0);
        if (configsCount[0] <= 0) {
            throw new RuntimeException("initOffScreenGL eglChooseConfig has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        wrapper.eglConfig = configs[0];

        int[] surfaceAttributes = {
                EGL10.EGL_WIDTH, 1,
                EGL10.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        int[] contextSpec = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        wrapper.eglContext = EGL14.eglCreateContext(wrapper.eglDisplay, wrapper.eglConfig, EGL14.EGL_NO_CONTEXT, contextSpec, 0);
        if (EGL14.EGL_NO_CONTEXT == wrapper.eglContext) {
            throw new RuntimeException("initOffScreenGL eglCreateContext has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] values = new int[1];
        EGL14.eglQueryContext(wrapper.eglDisplay, wrapper.eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);
        // 创建的是离屏 Surface
        wrapper.eglSurface = EGL14.eglCreatePbufferSurface(wrapper.eglDisplay, wrapper.eglConfig, surfaceAttributes, 0);
        if (null == wrapper.eglSurface || EGL14.EGL_NO_SURFACE == wrapper.eglSurface) {
            throw new RuntimeException("initOffScreenGL eglCreatePBufferSurface has failed:" + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void initMediaCodecGL(MediaCodecGLWrapper wrapper, EGLContext sharedContext, Surface mediaInputSurface) {
        wrapper.eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (EGL14.EGL_NO_DISPLAY == wrapper.eglDisplay) {
            throw new RuntimeException("initMediaCodecGL get eglGetDisplay has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] versions = new int[2];
        if (!EGL14.eglInitialize(wrapper.eglDisplay, versions, 0, versions, 1)) {
            throw new RuntimeException("initMediaCodecGL eglInitialize has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        // 注意下方的 EGL_RECORDABLE_ANDROID —— Android-specific extension.
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = new int[]{
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
        EGL14.eglChooseConfig(wrapper.eglDisplay, configSpec, 0, configs, 0, 1, configsCount, 0);
        if (configsCount[0] <= 0) {
            throw new RuntimeException("initMediaCodecGL eglChooseConfig has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        wrapper.eglConfig = configs[0];

        int[] surfaceAttributes = {
                EGL14.EGL_NONE
        };
        int[] contextSpec = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        wrapper.eglContext = EGL14.eglCreateContext(wrapper.eglDisplay, wrapper.eglConfig, sharedContext, contextSpec, 0);
        if (EGL14.EGL_NO_CONTEXT == wrapper.eglContext) {
            throw new RuntimeException("initMediaCodecGL eglCreateContext has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] values = new int[1];
        EGL14.eglQueryContext(wrapper.eglDisplay, wrapper.eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);
        // 创建的是窗口 Surface
        wrapper.eglSurface = EGL14.eglCreateWindowSurface(wrapper.eglDisplay, wrapper.eglConfig, mediaInputSurface, surfaceAttributes, 0);
        if (null == wrapper.eglSurface || EGL14.EGL_NO_SURFACE == wrapper.eglSurface) {
            throw new RuntimeException("initMediaCodecGL eglCreateWindowSurface has failed:" + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void initScreenGL(ScreenGLWrapper wrapper, EGLContext sharedContext, SurfaceTexture screenSurface) {
        wrapper.eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (EGL14.EGL_NO_DISPLAY == wrapper.eglDisplay) {
            throw new RuntimeException("initScreenGL get eglGetDisplay has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] versions = new int[2];
        if (!EGL14.eglInitialize(wrapper.eglDisplay, versions, 0, versions, 1)) {
            throw new RuntimeException("initScreenGL get eglInitialize has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = new int[]{
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
        EGL14.eglChooseConfig(wrapper.eglDisplay, configSpec, 0, configs, 0, 1, configsCount, 0);
        if (configsCount[0] <= 0) {
            throw new RuntimeException("initScreenGL get eglChooseConfig has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        wrapper.eglConfig = configs[0];

        int[] surfaceAttributes = {
                EGL14.EGL_NONE
        };
        int[] contextSpec = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        wrapper.eglContext = EGL14.eglCreateContext(wrapper.eglDisplay, wrapper.eglConfig, sharedContext, contextSpec, 0);
        if (EGL14.EGL_NO_CONTEXT == wrapper.eglContext) {
            throw new RuntimeException("initScreenGL get eglCreateContext has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] values = new int[1];
        EGL14.eglQueryContext(wrapper.eglDisplay, wrapper.eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);
        wrapper.eglSurface = EGL14.eglCreateWindowSurface(wrapper.eglDisplay, wrapper.eglConfig, screenSurface, surfaceAttributes, 0);
        if (null == wrapper.eglSurface || EGL14.EGL_NO_SURFACE == wrapper.eglSurface) {
            throw new RuntimeException("initScreenGL get eglCreateWindowSurface has failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    /**
     * 切换为 OffScreenGLWrapper OpenGL 上下文
     * OpenGL API 在将 OffScreenWrapper Surface 作为渲染目标，而 display 则作为 Surface 的前端显示
     * PS : EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) 返回的实例是单例，是同一个对象
     *
     * @param wrapper
     */
    public static void makeCurrent(OffScreenGLWrapper wrapper) {
        if (!EGL14.eglMakeCurrent(wrapper.eglDisplay, wrapper.eglSurface, wrapper.eglSurface, wrapper.eglContext)) {
            throw new RuntimeException("makeCurrent off-screen context failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void makeCurrent(MediaCodecGLWrapper wrapper) {
        if (!EGL14.eglMakeCurrent(wrapper.eglDisplay, wrapper.eglSurface, wrapper.eglSurface, wrapper.eglContext)) {
            throw new RuntimeException("makeCurrent media codec context failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void makeCurrentForView(MediaCodecGLWrapper wrapper) {
        if (!EGL14.eglMakeCurrent(wrapper.eglDisplay, wrapper.eglSurface, wrapper.eglSurface, wrapper.eglViewContext)) {
            throw new RuntimeException("makeCurrent media codec context failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void makeCurrent(ScreenGLWrapper wrapper) {
        if (!EGL14.eglMakeCurrent(wrapper.eglDisplay, wrapper.eglSurface, wrapper.eglSurface, wrapper.eglContext)) {
            throw new RuntimeException("makeCurrent screen context failed : " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    public static void createFrameBuffer(int[] frameBuffer, int[] frameBufferTexture, int width, int height) {
        GLES20.glGenFramebuffers(1, frameBuffer, 0);
        GLES20.glGenTextures(1, frameBufferTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, frameBufferTexture[0], 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLESTools.checkGlError("createCameraFrameBuffer");
    }

    public static void enableVertex(int posLoc, int texLoc, FloatBuffer shapeBuffer, FloatBuffer texBuffer) {
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(posLoc, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                COORDS_PER_VERTEX * 4, shapeBuffer);
        GLES20.glVertexAttribPointer(texLoc, TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                TEXTURE_COORDS_PER_VERTEX * 4, texBuffer);
    }

    public static void disableVertex(int posLoc, int texLoc) {
        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    public static int createCamera2DProgram() {
        return GLESTools.createProgram(VERTEX_SHADER_CAMERA2D, FRAGMENT_SHADER_CAMERA2D);
    }

    public static int createView2DProgram() {
        return GLESTools.createProgram(VERTEX_SHADER_CAMERA2D, FRAGMENT_SHADER_CAMERA2D);
    }

    public static int createCameraProgram() {
        return GLESTools.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_CAMERA);
    }

    public static int createMediaCodecProgram() {
        return GLESTools.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
    }

    public static int createScreenProgram() {
        return GLESTools.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
    }

    public static ShortBuffer getDrawIndexesBuffer() {
        ShortBuffer result = ByteBuffer.allocateDirect(SHORT_SIZE_BYTES * drawIndices.length).
                order(ByteOrder.nativeOrder()).
                asShortBuffer();
        result.put(drawIndices);
        result.position(0);
        return result;
    }

    public static FloatBuffer getShapeVerticesBuffer() {
        FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * SquareVertices.length).
                order(ByteOrder.nativeOrder()).
                asFloatBuffer();
        result.put(SquareVertices);
        result.position(0);
        return result;
    }

    public static FloatBuffer getMediaCodecTextureVerticesBuffer() {
        FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * MediaCodecTextureVertices.length).
                order(ByteOrder.nativeOrder()).
                asFloatBuffer();
        result.put(MediaCodecTextureVertices);
        result.position(0);
        return result;
    }

    public static FloatBuffer getScreenTextureVerticesBuffer() {
        FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * ScreenTextureVertices.length).
                order(ByteOrder.nativeOrder()).
                asFloatBuffer();
        result.put(ScreenTextureVertices);
        result.position(0);
        return result;
    }

    public static FloatBuffer getCamera2DTextureVerticesBuffer(final int directionFlag, final float cropRatio) {
        if (directionFlag == -1) {
            FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * Camera2dTextureVertices.length).
                    order(ByteOrder.nativeOrder()).
                    asFloatBuffer();
            result.put(CamTextureVertices);
            result.position(0);
            return result;
        }
        float[] buffer;
        switch (directionFlag & 0xF0) {
            case MediaMakerConfig.FLAG_DIRECTION_ROATATION_90:
                buffer = Cam2dTextureVertices_90.clone();
                break;
            case MediaMakerConfig.FLAG_DIRECTION_ROATATION_180:
                buffer = Cam2dTextureVertices_180.clone();
                break;
            case MediaMakerConfig.FLAG_DIRECTION_ROATATION_270:
                buffer = Cam2dTextureVertices_270.clone();
                break;
            default:
                buffer = Camera2dTextureVertices.clone();
        }

        if ((directionFlag & 0xF0) == MediaMakerConfig.FLAG_DIRECTION_ROATATION_0
                || (directionFlag & 0xF0) == MediaMakerConfig.FLAG_DIRECTION_ROATATION_180) {
            if (cropRatio > 0) {
                buffer[1] = buffer[1] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[3] = buffer[3] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[5] = buffer[5] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[7] = buffer[7] == 1.0f ? (1.0f - cropRatio) : cropRatio;
            } else {
                buffer[0] = buffer[0] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[2] = buffer[2] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[4] = buffer[4] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[6] = buffer[6] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
            }
        } else {
            if (cropRatio > 0) {
                buffer[0] = buffer[0] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[2] = buffer[2] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[4] = buffer[4] == 1.0f ? (1.0f - cropRatio) : cropRatio;
                buffer[6] = buffer[6] == 1.0f ? (1.0f - cropRatio) : cropRatio;
            } else {
                buffer[1] = buffer[1] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[3] = buffer[3] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[5] = buffer[5] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
                buffer[7] = buffer[7] == 1.0f ? (1.0f + cropRatio) : -cropRatio;
            }
        }

        if ((directionFlag & MediaMakerConfig.FLAG_DIRECTION_FLIP_HORIZONTAL) != 0) {
            buffer[0] = flip(buffer[0]);
            buffer[2] = flip(buffer[2]);
            buffer[4] = flip(buffer[4]);
            buffer[6] = flip(buffer[6]);
        }
        if ((directionFlag & MediaMakerConfig.FLAG_DIRECTION_FLIP_VERTICAL) != 0) {
            buffer[1] = flip(buffer[1]);
            buffer[3] = flip(buffer[3]);
            buffer[5] = flip(buffer[5]);
            buffer[7] = flip(buffer[7]);
        }

        FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * buffer.length).
                order(ByteOrder.nativeOrder()).
                asFloatBuffer();
        result.put(buffer);
        result.position(0);
        return result;
    }

    public static FloatBuffer getCameraTextureVerticesBuffer() {
        FloatBuffer result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * Camera2dTextureVertices.length).
                order(ByteOrder.nativeOrder()).
                asFloatBuffer();
        result.put(CamTextureVertices);
        result.position(0);
        return result;
    }

    private static float flip(final float i) {
        return (1.0f - i);
    }
}
