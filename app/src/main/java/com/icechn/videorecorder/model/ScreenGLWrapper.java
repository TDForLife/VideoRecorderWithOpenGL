package com.icechn.videorecorder.model;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

public class ScreenGLWrapper {

    public EGLDisplay eglDisplay;
    public EGLConfig eglConfig;
    public EGLSurface eglSurface;
    public EGLContext eglContext;

    public int drawProgram;
    public int drawTextureLoc;
    public int drawPositionLoc;
    public int drawTextureCoordLoc;
}