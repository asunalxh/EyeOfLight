package com.example.eyeoflight_lite.Fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.model.Poi;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.model.NaviPoi;
import com.example.eyeoflight_lite.R;

public class NavigationFragment extends Fragment {
    private static final String TAG = "NavigationFragment_Log";
    private AMapNaviView aMapNaviView;
    private AMapNavi aMapNavi;

    private String cityCode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_navigation,container,false);
        aMapNaviView = view.findViewById(R.id.navigation_view);
        aMapNavi = AMapNavi.getInstance(getContext());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        location();
    }

    private void location(){
        AMapLocationClient aMapLocationClient = new AMapLocationClient(getContext());
        AMapLocationListener aMapLocationListener = new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                cityCode = aMapLocation.getCityCode();
            }
        };

        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setOnceLocation(true);
        aMapLocationClient.setLocationOption(option);
        aMapLocationClient.setLocationListener(aMapLocationListener);

        aMapLocationClient.stopLocation();
        aMapLocationClient.startLocation();

    }



}
