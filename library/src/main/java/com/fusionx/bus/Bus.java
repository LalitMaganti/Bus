package com.fusionx.bus;

import android.os.Looper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gnu.trove.map.hash.THashMap;

public class Bus {

    private final static Map<Class, Method[]> sClassMethodCache = new THashMap<>();

    private static final int DEFAULT_PRIORITY = 100;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private final Map<Object, List<SubscribedMethod>> mSubscribedObjectMap = new THashMap<>();

    private final Map<Class, List<SubscribedMethod>> mEventToSubscriberMap = new THashMap<>();

    private final Map<Class, Object> mStickyEventMap =
            Collections.synchronizedMap(new THashMap<Class, Object>());

    public void register(final Object registeredObject) {
        registerInternal(registeredObject, DEFAULT_PRIORITY, false);
    }

    public void register(final Object registeredObject, final int priority) {
        registerInternal(registeredObject, priority, false);
    }

    public void registerSticky(final Object registeredObject) {
        registerInternal(registeredObject, DEFAULT_PRIORITY, true);
    }

    public void registerSticky(final Object registeredObject, final int priority) {
        registerInternal(registeredObject, priority, true);
    }

    public void post(final Object event) {
        postInternal(event, false);
    }

    public void postSticky(final Object event) {
        postInternal(event, true);
    }

    public void unregister(final Object registedObject) {
        unregisterInternal(registedObject);
    }

    public <T> T getStickyEvent(final Class<T> eventClass) {
        return (T) mStickyEventMap.get(eventClass);
    }

    public <T> T removeStickyEvent(final Class<T> eventClass) {
        return (T) mStickyEventMap.remove(eventClass);
    }

    public void clearStickyEvents() {
        mStickyEventMap.clear();
    }

    private synchronized void registerInternal(final Object registeredObject, final int priority,
            final boolean sticky) {
        List<SubscribedMethod> methodList = mSubscribedObjectMap.get(registeredObject);
        if (methodList != null) {
            throw new IllegalArgumentException();
        }

        final Method[] methods = getMethods(registeredObject.getClass());
        methodList = new ArrayList<>();

        final Looper looper = Looper.myLooper();
        for (final Method method : methods) {
            final SubscribedMethod subscribedMethod = new SubscribedMethod(registeredObject,
                    method, priority);
            addSubscribedMethodToMap(subscribedMethod);
            methodList.add(subscribedMethod);

            if (sticky) {
                final Object event = mStickyEventMap.get(subscribedMethod.getEventClass());
                if (event != null) {
                    postSingleEventClass(event, subscribedMethod.getEventClass(), looper);
                }
            }
        }
        mSubscribedObjectMap.put(registeredObject, methodList);
    }

    private synchronized void postInternal(final Object event, final boolean sticky) {
        final Looper looper = Looper.myLooper();
        Class eventClass = event.getClass();
        while (eventClass != null) {
            postSingleEventClass(event, eventClass, looper);
            eventClass = eventClass.getSuperclass();
        }

        if (sticky) {
            mStickyEventMap.put(event.getClass(), event);
        }
    }

    private void postSingleEventClass(final Object event, final Class eventClass,
            final Looper postingLooper) {
        final List<SubscribedMethod> methodList = mEventToSubscriberMap.get(eventClass);
        if (methodList != null) {
            mExecutorService.submit(new PostRunnable(event, new EventBatch(methodList),
                    postingLooper));
        }
    }

    private synchronized void unregisterInternal(Object registedObject) {
        final List<SubscribedMethod> methodList = mSubscribedObjectMap.get(registedObject);
        if (methodList == null) {
            throw new IllegalArgumentException();
        }
        for (final SubscribedMethod subscribedMethod : methodList) {
            final List<SubscribedMethod> list = mEventToSubscriberMap.get(subscribedMethod
                    .getEventClass());
            list.remove(subscribedMethod);
            if (list.isEmpty()) {
                mEventToSubscriberMap.remove(subscribedMethod.getEventClass());
            }
        }
    }

    private void addSubscribedMethodToMap(final SubscribedMethod subscribedMethod) {
        List<SubscribedMethod> methodList = mEventToSubscriberMap.get(subscribedMethod
                .getEventClass());
        if (methodList == null) {
            methodList = new LinkedList<>();
            mEventToSubscriberMap.put(subscribedMethod.getEventClass(), methodList);
        }
        methodList.add(subscribedMethod);
    }

    private Method[] getMethods(final Class<?> registeredClass) {
        Method[] methods = sClassMethodCache.get(registeredClass);
        if (methods == null) {
            methods = registeredClass.getDeclaredMethods();
            sClassMethodCache.put(registeredClass, methods);
        }
        return methods;
    }
}