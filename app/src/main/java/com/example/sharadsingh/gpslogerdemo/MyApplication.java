package com.example.sharadsingh.gpslogerdemo;

import android.content.Context;
import android.os.Bundle;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;


/**
 * Created by mansha on 30/10/15.
 */
public class MyApplication extends MultiDexApplication {
    public static final String TAG = MyApplication.class.getSimpleName();
    private static MyApplication mInstance;
    public static Context mContext;
    public static synchronized MyApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mContext = this;

    }


    public static Context getContext() {
        return mContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
