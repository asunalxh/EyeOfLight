package com.example.eyeoflight_lite;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eyeoflight_lite.env.ImageUtils;
import com.intel.realsense.librealsense.Colorizer;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DepthFrame;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameCallback;
import com.intel.realsense.librealsense.FrameReleaser;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamFormat;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.StreamType;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;

public abstract class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity_Log";
    private static final int PERMISSIONS_REQUEST_CAMERA = 0;
    protected static final int width = 640;
    protected static final int height = 480;

    private byte[] bytes = new byte[width * height * 3];

    private GLRsSurfaceView mGLSurfaceView;     //显示画面

    boolean mPermissionsGranted = false;        //获取了USB摄像头权限
    private boolean mIsStreaming = false;       //正在拍摄

    private Pipeline mPipeline;
    private RsContext mRsContext;

    private HandlerThread handlerThread;        //物体识别用线程
    private Handler handler;                    //物体识别用handler


    protected boolean computingDetection = false;   //正在计算图像
    private int[] rgbBytes = null;
    private float[] depth = new float[width * height * 2];


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//保持屏幕常亮

        debug();

        //更改标题字体
        Typeface typeface = Typeface.createFromAsset(getAssets(), "textType.TTF");
        ((TextView) findViewById(R.id.toolbar_title)).setTypeface(typeface);

        //显示屏幕
        mGLSurfaceView = findViewById(R.id.glSurfaceView);
        mGLSurfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        //动态申请USB摄像头权限
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }

        mPermissionsGranted = true;

        onPreviewSizeChosen(new Size(width, height), 0);

    }


    private void debug() {
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }
        mPermissionsGranted = true;
    }


    //初始化并启动Realsense摄像头
    private void realSenseInit() {
        //RsContext.init must be called once in the application lifetime before any interaction with physical RealSense devices.
        //For multi activities applications use the application context instead of the activity context
        RsContext.init(getApplicationContext());

        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
        mRsContext = new RsContext();

        mRsContext.setDevicesChangedCallback(new DeviceListener() {
            @Override
            public void onDeviceAttach() {
                start();
            }

            @Override
            public void onDeviceDetach() {
                stop();
            }
        });

        start();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLSurfaceView.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //如果获得了权限，就开启摄像头
        if (mPermissionsGranted)
            realSenseInit();

        handlerThread = new HandlerThread("handlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRsContext != null)
            mRsContext.close();
        stop();
    }

    protected Frame fr;

    //摄像头帧处理线程
    private final Thread cameraThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                // try statement needed here to release resources allocated by the Pipeline:start() method
                while (!cameraThread.isInterrupted()) {
                    try (FrameSet frames = mPipeline.waitForFrames()) {

                        mGLSurfaceView.upload(frames);

//                        if (isProcessingFrame)
//                            continue;

                        //提取彩色摄像机图像
                        try (Frame rgbFrame = frames.first(StreamType.COLOR)) {
//                            mGLSurfaceView.upload(rgbFrame);//更新显示
                            rgbFrame.getData(bytes);
                        }

                        if (computingDetection) {
//                            Log.d(TAG,"continue");
                            continue;
                        }

                        fr = frames.first(StreamType.DEPTH);


                        //提取深度图像

                        processImage();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });


    //摄像头启动设置
    private void configAndStart() throws Exception {
        try (Config config = new Config()) {
            config.enableStream(StreamType.DEPTH, width, height);
            config.enableStream(StreamType.COLOR, width, height);
            // try statement needed here to release resources allocated by the Pipeline:start() method
            try (PipelineProfile pp = mPipeline.start(config)) {
            }
        }
    }

    //摄像头启动程序
    private synchronized void start() {
        //如果已经启动摄像头，则不再次启动
        if (mIsStreaming)
            return;
        try {
            mPipeline = new Pipeline();

            try (DeviceList dl = mRsContext.queryDevices()) {
                if (dl.getDeviceCount() <= 0) {
                    return;
                }
            }

            mGLSurfaceView.clear();
            configAndStart();
            mIsStreaming = true;
            cameraThread.start();
            //cameraHandler.post(mStreaming);
        } catch (Exception e) {
        }
    }

    //摄像头关闭程序
    private synchronized void stop() {
        //如果摄像头没启动，不执行关闭程序
        if (!mIsStreaming)
            return;
        try {
            mIsStreaming = false;
            //cameraHandler.removeCallbacks(mStreaming);
            cameraThread.interrupt();
            mPipeline.stop();
            mGLSurfaceView.clear();
        } catch (Exception e) {
        }
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected int[] getRgbBytes() {
        return ImageUtils.convertByteToColor(bytes);
    }


    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);
}