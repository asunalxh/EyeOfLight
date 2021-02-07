package com.example.eyeoflight_lite.Fragment;

import android.os.Bundle;
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
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.enums.TravelStrategy;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviPoi;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.eyeoflight_lite.R;

import java.util.List;

public class NavigationFragment extends Fragment {

    private static final String TAG = "NavigationFragment_Log";
    private AMapNaviView aMapNaviView;
    private AMapNavi aMapNavi;

    private String cityCode;
    private String destinationId;
    private String destinationName = "万达广场";

    private NavigationProgress navigationProgress = new NavigationProgress() {
        @Override
        public void onLocationChange(AMapLocation aMapLocation) {
            cityCode = aMapLocation.getCityCode();
            search(destinationName,10,1);
        }

        @Override
        public void onSearchSuccess(PoiResult poiResult) {
            List<PoiItem> list = poiResult.getPois();
            if(list.size() > 0){
                destinationId = list.get(0).getPoiId();
                startNavigation();
            }
        }

        @Override
        public void onSearchFailure() {

        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_navigation,container,false);
        aMapNaviView = view.findViewById(R.id.navigation_view);
        aMapNaviView.onCreate(savedInstanceState);

        aMapNavi = AMapNavi.getInstance(getContext());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        aMapNaviView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        aMapNaviView.onDestroy();
    }

    private void location(){
        AMapLocationClient aMapLocationClient = new AMapLocationClient(getContext());
        AMapLocationListener aMapLocationListener = new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                navigationProgress.onLocationChange(aMapLocation);
            }
        };

        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setOnceLocation(true);
        aMapLocationClient.setLocationOption(option);
        aMapLocationClient.setLocationListener(aMapLocationListener);

        aMapLocationClient.stopLocation();
        aMapLocationClient.startLocation();

    }

    private void search(String place,int pageSize,int pageNum){
        PoiSearch.Query query = new PoiSearch.Query(place,"",cityCode);
        query.setPageSize(pageSize);
        query.setPageNum(pageNum);

        PoiSearch poiSearch = new PoiSearch(getContext(),query);
        poiSearch.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
            @Override
            public void onPoiSearched(PoiResult poiResult, int i) {
                //查询成功
                if(i == 1000){
                    navigationProgress.onSearchSuccess(poiResult);
                }else{
                    navigationProgress.onSearchFailure();
                }
            }

            @Override
            public void onPoiItemSearched(PoiItem poiItem, int i) {

            }
        });
        poiSearch.searchPOIAsyn();
    }

    private void startNavigation(){
        NaviPoi destination = new NaviPoi(destinationName,null,destinationId);
        aMapNavi.calculateWalkRoute(null,destination, TravelStrategy.SINGLE);
        aMapNavi.setUseInnerVoice(true,true);
        aMapNavi.addAMapNaviListener(new AMapNaviListener() {
            @Override
            public void onInitNaviFailure() {

            }

            @Override
            public void onInitNaviSuccess() {

            }

            @Override
            public void onStartNavi(int i) {

            }

            @Override
            public void onTrafficStatusUpdate() {

            }

            @Override
            public void onLocationChange(AMapNaviLocation aMapNaviLocation) {

            }

            @Override
            public void onGetNavigationText(int i, String s) {

            }

            @Override
            public void onGetNavigationText(String s) {

            }

            @Override
            public void onEndEmulatorNavi() {

            }

            @Override
            public void onArriveDestination() {

            }

            @Override
            public void onCalculateRouteFailure(int i) {

            }

            @Override
            public void onReCalculateRouteForYaw() {

            }

            @Override
            public void onReCalculateRouteForTrafficJam() {

            }

            @Override
            public void onArrivedWayPoint(int i) {

            }

            @Override
            public void onGpsOpenStatus(boolean b) {

            }

            @Override
            public void onNaviInfoUpdate(NaviInfo naviInfo) {

            }

            @Override
            public void updateCameraInfo(AMapNaviCameraInfo[] aMapNaviCameraInfos) {

            }

            @Override
            public void updateIntervalCameraInfo(AMapNaviCameraInfo aMapNaviCameraInfo, AMapNaviCameraInfo aMapNaviCameraInfo1, int i) {

            }

            @Override
            public void onServiceAreaUpdate(AMapServiceAreaInfo[] aMapServiceAreaInfos) {

            }

            @Override
            public void showCross(AMapNaviCross aMapNaviCross) {

            }

            @Override
            public void hideCross() {

            }

            @Override
            public void showModeCross(AMapModelCross aMapModelCross) {

            }

            @Override
            public void hideModeCross() {

            }

            @Override
            public void showLaneInfo(AMapLaneInfo[] aMapLaneInfos, byte[] bytes, byte[] bytes1) {

            }

            @Override
            public void showLaneInfo(AMapLaneInfo aMapLaneInfo) {

            }

            @Override
            public void hideLaneInfo() {

            }

            @Override
            public void onCalculateRouteSuccess(int[] ints) {

            }

            @Override
            public void notifyParallelRoad(int i) {

            }

            @Override
            public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] aMapNaviTrafficFacilityInfos) {

            }

            @Override
            public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo aMapNaviTrafficFacilityInfo) {

            }

            @Override
            public void updateAimlessModeStatistics(AimLessModeStat aimLessModeStat) {

            }

            @Override
            public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo aimLessModeCongestionInfo) {

            }

            @Override
            public void onPlayRing(int i) {

            }

            @Override
            public void onCalculateRouteSuccess(AMapCalcRouteResult aMapCalcRouteResult) {
                aMapNavi.startNavi(NaviType.GPS);
            }

            @Override
            public void onCalculateRouteFailure(AMapCalcRouteResult aMapCalcRouteResult) {

            }

            @Override
            public void onNaviRouteNotify(AMapNaviRouteNotifyData aMapNaviRouteNotifyData) {

            }

            @Override
            public void onGpsSignalWeak(boolean b) {

            }
        });
    }

    private void stopNavigation(){
        aMapNavi.stopNavi();
    }

    public void startNaviTo(String place){
        destinationName = place;
        location();
    }

    private interface NavigationProgress{
        void onLocationChange(AMapLocation aMapLocation);
        void onSearchSuccess(PoiResult poiResult);
        void onSearchFailure();
    }

}
