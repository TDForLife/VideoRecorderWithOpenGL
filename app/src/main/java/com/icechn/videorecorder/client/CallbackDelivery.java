package com.icechn.videorecorder.client;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Created by lake on 16-4-11.
 */
public class CallbackDelivery {

    private static CallbackDelivery sInstance;
    private final Executor mCallbackPoster;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public static CallbackDelivery getInstance() {
        return sInstance == null ? sInstance = new CallbackDelivery() : sInstance;
    }

    private CallbackDelivery() {
        mCallbackPoster = new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                mHandler.post(command);
            }
        };
    }

    public void post(Runnable runnable) {
        mCallbackPoster.execute(runnable);
    }
}
