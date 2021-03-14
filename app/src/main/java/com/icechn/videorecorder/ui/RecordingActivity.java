package com.icechn.videorecorder.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.icechn.videorecorder.R;
import com.icechn.videorecorder.client.RecorderClient;
import com.icechn.videorecorder.core.listener.IVideoChange;
import com.icechn.videorecorder.filter.image.AnimImageFilter;
import com.icechn.videorecorder.filter.image.DrawMultiImageFilter;
import com.icechn.videorecorder.filter.image.DrawMultiImageFilter.ImageDrawData;
import com.icechn.videorecorder.filter.softaudiofilter.SetVolumeAudioFilter;
import com.icechn.videorecorder.model.MediaConfig;
import com.icechn.videorecorder.model.RecordConfig;
import com.icechn.videorecorder.model.Size;
import com.icechn.videorecorder.test.ViewToGLRenderer;
import com.icechn.videorecorder.tools.DensityUtil;

import java.util.ArrayList;
import java.util.List;


public class RecordingActivity extends Activity implements TextureView.SurfaceTextureListener, View.OnClickListener, IVideoChange {

    public static final String TAG = "main";
    public static final String IS_SQUARE = "is_square";

    // Object
    private Handler mMainHandler;
    private RecorderClient mRecorderClient;
    private ViewToGLRenderer mViewToGLRender;

    // View
    private GLSurfaceView mGLSurfaceView;
    private AspectTextureView mTextureView;
    private Button startRecordButton;
    private LinearLayout mAnimLayout;
    private View mAnimIconView;

    // Config
    private boolean mStarted;
    private boolean mIsSquare;
    private String mSaveVideoPath;
    private RecordConfig mRecordConfig;

    private int mScreenWidth;
    private int mScreenHeight;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mIsSquare = getIntent().getBooleanExtra(IS_SQUARE, false);
        mSaveVideoPath = Environment.getExternalStorageDirectory().getPath() + "/live_save_video" + System.currentTimeMillis() + ".mp4";
        mStarted = false;
        printScreenDisplayInfo();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming);

        mAnimLayout = findViewById(R.id.gl_layout);
        mAnimIconView = findViewById(R.id.gl_icon_view);

        mTextureView = findViewById(R.id.preview_texture_view);
        mTextureView.setKeepScreenOn(true);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOpaque(false);

        startRecordButton = findViewById(R.id.btn_toggle);
        startRecordButton.setOnClickListener(this);

        findViewById(R.id.btn_swap).setOnClickListener(this);
        findViewById(R.id.btn_flash).setOnClickListener(this);

        prepareStreamingClient();
        onSetFilters();
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
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
        if (mStarted) {
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
        mRecordConfig = RecordConfig.obtain();
        if (mIsSquare) {
            mRecordConfig.setTargetVideoSize(new Size(480, 480));
        } else {
            // 需要加上横屏竖屏的判断为好
            // recordConfig.setTargetVideoSize(new Size(960, 480));
            mRecordConfig.setTargetVideoSize(new Size(screenHeight, screenWidth));
        }
        mRecordConfig.setSquare(true);
        mRecordConfig.setBitRate(750 * 1024);
        mRecordConfig.setVideoFPS(20);
        mRecordConfig.setVideoGOP(1);
        mRecordConfig.setRenderingMode(MediaConfig.Rending_Model_OpenGLES);
        // Camera record config
        mRecordConfig.setDefaultCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        int frontDirection, backDirection;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, cameraInfo);
        frontDirection = cameraInfo.orientation;
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        backDirection = cameraInfo.orientation;
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mRecordConfig.setFrontCameraDirectionMode((frontDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_270
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_90) | MediaConfig.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
            mRecordConfig.setBackCameraDirectionMode((backDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_90
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_270));
        } else {
            mRecordConfig.setBackCameraDirectionMode((backDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_0
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_180));
            mRecordConfig.setFrontCameraDirectionMode((frontDirection == 90
                    ? MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_180
                    : MediaConfig.DirectionMode.FLAG_DIRECTION_ROATATION_0) | MediaConfig.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
        }

        // save video
        mRecordConfig.setSaveVideoPath(mSaveVideoPath);

        if (!mRecorderClient.prepare(this, mRecordConfig)) {
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
        DrawMultiImageFilter drawMultiImageFilter = new DrawMultiImageFilter(this, imageDrawDataList);

        mAnimLayout.post(new Runnable() {
            @Override
            public void run() {
                Log.d("zwt", "height - " + mAnimLayout.getHeight());
                AnimImageFilter.ImageAnimationData animationData = new AnimImageFilter.ImageAnimationData();
                int animLeft = 0;
                int animTop = 0;
                int animRight = mScreenWidth;
                int animBottom = animTop + mAnimLayout.getHeight();
                animationData.rect = new Rect(animLeft, animTop, animRight, animBottom);
                AnimImageFilter animImageFilter = new AnimImageFilter(RecordingActivity.this, mAnimLayout, mAnimIconView, animationData);
                mRecorderClient.setHardVideoFilter(animImageFilter);
            }
        });
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
                if (!mStarted) {
                    startRecordButton.setText("stop");
                    mRecorderClient.startRecording();
                } else {
                    startRecordButton.setText("start");
                    mRecorderClient.stopRecording();
                    Log.d(TAG, "Save video path - " + mSaveVideoPath);
                    Toast.makeText(RecordingActivity.this, "视频文件已保存至" + mSaveVideoPath, Toast.LENGTH_SHORT).show();
                }
                mStarted = !mStarted;
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
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        int screenDensity = metrics.densityDpi;
        Log.d(TAG, "Screen info : size - " + mScreenWidth + " x " + mScreenHeight + " and density - " + screenDensity);
    }

    private static final int ANIMATION_DURATION = 1000;
    private List<Bitmap> mBitmapList = new ArrayList<>();
    private int mCount = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mCount > ANIMATION_DURATION / 16.7f * 2) {
                return;
            }
            Bitmap bitmap = getViewsScreenShot(mAnimLayout);
            mBitmapList.add(bitmap);
            mCount++;
            mHandler.sendEmptyMessageDelayed(0, 17);
        }
    };


    // 属性动画-平移
    private void startPopsAnimTrans(final View view) {
        float[] x = {460f};
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.1f, 1f);
        ObjectAnimator objectAnimatorX = ObjectAnimator.ofFloat(view, "translationX", x);
        animatorSet.playTogether(scaleX, scaleY, objectAnimatorX);
        animatorSet.setDuration(ANIMATION_DURATION);
        animatorSet.start();
    }

    public static Bitmap getViewsScreenShot(View view) {
        Bitmap bitmap = view.getDrawingCache();
        Bitmap emptyBitmap = Bitmap.createBitmap(bitmap);
        view.destroyDrawingCache();
        return emptyBitmap;
    }
}
