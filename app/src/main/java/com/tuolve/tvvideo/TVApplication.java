package com.tuolve.tvvideo;

import android.app.Application;

/**
 * Created by Administrator on 2018/12/12
 */

public class TVApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogReportHandler.getInstance().init(getApplicationContext());
    }
}
