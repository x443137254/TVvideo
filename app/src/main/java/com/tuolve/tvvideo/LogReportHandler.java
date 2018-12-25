package com.tuolve.tvvideo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class LogReportHandler implements Thread.UncaughtExceptionHandler {

    @SuppressLint("StaticFieldLeak")
    private static LogReportHandler INSTANCE;
    private Context mContext;
    private Map<String, String> info;

    private LogReportHandler() {
    }

    static LogReportHandler getInstance() {
        if (INSTANCE == null) {
            synchronized (LogReportHandler.class) {
                if (INSTANCE == null) INSTANCE = new LogReportHandler();
            }
        }
        return INSTANCE;
    }

    void init(Context context) {
        mContext = context;
        info = new HashMap<>();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

//        new Thread() {
//            @Override
//            public void run() {
//                Looper.prepare();
//                showDialog();
//                Looper.loop();
//            }
//        }.start();

        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (pi != null) {
            String ver = "版本：" + pi.versionCode + "/" + (pi.versionName == null ? "null" : pi.versionName);
            info.put("post[ver]",ver);
        }
        info.put("post[system]","SDK " + android.os.Build.VERSION.SDK);
        info.put("post[phone]",android.os.Build.MODEL);
        info.put("post[addtime]", SimpleDateFormat.getDateTimeInstance().format(System.currentTimeMillis()));

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = sDateFormat.format(new java.util.Date());
        sb.append("\r\n").append(date).append("\n");

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.flush();
        printWriter.close();
        String result = writer.toString();
        sb.append(result);

        String content = sb.toString();
        info.put("post[content]",content);

        uploadInfo();

        Toast.makeText(mContext, info.toString(), Toast.LENGTH_SHORT).show();

        //参数1表示异常退出，正常退出参数为0
        SystemClock.sleep(3000);
        System.exit(0);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void showDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setMessage(info.toString())
                .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void uploadInfo() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30,TimeUnit.SECONDS)
                .build();
        RequestBody body = new FormBody.Builder()
                .add("params",info.toString())
                .add("token","fe2d5e06a6a97520a00a62708ce18fc1b1c1129d")
                .build();
        Request request = new Request.Builder()
                .url("http://test.tuolve.com/app_video/web/api.php/Log/upload")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d("aaaaaaa", "onResponse: " + response.body().string());

            }
        });
    }
}