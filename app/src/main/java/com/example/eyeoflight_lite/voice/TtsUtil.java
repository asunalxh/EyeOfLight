package com.example.eyeoflight_lite.voice;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.eyeoflight_lite.R;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;

public class TtsUtil {

    public interface TtsUtilListener {
        void onSpeakCompleted();
    }


    private static String TAG = "Tts";
    // 语音合成对象
    private static SpeechSynthesizer mTts;

    // 默认云端发音人
    private static String voicerCloud = "xiaoyan";
    // 默认本地发音人
    private static String voicerLocal = "xiaoyan";

    private static String voicerXtts = "xiaoyan";
    // 云端发音人列表
    private static String[] cloudVoicersEntries;
    private static String[] cloudVoicersValue;

    // 本地发音人列表
    private static String[] localVoicersEntries;
    private static String[] localVoicersValue;

    // 增强版发音人列表
    private static String[] xttsVoicersEntries;
    private static String[] xttsVoicersValue;

    //缓冲进度
    private static int mPercentForBuffering = 0;
    //播放进度
    private static int mPercentForPlaying = 0;

    // 引擎类型
    private static String mEngineType = SpeechConstant.TYPE_CLOUD;

    private static Toast mToast;

    private static Context context;

    public static void init(Context mContext) {
        context = mContext;

        mToast = new Toast(context);

        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(context, mTtsInitListener);

        // 云端发音人名称列表
        cloudVoicersEntries = context.getResources().getStringArray(R.array.voicer_cloud_entries);
        cloudVoicersValue = context.getResources().getStringArray(R.array.voicer_cloud_values);

        // 本地发音人名称列表
        localVoicersEntries = context.getResources().getStringArray(R.array.voicer_local_entries);
        localVoicersValue = context.getResources().getStringArray(R.array.voicer_local_values);

        // 增强版发音人名称列表
        xttsVoicersEntries = context.getResources().getStringArray(R.array.voicer_xtts_entries);
        xttsVoicersValue = context.getResources().getStringArray(R.array.voicer_xtts_values);

        mToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
    }


    public static void speak(final String text, final TtsUtilListener ttsUtilListener) {
        // 开始合成
        // 收到onCompleted 回调时，合成结束、生成合成音频
        // 合成的音频格式：只支持pcm格式

        // 设置参数
        setParam();
        Log.d(TAG, "准备点击： " + System.currentTimeMillis());
        int code = mTts.startSpeaking(text, new SynthesizerListener() {
            @Override
            public void onSpeakBegin() {
                //showTip("开始播放");
                Log.d(TAG, "开始播放：" + System.currentTimeMillis());
            }

            @Override
            public void onSpeakPaused() {
//            showTip("暂停播放");
            }

            @Override
            public void onSpeakResumed() {
//            showTip("继续播放");
            }

            @Override
            public void onBufferProgress(int percent, int beginPos, int endPos,
                                         String info) {
                // 合成进度
                mPercentForBuffering = percent;
//            showTip(String.format(context.getString(R.string.tts_toast_format),
//                    mPercentForBuffering, mPercentForPlaying));
            }

            @Override
            public void onSpeakProgress(int percent, int beginPos, int endPos) {
                // 播放进度
                mPercentForPlaying = percent;
//            showTip(String.format(context.getString(R.string.tts_toast_format),
//                    mPercentForBuffering, mPercentForPlaying));
            }

            @Override
            public void onCompleted(SpeechError error) {
                if (error == null) {
//                showTip("播放完成");
                    if(ttsUtilListener!=null)
                        ttsUtilListener.onSpeakCompleted();
                } else if (error != null) {
//                showTip(error.getPlainDescription(true));
                }
            }

            @Override
            public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
                // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
                // 若使用本地能力，会话id为null
                if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                    String sid = obj.getString(SpeechEvent.KEY_EVENT_AUDIO_URL);
                    Log.d(TAG, "session id =" + sid);
                }

                //实时音频流输出参考
			/*if (SpeechEvent.EVENT_TTS_BUFFER == eventType) {
				byte[] buf = obj.getByteArray(SpeechEvent.KEY_EVENT_TTS_BUFFER);
				Log.e("MscSpeechLog", "buf is =" + buf);
			}*/
            }
        });
//			/**
//			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//			*/
//			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//			int code = mTts.synthesizeToUri(text, path, mTtsListener);

        if (code != ErrorCode.SUCCESS) {
//            showTip("语音合成失败,错误码: " + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
        }


    }


    private static int selectedNumCloud = 0;
    private static int selectedNumLocal = 0;

    /**
     * 初始化监听。
     */
    private static InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
//                showTip("初始化失败,错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");

            } else {
                // 初始化成功，之后可以调用startSpeaking方法
                // 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
                // 正确的做法是将onCreate中的startSpeaking调用移至这里
            }
        }
    };


    private static void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    /**
     * 参数设置
     */
    private static void setParam() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        //设置合成
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            //设置使用云端引擎
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            //设置发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerCloud);

        } else if (mEngineType.equals(SpeechConstant.TYPE_LOCAL)) {
            //设置使用本地引擎
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            //设置发音人资源路径
            mTts.setParameter(ResourceUtil.TTS_RES_PATH, getResourcePath());
            //设置发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerLocal);
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_XTTS);
            //设置发音人资源路径
            mTts.setParameter(ResourceUtil.TTS_RES_PATH, getResourcePath());
            //设置发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerXtts);
        }
        //mTts.setParameter(SpeechConstant.TTS_DATA_NOTIFY,"1");//支持实时音频流抛出，仅在synthesizeToUri条件下支持
        //设置合成语速
        mTts.setParameter(SpeechConstant.SPEED, "50");
        //设置合成音调
        mTts.setParameter(SpeechConstant.PITCH, "50");
        //设置合成音量
        mTts.setParameter(SpeechConstant.VOLUME, "50");
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
        //	mTts.setParameter(SpeechConstant.STREAM_TYPE, AudioManager.STREAM_MUSIC+"");

        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");

        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");


    }

    //获取发音人资源路径
    private static String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        String type = "tts";
        if (mEngineType.equals(SpeechConstant.TYPE_XTTS)) {
            type = "xtts";
        }
        //合成通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(context, ResourceUtil.RESOURCE_TYPE.assets, type + "/common.jet"));
        tempBuffer.append(";");
        //发音人资源
        if (mEngineType.equals(SpeechConstant.TYPE_XTTS)) {
            tempBuffer.append(ResourceUtil.generateResourcePath(context, ResourceUtil.RESOURCE_TYPE.assets, type + "/" + voicerXtts + ".jet"));
        } else {
            tempBuffer.append(ResourceUtil.generateResourcePath(context, ResourceUtil.RESOURCE_TYPE.assets, type + "/" + voicerLocal + ".jet"));
        }

        return tempBuffer.toString();
    }


}
