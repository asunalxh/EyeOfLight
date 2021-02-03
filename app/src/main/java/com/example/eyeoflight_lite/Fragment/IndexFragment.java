package com.example.eyeoflight_lite.Fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.eyeoflight_lite.CameraActivity;
import com.example.eyeoflight_lite.R;

public class IndexFragment extends Fragment {

    private CardView cameraView,navigationView,readView;
    private CameraActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_index,container,false);

        cameraView = view.findViewById(R.id.camera_btn);
        navigationView = view.findViewById(R.id.navigation_btn);
        readView = view.findViewById(R.id.read_btn);

        activity = (CameraActivity) getActivity();

        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.switchToCameraFragment();
            }
        });

        navigationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.switchToMap();
            }
        });

        readView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.switchToNavigation();
            }
        });
        return view;
    }
}
