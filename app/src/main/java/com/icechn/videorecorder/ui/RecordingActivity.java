package com.icechn.videorecorder.ui;

import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.icechn.videorecorder.R;
import com.icechn.videorecorder.client.RecorderClient;
import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.filter.image.DrawMultiImageFilter;
import com.icechn.videorecorder.filter.image.DrawMultiImageFilter.ImageDrawData;
import com.icechn.videorecorder.filter.softaudiofilter.SetVolumeAudioFilter;
import com.icechn.videorecorder.model.MediaConfig;
import com.icechn.videorecorder.model.RecordConfig;
import com.icechn.videorecorder.model.Size;
import com.icechn.videorecorder.tools.DensityUtil;

import java.util.ArrayList;


public class RecordingActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, View.OnClickListener, IVideoChange {

    public static final String TAG = "main";
    public static final String IS_SQUARE = "is_square";

    protected RecorderClient mRecorderClient;
    protected AspectTextureView mTextureView;
    protected Handler mainHandler;
    protected Button startRecordButton;
    protected boolean started;
    protected String mSaveVideoPath = null;
    protected boolean mIsSquare = false;
    RecordConfig recordConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mIsSquare = getIntent().getBooleanExtra(IS_SQUARE, false);
        mSaveVideoPath = Environment.getExternalStorageDirectory().getPath() + "/live_save_video" + System.currentTimeMillis() + ".mp4";
        started = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming);
        mTextureView = findViewById(R.id.preview_texture_view);
        mTextureView.setKeepScreenOn(true);
        mTextureView.setSurfaceTextureListener(this);

        startRecordButton = findViewById(R.id.btn_toggle);
        startRecordButton.setOnClickListener(this);

        findViewById(R.id.btn_swap).setOnClickListener(this);
        findViewById(R.id.btn_flash).setOnClickListener(this);

        prepareStreamingClient();

        // onSetFilters();

        printScreenDisplayInfo();
    }

    private void initGLRenderView() {
//        final GLSurfaceView  mGLSurfaceView = findViewById(R.id.gl_surface_view);
//        final GLRenderable mGLLinearLayout= findViewById(R.id.gl_layout);
//        ViewToGLRenderer viewToGlRenderer = new NormalGLRenderer(this);
//        mGLSurfaceView.setEGLContextClientVersion(2);
//        mGLSurfaceView.setRenderer(viewToGlRenderer);
//        mGLLinearLayout.setViewToGLRenderer(viewToGlRenderer);


    }

    private void activeGLRender() {
        final TextView textView = findViewById(R.id.gl_text_view);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startPopsAnimTrans(textView);
            }
        }, 2000);

        final ImageView iconView = findViewById(R.id.gl_icon_view);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startPopsAnimTrans(iconView);
                textView.setText("NiuBi");
            }
        }, 4000);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (started) {
            mRecorderClient.stopRecording();
        }
        if (mRecorderClient != null) {
            mRecorderClient.destroy();
        }
        super.onDestroy();
    }

    private void prepareStreamingClient() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        mRecorderClient = new RecorderClient();
        recordConfig = RecordConfig.obtain();
        if (mIsSquare) {
            recordConfig.setTargetVideoSize(new Size(480, 480));
        } else {
            // 需要加上横屏竖屏的判断为好
            // recordConfig.setTargetVideoSize(new Size(960, 480));
            recordConfig.setTargetVideoSize(new Size(screenHeight, screenWidth));
        }
        recordConfig.setSquare(true);
        recordConfig.setBitRate(750 * 1024);
        recordConfig.setVideoFPS(20);
        recordConfig.setVideoGOP(1);
        recordConfig.setRenderingMode(MediaConfig.Rending_Model_OpenGLES);
        // Camera record config
        recordConfig.setDefaultCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        int frontDirection, backDirection;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, cameraInfo);
        frontDirection = cameraInfo.orientation;
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        backDirection = cameraInfo.orientation;
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            recordConfig.setFrontCameraDirectionMode((frontDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_270
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_90) | MediaConfig.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
            recordConfig.setBackCameraDirectionMode((backDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_90
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_270));
        } else {
            recordConfig.setBackCameraDirectionMode((backDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_0
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_180));
            recordConfig.setFrontCameraDirectionMode((frontDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_180
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_0) | MediaConfig.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
        }

        // save video
        recordConfig.setSaveVideoPath(mSaveVideoPath);

        if (!mRecorderClient.prepare(this, recordConfig)) {
            mRecorderClient = null;
            Log.e("RecordingActivity", "prepare,failed!!");
            Toast.makeText(this, "StreamingClient prepare failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // resize TextureView
        Size videoSize = mRecorderClient.getVideoSize();
        Log.d(TAG, "VideoSize = " + videoSize.toString());
        mTextureView.setAspectRatio(AspectTextureView.MODE_INSIDE, ((double) videoSize.getWidth()) / videoSize.getHeight());
        mRecorderClient.setVideoChangeListener(this);
        mRecorderClient.setSoftAudioFilter(new SetVolumeAudioFilter());
    }

    protected void onSetFilters() {
        ArrayList<ImageDrawData> imageDrawDataList = new ArrayList<>();

        ImageDrawData data = new ImageDrawData();
        data.resId = R.drawable.t;
        int left = DensityUtil.dip2px(this, 30);
        int top = DensityUtil.dip2px(this, 30);
        int right = left + DensityUtil.dip2px(this, 69 * 2);
        int bottom = top + DensityUtil.dip2px(this, 25 * 2);
        data.rect = new Rect(left, top, right, bottom);
        imageDrawDataList.add(data);

        ImageDrawData data2 = new ImageDrawData();
        data2.resId = R.drawable.t;
        left = DensityUtil.dip2px(this, 80);
        top = DensityUtil.dip2px(this, 80);
        right = left + DensityUtil.dip2px(this, 69 * 2);
        bottom = top + DensityUtil.dip2px(this, 25 * 2);
        data2.rect = new Rect(left, top, right, bottom);
        imageDrawDataList.add(data2);

        mRecorderClient.setHardVideoFilter(new DrawMultiImageFilter(this, imageDrawDataList));
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        mTextureView.setAspectRatio(AspectTextureView.MODE_INSIDE, ((double) width) / height);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mRecorderClient != null) {
            Log.d(TAG, "onSurfaceTextureAvailable width - " + width + " height - " + height);
            mRecorderClient.startPreview(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mRecorderClient != null) {
            mRecorderClient.updatePreview(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mRecorderClient != null) {
            mRecorderClient.stopPreview(true);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_toggle:
                if (!started) {
                    startRecordButton.setText("stop");
                    mRecorderClient.startRecording();
                } else {
                    startRecordButton.setText("start");
                    mRecorderClient.stopRecording();
                    Log.d(TAG, "Save video path - " + mSaveVideoPath);
                    Toast.makeText(RecordingActivity.this, "视频文件已保存至" + mSaveVideoPath, Toast.LENGTH_SHORT).show();
                }
                started = !started;
                break;
            case R.id.btn_swap:
                mRecorderClient.swapCamera();
                findViewById(R.id.btn_flash).setVisibility(mRecorderClient.isFrontCamera() ? View.GONE : View.VISIBLE);
                break;
            case R.id.btn_flash:
                mRecorderClient.toggleFlashLight();
                break;
        }
    }

    private void printScreenDisplayInfo() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int screenDensity = metrics.densityDpi;
        Log.d(TAG, "Screen info : size - " + screenWidth + " x " + screenHeight + " and density - " + screenDensity);
    }

    // 属性动画-平移
    private void startPopsAnimTrans(final View view) {
        float[] x = {260f};
        ObjectAnimator objectAnimatorX = ObjectAnimator.ofFloat(view, "translationX", x);
        objectAnimatorX.setDuration(1000);
        objectAnimatorX.start();
    }
}
