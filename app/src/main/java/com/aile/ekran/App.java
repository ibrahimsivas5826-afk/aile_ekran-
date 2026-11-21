package com.aile.ekran;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
    }
}
