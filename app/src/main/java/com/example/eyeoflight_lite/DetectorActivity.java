package com.example.eyeoflight_lite;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.widget.Toast;

import com.example.eyeoflight_lite.Fragment.FragmentOperation;
import com.example.eyeoflight_lite.customview.OverlayView;
import com.example.eyeoflight_lite.env.BorderedText;
import com.example.eyeoflight_lite.env.ImageUtils;
import com.example.eyeoflight_lite.tflite.Classifier;
import com.example.eyeoflight_lite.tflite.TFLiteObjectDetectionAPIModel;
import com.example.eyeoflight_lite.tracking.MultiBoxTracker;
import com.intel.realsense.librealsense.DepthFrame;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DetectorActivity extends CameraActivity {
    private static final String TAG = "DetectorActivity_Log";
//    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(width, height);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;


    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Integer sensorOrientation;

    private Classifier detector;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;


    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;    //绘制方框

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

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
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        cameraFragment.setDrawCallBack(new OverlayView.DrawCallback() {
            @Override
            public void drawCallback(Canvas canvas) {
                tracker.draw(canvas);
            }
        });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    private DepthFrame depthFrame;
    private Frame frame;

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;

        if(showBox)
            cameraFragment.postInvalidate();

        if (computingDetection) {
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);


        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        frame = fr.clone();
        fr.close();

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);    //物体识别结果

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

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        depthFrame = frame.as(Extension.DEPTH_FRAME);

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();

                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);

                                //添加距离信息
                                float depth = 0.f;
                                int x = 0, y = 0;
                                for (int i = (int) location.left; i <= location.right; i += 5) {
                                    if (i > 0 && i < width) {
                                        for (int j = (int) location.top; j <= location.bottom; j += 5) {
                                            if (j > 0 && j < height) {
                                                float temp = depthFrame.getDistance(x,j);
                                                if (depth == 0 || temp < depth) {
                                                    depth = temp;
                                                    x = i;
                                                    y = j;
                                                }
                                            }
                                        }
                                    }
                                }
                                result.setDepth(depth);
//                                Log.d(TAG,"depth: " + depth + "   title: " + result.toString() + " x:" + x + " y: "+y);
//                                Log.d(TAG,"Width: " + depthFrame.getWidth() + " Height: " + depthFrame.getHeight());
//                                Log.d(TAG,"CenterDepth: " + depthFrame.getDistance(depthFrame.getWidth()/2,depthFrame.getHeight()/2));

                                mappedRecognitions.add(result);
                                //Log.d(TAG,"Location: left:" + location.left + " right:" + location.right + " top:"+ location.top + " bottom:" + location.bottom);
                            }
                        }

                        frame.close();

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        if(showBox)
                            cameraFragment.postInvalidate();
                        computingDetection = false;

                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    private int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }
}