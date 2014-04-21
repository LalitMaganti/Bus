package com.fusionx.bus;

import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class PostRunnable implements Runnable {

    private final EventBatch mEventBatch;

    private final Object mEvent;

    public PostRunnable(final Object event, final EventBatch batch) {
        mEvent = event;
        mEventBatch = batch;
    }

    @Override
    public void run() {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        SubscribedMethod subscribedMethod = mEventBatch.getNextMethod();
        final Looper looper = Looper.myLooper();

        while (subscribedMethod != null && !cancelled.get()) {
            final SubscribedMethod subs = subscribedMethod;
            final FutureTask<Void> runnable = new FutureTask<>(new Runnable() {
                @Override
                public void run() {
                    Object returnValue;
                    try {
                        returnValue = subs.getMethod().invoke(subs.getObject(), mEvent);
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                        return;
                    }
                    cancelled.set(subs.isCancellable() && (boolean) returnValue);
                }
            }, null);
            if (subs.getThreadType() == ThreadType.POSTING ||
                    subs.getThreadType() == ThreadType.MAIN && looper == Looper.getMainLooper()) {
                runnable.run();
            } else {
                subscribedMethod.getHandler().post(runnable);
                if (subs.isCancellable()) {
                    try {
                        runnable.get();
                    } catch (final InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
            subscribedMethod = mEventBatch.getNextMethod();
        }
    }
}