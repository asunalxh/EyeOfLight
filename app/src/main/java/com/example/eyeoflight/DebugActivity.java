package com.example.eyeoflight;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.IOException;

public class DebugActivity extends AppCompatActivity {

    private RangingHelper rangingHelper;
    private ImageView imageView1, imageView2, imageView3;
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        imageView1 = findViewById(R.id.image1);
        button = findViewById(R.id.button);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    rangingHelper = new RangingHelper();
                    try {
                        Mat left = Utils.loadResource(DebugActivity.this, R.drawable.testleft2);
                        Mat right = Utils.loadResource(DebugActivity.this, R.drawable.testright2);
                        rangingHelper.setImage(left, right);
                        button.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                rangingHelper.calculate();
                                imageView1.setImageBitmap(rangingHelper.getDisparityBitmap());
                            }
                        });

                        imageView1.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                switch (event.getAction()){
                                    case MotionEvent.ACTION_DOWN:
                                        int x = (int) event.getX();
                                        int y = (int) event.getY();
                                        Log.d("test:::",String.valueOf(x)+' '+y+' '+rangingHelper.getDis(y,x));
                                        break;
                                }
                                return true;
                            }
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

}

