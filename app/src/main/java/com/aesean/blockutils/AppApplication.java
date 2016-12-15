package com.aesean.blockutils;

import android.app.Application;

/**
 * AppApplication
 *
 * @author xl
 * @version V1.0
 * @since 16/8/15
 */
public class AppApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        BlockUtils.getInstance().install();
    }
}
