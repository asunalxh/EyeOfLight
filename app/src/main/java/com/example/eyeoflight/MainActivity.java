package com.example.eyeoflight;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.eyeoflight.Views.OverlayView;
import com.example.eyeoflight.Views.SiriWaveView;
import com.example.eyeoflight.env.BorderedText;
import com.example.eyeoflight.env.ImageUtils;
import com.example.eyeoflight.tflite.Classifier;
import com.example.eyeoflight.tflite.TFLiteObjectDetectionAPIModel;
import com.example.eyeoflight.tracking.MultiBoxTracker;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class MainActivity extends AppCompatActivity {

    protected Size outputSize = new Size(1280, 480);
    protected Size previewSize = new Size(640, 480);
    private HandlerThread detectThread;
    private Handler detectHandler;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private boolean isProcessingFrame = false;
    private int[] rgbBytes = null;

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.55f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private Bitmap baseBitmap;
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    private CameraViewInterface cameraView;
    private UVCCameraHelper uvcCameraHelper;
    private boolean isRequest = false;
    private boolean isPreView = false;

    private RangingHelper rangingHelper;

    private HandlerThread stereoThread;
    private Handler stereoHandler;

    private HandlerThread siriThread;
    private Handler siriHandler;
    private SiriWaveView siri;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    private CyclicBarrier cyclicBarrier;

    private CountDownLatch countDownLatch;

    private List<Classifier.Recognition> results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=" + getString(R.string.app_id));

        onPreviewSizeChosen(90);

        cameraView = findViewById(R.id.cameraTextureView);
        cameraView.setCallback(callback);
        uvcCameraHelper = UVCCameraHelper.getInstance();
        uvcCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        uvcCameraHelper.setDefaultPreviewSize(outputSize.getWidth(), outputSize.getHeight());
        uvcCameraHelper.initUSBMonitor(MainActivity.this, cameraView, onMyDevConnectListener);
        uvcCameraHelper.setOnPreviewFrameListener(onPreViewResultListener);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        siri = findViewById(R.id.siri);
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet_layout));
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    }

    @Override
    protected void onStart() {
        super.onStart();
        detectThread = new HandlerThread("detectThread",-19);
        detectThread.start();
        detectHandler = new Handler(detectThread.getLooper());


        stereoThread = new HandlerThread("stereoThread",-19);
        stereoThread.start();
        stereoHandler = new Handler(stereoThread.getLooper());


        siriThread = new HandlerThread("siriThread");
        siriThread.start();
        siriHandler = new Handler(siriThread.getLooper());
        uvcCameraHelper.registerUSB();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
//            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
//            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    rangingHelper = new RangingHelper();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private UVCCameraHelper.OnMyDevConnectListener onMyDevConnectListener = new UVCCameraHelper.OnMyDevConnectListener() {
        @Override
        public void onAttachDev(UsbDevice device) {
            if (!isRequest) {
                isRequest = true;
                if (uvcCameraHelper != null)
                    uvcCameraHelper.requestPermission(0);
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            if (isRequest) {
                isRequest = false;
                if (uvcCameraHelper != null) {
                    uvcCameraHelper.closeCamera();
                    isPreView = false;
                }
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (isConnected) {
                isPreView = true;
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {

        }
    };

    private CameraViewInterface.Callback callback = new CameraViewInterface.Callback() {
        @Override
        public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
            int width = ((TextureView) view).getWidth();
            int height = ((TextureView) view).getHeight();
            configureTransform(view, width, height, Surface.ROTATION_270);
        }

        @Override
        public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

        }

        @Override
        public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {

        }
    };

    private AbstractUVCCameraHandler.OnPreViewResultListener onPreViewResultListener = new AbstractUVCCameraHandler.OnPreViewResultListener() {
        @Override
        public void onPreviewResult(byte[] data) {

            if (isProcessingFrame)
                return;

            if (rgbBytes == null)
                rgbBytes = new int[outputSize.getWidth() * outputSize.getHeight()];

            isProcessingFrame = true;

            imageConverter = new Runnable() {
                @Override
                public void run() {
                    ImageUtils.convertYUV420SPToARGB8888(data, outputSize.getWidth(), outputSize.getHeight(), rgbBytes);
                }
            };

            postInferenceCallback = new Runnable() {
                @Override
                public void run() {
                    isProcessingFrame = false;
                }
            };
            processImage();
        }
    };

    private void configureTransform(CameraViewInterface view, final int viewWidth, final int viewHeight, int rotation) {
        if (null == view) {
            return;
        }
        final Matrix matrix = new Matrix();
        final RectF preRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF postRect = new RectF(0, 0, viewWidth, viewWidth / 3 * 8);
        final float centerX = postRect.centerX();
        final float centerY = postRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            preRect.offset(centerX - preRect.centerX(), centerY - centerY);
            matrix.setRectToRect(preRect, postRect, Matrix.ScaleToFit.CENTER);
            final float scaleX = postRect.height() / viewWidth;
            final float scaleY = postRect.width() / viewHeight;
            matrix.postScale(scaleX, scaleY, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);

        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        ((TextureView) view).setTransform(matrix);
    }

    private void onPreviewSizeChosen(final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
        }


        sensorOrientation = rotation;
//        rgbFrameBitmaprgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewSize.getWidth(), previewSize.getHeight(),
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });

        tracker.setFrameConfiguration(previewSize.getWidth(), previewSize.getHeight(), sensorOrientation);
    }

    private void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        baseBitmap = Bitmap.createBitmap(outputSize.getWidth(), outputSize.getHeight(), Bitmap.Config.ARGB_8888);
        baseBitmap.setPixels(getRgbBytes(), 0, outputSize.getWidth(), 0, 0, outputSize.getWidth(), outputSize.getHeight());

        rgbFrameBitmap = Bitmap.createBitmap(baseBitmap, 0, 0, previewSize.getWidth(), previewSize.getHeight());

        rangingHelper.setImage(baseBitmap);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

//        if (cyclicBarrier == null)
//            cyclicBarrier = new CyclicBarrier(2);

        countDownLatch = new CountDownLatch(1);

        runInStereoThread(() -> {
            long startTime = SystemClock.uptimeMillis();
            rangingHelper.calculate(RangingHelper.Method.BM);
            long time = SystemClock.uptimeMillis() - startTime;
            Log.d("DebugTag", "Arrived At StereoThread " + time + "ms");
            countDownLatch.countDown();
        });

        runInDetectThread(
                new Runnable() {
                    @Override
                    public void run() {

                        long startTime = SystemClock.uptimeMillis();
                        results = detector.recognizeImage(croppedBitmap);
                        long time = SystemClock.uptimeMillis() - startTime;
                        Log.d("DebugTag", "Arrived At DetectThread " + time + "ms");
                        try {
                            countDownLatch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        Log.d("DebugTag", "Arrived At CyclicBarrier");

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);
                                result.setDistance(rangingHelper.getDis(location.top, location.bottom, location.left, location.right));
//                                result.setDistance(rangingHelper.getDis((int)location.centerY(), (int) location.centerX()));
//                                result.setDistance(16.f);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;
                    }
                });
    }

    private synchronized void runInDetectThread(final Runnable r) {
        if (detectHandler != null) {
            detectHandler.post(r);
        }
    }

    private synchronized void runInStereoThread(final Runnable r) {
        if (stereoHandler != null)
            stereoHandler.post(r);
    }

    private int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    private void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    private enum DetectorMode {
        TF_OD_API;
    }

    private void requestPermissions() {
        //权限列表
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,//GPS
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE,//读取网络信息状态
                Manifest.permission.ACCESS_WIFI_STATE,//获取当前wifi状态
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,//存储
                Manifest.permission.READ_PHONE_STATE,//读取手机信息

        };
        final int PERMISSION_REQUEST_CODE = 1;//定位权限获取code

        //Android6.0以上需要申请权限
        if (Build.VERSION.SDK_INT >= 23) {
            ArrayList<String> list = new ArrayList<>();
            for (String str : permissions) {
                //如果权限未获取
                if (ContextCompat.checkSelfPermission(this, str) != PackageManager.PERMISSION_GRANTED)
                    list.add(str);
            }
            if (!list.isEmpty()) {
                String[] requestList = new String[list.size()];
                ActivityCompat.requestPermissions(this, list.toArray(requestList), PERMISSION_REQUEST_CODE);
            }

        }
    }
}
