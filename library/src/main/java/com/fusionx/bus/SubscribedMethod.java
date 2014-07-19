package com.fusionx.bus;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.lang.reflect.Method;

public class SubscribedMethod implements Comparable<SubscribedMethod> {

    private static final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private final Method mMethod;

    private final Subscribe mSubscribe;

    private final Class mEventClass;

    private final int mPriority;

    private final Object mObject;

    public SubscribedMethod(final Object object, final Method method,
            final Subscribe subscribe, final int priority) {
        mObject = object;
        mMethod = method;
        mSubscribe = subscribe;

        final Class[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            // throw exception
        }
        // More checks needed

        mEventClass = parameterTypes[0];
        mPriority = priority;
    }

    public Method getMethod() {
        return mMethod;
    }

    public Subscribe getSubscribe() {
        return mSubscribe;
    }

    public Class getEventClass() {
        return mEventClass;
    }

    @Override
    public int compareTo(@NonNull final SubscribedMethod another) {
        if (mPriority > another.mPriority) {
            return -1;
        } else if (mPriority < another.mPriority) {
            return 1;
        }
        return 0;
    }

    public Object getObject() {
        return mObject;
    }

    public boolean isCancellable() {
        return mSubscribe.cancellable();
    }

    public ThreadType getThreadType() {
        return mSubscribe.threadType();
    }

    public Handler getHandler() {
        switch (mSubscribe.threadType()) {
            case MAIN:
                return mMainThreadHandler;
        }
        return null;
    }
}