package com.icechn.videorecorder.model;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

public class OffScreenGLWrapper {

    public EGLConfig eglConfig;
    public EGLDisplay eglDisplay;
    public EGLSurface eglSurface;
    public EGLContext eglContext;

    public int camera2dProgram;
    public int cam2dTextureMatrixLocation;
    public int cam2dTextureLocation;
    public int cam2dPositionLocation;
    public int cam2dTextureCoordsLocation;

    public int cameraProgram;
    public int camTextureLocation;
    public int camPositionLocation;
    public int camTextureCoordsLocation;

}
