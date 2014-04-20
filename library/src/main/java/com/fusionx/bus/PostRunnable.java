package com.fusionx.bus;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class PostRunnable implements Runnable {

    private final EventBatch mEventBatch;

    private final Object mEvent;

    private final Looper mPostingLooper;

    public PostRunnable(final Object event, final EventBatch batch, final Looper looper) {
        mEvent = event;
        mEventBatch = batch;
        mPostingLooper = looper;
    }

    @Override
    public void run() {
        boolean cancelled = false;
        SubscribedMethod subscribedMethod = mEventBatch.getNextMethod();

        while (subscribedMethod != null && !cancelled) {
            final SubscribedMethod finalSubscribedMethod = subscribedMethod;
            final FutureTask<Object> futureTask = new FutureTask<>(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    try {
                        return finalSubscribedMethod.getMethod()
                                .invoke(finalSubscribedMethod.getObject(), mEvent);
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            final Handler handler = SubscribedMethod.getHandler(finalSubscribedMethod,
                    mPostingLooper);
            handler.post(futureTask);
            try {
                cancelled = subscribedMethod.isCancellable() && (boolean) futureTask.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            subscribedMethod = mEventBatch.getNextMethod();
        }
    }
}