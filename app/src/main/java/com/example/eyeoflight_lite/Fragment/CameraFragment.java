package com.example.eyeoflight_lite.Fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eyeoflight_lite.R;
import com.example.eyeoflight_lite.customview.OverlayView;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.GLRsSurfaceView;

public class CameraFragment extends Fragment {

    private OverlayView overlayView;    //绘制方框的布局
    private GLRsSurfaceView glRsSurfaceView;    //显示画面的布局

    private OverlayView.DrawCallback drawCallback;

    private FragmentOperation operation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera,container,false);


        overlayView = view.findViewById(R.id.tracking_overlay);
        glRsSurfaceView = view.findViewById(R.id.glSurfaceView);
        glRsSurfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        glRsSurfaceView.close();
    }


    @Override
    public void onResume() {
        super.onResume();
        operation.ResumeOperation();
        overlayView.addCallback(drawCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        overlayView.addCallback(null);
    }

    public void postInvalidate(){
        overlayView.postInvalidate();
    }

    public void upload(Frame frame){
        glRsSurfaceView.upload(frame);
    }

    public void clearView(){
        glRsSurfaceView.clear();
    }

    public void setDrawCallBack(OverlayView.DrawCallback drawCallBack){
        this.drawCallback = drawCallBack;
    }

    public void setOperation(FragmentOperation operation) {
        this.operation = operation;
    }
}
