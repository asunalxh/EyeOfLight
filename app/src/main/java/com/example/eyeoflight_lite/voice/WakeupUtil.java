package com.example.eyeoflight_lite.voice;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.example.eyeoflight_lite.R;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.util.ResourceUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class WakeupUtil {

    public interface WakeUpUtilListener {
        void onResult();
        void onVolumeChange(int volume);
    }

    private static WakeUpUtilListener wakeUpUtilListener=null;

    public static void setWakeUpUtilListener(WakeUpUtilListener mWakeUpUtilListener){
        wakeUpUtilListener = mWakeUpUtilListener;
    }

    private static Context context;

    private static String TAG = "ivw";
    // 语音唤醒对象
    private static VoiceWakeuper mIvw;
    // 唤醒结果内容
    private static String resultString;

    // 设置门限值 ： 门限值越低越容易被唤醒
    private final static int curThresh = 450;
    private static String keep_alive = "0";
    private static String ivwNetMode = "0";

    public static void init(Context mContext) {
        context = mContext;

        // 初始化唤醒对象
        mIvw = VoiceWakeuper.createWakeuper(context, null);

        setUpVoiceWakeuper();
    }

    public static void startListen(){
        mIvw.startListening(mWakeuperListener);
    }

    public static void destory(){
        mIvw.destroy();
    }

    public static void stopListen(){
        mIvw.stopListening();
    }

    private static void setUpVoiceWakeuper(){
        mIvw = VoiceWakeuper.getWakeuper();
        if(mIvw != null) {
            resultString = "";

            // 清空参数
            mIvw.setParameter(SpeechConstant.PARAMS, null);
            // 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
            mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:"+ curThresh);
            // 设置唤醒模式
            mIvw.setParameter(SpeechConstant.IVW_SST, "wakeup");
            // 设置持续进行唤醒
            mIvw.setParameter(SpeechConstant.KEEP_ALIVE, keep_alive);
            // 设置闭环优化网络模式
            mIvw.setParameter(SpeechConstant.IVW_NET_MODE, ivwNetMode);
            // 设置唤醒资源路径
            mIvw.setParameter(SpeechConstant.IVW_RES_PATH, getResource());
            // 设置唤醒录音保存路径，保存最近一分钟的音频
            mIvw.setParameter( SpeechConstant.IVW_AUDIO_PATH, Environment.getExternalStorageDirectory().getPath()+"/msc/ivw.wav" );
            mIvw.setParameter( SpeechConstant.AUDIO_FORMAT, "wav" );
            // 如有需要，设置 NOTIFY_RECORD_DATA 以实时通过 onEvent 返回录音音频流字节
            //mIvw.setParameter( SpeechConstant.NOTIFY_RECORD_DATA, "1" );
            // 启动唤醒
            /*	mIvw.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");*/

//            mIvw.startListening(mWakeuperListener);
				/*File file = new File(Environment.getExternalStorageDirectory().getPath() + "/msc/ivw1.wav");
				byte[] byetsFromFile = getByetsFromFile(file);
				mIvw.writeAudio(byetsFromFile,0,byetsFromFile.length);*/
            //	mIvw.stopListening();
        }
    }




    private static WakeuperListener mWakeuperListener = new WakeuperListener() {

        @Override
        public void onResult(final WakeuperResult result) {
            if(wakeUpUtilListener!=null)
                wakeUpUtilListener.onResult();
        }

        @Override
        public void onError(SpeechError error) {
        }

        @Override
        public void onBeginOfSpeech() {
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            switch( eventType ){
                // EVENT_RECORD_DATA 事件仅在 NOTIFY_RECORD_DATA 参数值为 真 时返回
                case SpeechEvent.EVENT_RECORD_DATA:
                    final byte[] audio = obj.getByteArray( SpeechEvent.KEY_EVENT_RECORD_DATA );
                    Log.i( TAG, "ivw audio length: "+audio.length );
                    break;
            }
        }

        @Override
        public void onVolumeChanged(int volume) {
            if(wakeUpUtilListener!=null)
                wakeUpUtilListener.onVolumeChange(volume);
        }
    };


    private static String getResource() {
        final String resPath = ResourceUtil.generateResourcePath(context, ResourceUtil.RESOURCE_TYPE.assets, "ivw/"+context.getString(R.string.app_id)+".jet");
        return resPath;
    }



    private static byte[] getByetsFromFile(File file) {
        byte[] buffer = null;
        ByteArrayOutputStream bos = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            bos = new ByteArrayOutputStream();
            buffer = new byte[35244];
            int length = 0;
            while ((length = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }
            fis.close();
            bos.close();
        }catch (Exception e){

        }
        return bos.toByteArray();//字节流转换为一个 byte数组

    }






}
