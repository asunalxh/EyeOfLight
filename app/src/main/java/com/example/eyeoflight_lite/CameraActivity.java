package com.example.eyeoflight_lite;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
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
import android.widget.TextView;

import com.example.eyeoflight_lite.Fragment.CameraFragment;
import com.example.eyeoflight_lite.Fragment.FragmentOperation;
import com.example.eyeoflight_lite.Fragment.IndexFragment;
import com.example.eyeoflight_lite.Fragment.MapFragment;
import com.example.eyeoflight_lite.Fragment.MicroBottomFragment;
import com.example.eyeoflight_lite.Fragment.NavigationFragment;
import com.example.eyeoflight_lite.Fragment.SiriBottomFragment;
import com.example.eyeoflight_lite.env.ImageUtils;
import com.example.eyeoflight_lite.env.OCRTool;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamType;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import okhttp3.Response;

public abstract class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity_Log";
    private static final int PERMISSIONS_REQUEST_CAMERA = 0;
    protected static final int width = 640;
    protected static final int height = 480;

    private byte[] bytes = new byte[width * height * 3];

    boolean mPermissionsGranted = false;        //获取了USB摄像头权限
    private boolean mIsStreaming = false;       //正在拍摄

    private Pipeline mPipeline;
    private RsContext mRsContext;

    private HandlerThread handlerThread;        //物体识别用线程
    private Handler handler;                    //物体识别用handler


    protected boolean computingDetection = false;   //正在计算图像
    private int[] rgbBytes = null;
    private float[] depth = new float[width * height * 2];

    protected boolean showRgbFrame = false;     //显示摄像头画面
    protected boolean showBox = false;          //显示方框

    protected Frame fr;

    protected CameraFragment cameraFragment;
    protected IndexFragment indexFragment;
    protected MapFragment mapFragment;
    protected NavigationFragment navigationFragment;
    private SiriBottomFragment siriBottomFragment;
    private MicroBottomFragment microBottomFragment;

    private FragmentType fragmentType;      //正在显示哪个fragment


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//保持屏幕常亮

        //更改标题字体
        Typeface typeface = Typeface.createFromAsset(getAssets(), "textType.TTF");
        ((TextView) findViewById(R.id.toolbar_title)).setTypeface(typeface);


        //动态申请USB摄像头权限
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }

        mPermissionsGranted = true;

        cameraFragment = new CameraFragment();
        indexFragment = new IndexFragment();
        mapFragment = new MapFragment();
        navigationFragment = new NavigationFragment();


        onPreviewSizeChosen(new Size(width, height), 0);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, indexFragment)
                .replace(R.id.bottom_container,new MicroBottomFragment())
                .commit();

        debug();
    }


    private void debug() {


        FloatingActionButton floatingActionButton = findViewById(R.id.floating_btn);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Bitmap bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
                bitmap.setPixels(getRgbBytes(),0,width,0,0,width,height);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
                byte[] b = outputStream.toByteArray();

                OCRTool.getWord(b, new OCRTool.OnGetWords() {
                    @Override
                    public void onGetWords(String words) {
                        Log.d(TAG,"OCR response: " + words);
                    }
                });
            }
        });
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


    //摄像头帧处理线程
    private final Thread cameraThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                // try statement needed here to release resources allocated by the Pipeline:start() method
                while (!cameraThread.isInterrupted()) {
                    try (FrameSet frames = mPipeline.waitForFrames()) {

                        //提取彩色摄像机图像
                        try (Frame rgbFrame = frames.first(StreamType.COLOR)) {
                            //更新显示
                            if (showRgbFrame) {
                                cameraFragment.upload(rgbFrame);
                                Log.d(TAG, "upload the rgbRrame");
                            }

                            rgbFrame.getData(bytes);
                        }

                        if (computingDetection) {
//                            Log.d(TAG,"continue");
                            continue;
                        }

                        //提取深度图像
                        fr = frames.first(StreamType.DEPTH);

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

//            cameraFragment.clearView();
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
//            cameraFragment.clearView();
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

    @Override
    public void onBackPressed() {
        if (fragmentType == FragmentType.IndexFragment)
            super.onBackPressed();
        else {
            switchToIndex();
            showBox = false;
            showRgbFrame = false;
        }
    }

    public void switchToCameraFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, cameraFragment).commit();
        cameraFragment.setOperation(new FragmentOperation() {
            @Override
            public void ResumeOperation() {
                showBox = true;
                showRgbFrame = true;
            }
        });
        fragmentType = FragmentType.CameraFragment;
    }

    public void switchToIndex() {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, indexFragment).commit();
        cameraFragment.setOperation(new FragmentOperation() {
            @Override
            public void ResumeOperation() {
                showBox = false;
                showRgbFrame = false;
            }
        });
        fragmentType = FragmentType.IndexFragment;
    }

    public void switchToMap(){
        getSupportFragmentManager().beginTransaction().replace(R.id.container, mapFragment).commit();
        fragmentType = FragmentType.MapFragment;
    }

    public void switchToNavigation(){
        getSupportFragmentManager().beginTransaction().replace(R.id.container, navigationFragment).commit();
        fragmentType = FragmentType.NavigationFragment;
    }



    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);

    public enum FragmentType {
        CameraFragment, IndexFragment, MapFragment,NavigationFragment
    }
}