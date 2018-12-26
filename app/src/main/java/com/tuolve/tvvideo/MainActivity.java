package com.tuolve.tvvideo;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener {

    private OkHttpClient client;
    private Request request;
    private String path;
    private final String HOST = "http://test.tuolve.com/app_video/web";
    private int isDownloading = 0;
    private VideoView videoPlayer;
    private ProgressBar progressBar;
    private File[] files;
    private int index = 0;
    private List<String> urlList;
    private long[] time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();

        videoPlayer = findViewById(R.id.video_player);
        progressBar = findViewById(R.id.progress_bar);
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.HOURS)
                .build();
        request = new Request.Builder().get().url(HOST + "/api.php/Cms/lists?&visit=public&t=test").build();
        path = Environment.getExternalStorageDirectory() + "/TV_videos";
//        if (!Environment.getExternalStorageState().equals("android.os.Environment.MEDIA_MOUNTED")){
//            Toast.makeText(this, "SD卡不存在", Toast.LENGTH_SHORT).show();
//            return;
//        }
        File file = new File(path);
        if (!file.exists() || file.isFile()) {
            if (!file.mkdirs()) {
                Toast.makeText(this, "视频创建失败", Toast.LENGTH_SHORT).show();
            }
        }

        files = file.listFiles();
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                checkUpdate();
            }
        };
        timer.schedule(task, 0, 300000);
        videoPlayer.setOnCompletionListener(this);

        if (files == null || files.length == 0) return;
        time = new long[files.length];
        for (int i = 0; i < files.length; i++) {
            time[i] = getDuration(i);
        }
        synTime();

    }

    private void checkUpdate() {
        if (isDownloading > 0) return;
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (isDownloading > 0) return;
                ResponseBody body = response.body();
                if (body == null) return;
                String s = body.string();
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (jsonObject == null) return;
                int status = jsonObject.optInt("status");
                if (status == 10001) {
                    JSONArray array = jsonObject.optJSONArray("lists");
                    if (array == null) return;
                    urlList = new ArrayList<>();
                    JSONObject item;
                    for (int i = 0; i < array.length(); i++) {
                        item = array.optJSONObject(i);
                        if (item == null) return;
                        urlList.add(item.optString("video"));
                    }
                    updateVideo();
                }
            }
        });
    }

    private void updateVideo() {
        //本地跟服务器视频列表对比，多的删除
        if (files != null) {
            List<Integer> deleteList = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                String s = file.getName();
                boolean needDelete = true;
                for (int j = 0; j < urlList.size(); j++) {
                    if (s.equals(getMD5(HOST + "/" + urlList.get(i)) + ".mp4")) {
                        needDelete = false;
                        break;
                    }
                }
                if (needDelete) {
                    deleteList.add(i);  //记录列表里哪个是已经被删掉了的，在下一步一起删除
                }
            }

            for (int i = 0; i < deleteList.size(); i++) {
                if (videoPlayer.isPlaying()) videoPlayer.pause();
                boolean deleteResult = files[deleteList.get(i)].delete();
                if (!deleteResult) {
                    Toast.makeText(this, "视频删除失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
        //本地跟服务器视频列表对比，缺少的下载
        File dir = new File(path);
        files = dir.listFiles();
        for (int i = 0; i < urlList.size(); i++) {
            String s = getMD5(HOST + "/" + urlList.get(i)) + ".mp4";
            boolean needDownload = true;
            if (files != null) {
                for (File file : files) {
                    if (s.equals(file.getName())) {
                        needDownload = false;
                        break;
                    }
                }
            }
            if (needDownload) {
                downloadVideo(urlList.get(i));
            }
        }
        if (isDownloading == 0 && !videoPlayer.isPlaying()) {
            synPlay();
        }
    }


    private String getMD5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes());
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void downloadVideo(String url) {
        Log.d("ad", "downloadVideo: -------------------------------------");
        isDownloading++;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
        final String fileUrl = HOST + "/" + url;
        Request request = new Request.Builder().url(fileUrl).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Toast.makeText(MainActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                isDownloading--;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) {
                    Toast.makeText(MainActivity.this, "null", Toast.LENGTH_SHORT).show();
                    return;
                }
                InputStream inputStream = body.byteStream();
                FileOutputStream fileOutputStream = new FileOutputStream(path + "/" + getMD5(fileUrl) + ".mp4");
                byte[] buff = new byte[1024 * 1024];
                int len = inputStream.read(buff);
                while (len > 0) {
                    fileOutputStream.write(buff, 0, len);
                    len = inputStream.read(buff);
                }
                fileOutputStream.flush();
                inputStream.close();
                fileOutputStream.close();
                isDownloading--;
                if (isDownloading == 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            synPlay();
                        }
                    });
                }
            }
        });
    }

    private void synPlay() {
        final int size = urlList.size();
        if (size == 0) return;
        files = new File[size];
        time = new long[size];
        for (int i = 0; i < size; i++) {
            files[i] = new File(path + "/" + getMD5(HOST + "/" + urlList.get(i)) + ".mp4");
            time[i] = getDuration(i);
        }
        synTime();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        index++;
        if (index == files.length) {
            index = 0;
        }
        videoPlayer.setVideoPath(files[index].getAbsolutePath());
        videoPlayer.start();
    }

    private void synTime() {
        long max = 0;
        for (long l : time) {
            max += l;
        }
        long offTime = System.currentTimeMillis() % max;
        index = 0;
        long off = getOffset(offTime);

        if (files != null) {
            videoPlayer.setVideoPath(files[index].getAbsolutePath());
            videoPlayer.seekTo((int) off);
            videoPlayer.start();
        }
    }

    private long getOffset(long t) {
        if (t < time[index]) {
            return t;
        } else {
            index++;
            return getOffset(t - time[index - 1]);
        }
    }

    private long getDuration(int i) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(files[i].getPath());
            mediaPlayer.prepare();
            return mediaPlayer.getDuration();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
