package com.example.eyeoflight_lite.env;

import android.util.Base64;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OCRTool {
    private static final String TAG = "OCRTool_Log";

    private static final String APPID = "23646950";
    private static final String APIKey = "WGuG5MMYueYu6toO50rkOzB0";
    private static final String SecretKey = "lMnlPE73wbEzSvqD9DxqbpM0GjPNxsx8";
    private static String token;


    public static void getToken(OnCallBack onCallBack) {
        Log.d(TAG, "response: " + "in getToken");

        HttpUrl url = HttpUrl.parse("https://aip.baidubce.com/oauth/2.0/token").newBuilder()
                .addQueryParameter("grant_type", "client_credentials")
                .addQueryParameter("client_id", APIKey)
                .addQueryParameter("client_secret", SecretKey)
                .build();

        OkHttpClient client = new OkHttpClient();

        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (onCallBack != null) onCallBack.onFailure();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                JSONObject object = JSON.parseObject(response.body().string());
                token = object.getString("access_token");
                if (onCallBack != null) onCallBack.onResponse(response);
            }
        });

    }

    private static void general_basic(byte[] bytes, OnCallBack onCallBack) {
        HttpUrl url = HttpUrl.parse("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic").newBuilder()
                .addQueryParameter("access_token", token)
                .build();
        OkHttpClient client = new OkHttpClient();

        String image = base64(bytes);
        RequestBody body = new FormBody.Builder()
                .add("image", image)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (onCallBack != null) onCallBack.onFailure();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (onCallBack != null) onCallBack.onResponse(response);
            }
        });

    }

    private static String base64(byte[] bytes) {
        Log.d(TAG, "bytes String: " + bytes);
        String string = Base64.encodeToString(bytes, Base64.DEFAULT);
        return string;
    }

    public static void getWord(byte[] bytes, OnGetWords onGetWords) {
        getToken(new OnCallBack() {
            @Override
            public void onFailure() {

            }

            @Override
            public void onResponse(@NotNull Response response) {
                general_basic(bytes, new OnCallBack() {
                    @Override
                    public void onFailure() {

                    }

                    @Override
                    public void onResponse(@NotNull Response response) {
                        try {
                            JSONObject object = JSON.parseObject(response.body().string());
                            JSONArray words_result = object.getJSONArray("words_result");
                            StringBuffer stringBuffer = new StringBuffer();
                            for (int i = 0; i < words_result.size(); i++) {
                                JSONObject j = words_result.getJSONObject(i);
                                stringBuffer.append(j.getString("words"));
                            }
                            onGetWords.onGetWords(stringBuffer.toString());

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        });
    }

    public interface OnCallBack {
        void onFailure();

        void onResponse(@NotNull Response response);
    }

    public interface OnGetWords {
        void onGetWords(String words);
    }

}
