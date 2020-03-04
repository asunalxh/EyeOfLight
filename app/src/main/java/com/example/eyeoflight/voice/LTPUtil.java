package com.example.eyeoflight.voice;

import android.content.Context;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.eyeoflight.R;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LTPUtil {

    private Context context;

    public LTPUtil(Context context) {
        this.context = context;
    }


    public interface OnCallBackListener {
        void onCWSCallBack(List<String> words);
    }

    private OnCallBackListener onCallBackListener = null;

    public void setOnCallBackListener(OnCallBackListener onCallBackListener) {
        this.onCallBackListener = onCallBackListener;
    }

    //命名实体识别
    private static final String NER = "https://ltpapi.xfyun.cn/v1/ner";

    //词性标注
    private static final String POS = "https://ltpapi.xfyun.cn/v1/pos";

    //中文分词
    private static final String CWS = "https://ltpapi.xfyun.cn/v1/cws";

    //语义角色标注
    private static final String SRL = "https://ltpapi.xfyun.cn/v1/srl";

    public void requestCWS(final String str) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient();
                String app_id = context.getString(R.string.app_id);
                String currentTime = String.valueOf(System.currentTimeMillis());
                int len = currentTime.length();
                currentTime = currentTime.substring(0, len - 3);
                String param = "eyJ0eXBlIjoiZGVwZW5kZW50In0=";
                String APIKey = context.getString(R.string.APIkey);

                JSONObject object = new JSONObject();
                object.put("text", str);
                FormBody body = new FormBody.Builder()
                        .add("text", str)
                        .build();


                Request request = new Request.Builder()
                        .url(CWS)
                        .addHeader("X-Appid", app_id)
                        .addHeader("X-CurTime", currentTime)
                        .addHeader("X-Param", param)
                        .addHeader("X-CheckSum", MD5(APIKey + currentTime + param))
                        .post(body)
                        .build();
                Call call = client.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {

                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        String body = response.body().string();
                        List<String> list = getCWSWords(body);

                        if (onCallBackListener != null)
                            onCallBackListener.onCWSCallBack(list);

                    }
                });
            }
        }).start();


    }


    /**
     * 32位MD5加密
     *
     * @param content -- 待加密内容
     * @return
     */
    private String MD5(String content) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(content.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("NoSuchAlgorithmException", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UnsupportedEncodingException", e);
        }
        //对生成的16字节数组进行补零操作
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) {
                hex.append("0");
            }
            hex.append(Integer.toHexString(b & 0xFF));
        }
        return hex.toString();
    }


    public static List<String> getCWSWords(String jsonString) {
        List<String> list = new ArrayList<>();
        JSONObject json = JSONObject.parseObject(jsonString);
        JSONObject data = json.getJSONObject("data");
        JSONArray word = data.getJSONArray("word");
        for (int i = 0; i < word.size(); i++) {
            list.add(word.getString(i));
        }
        return list;
    }


}
