package com.fusionx.bus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import gnu.trove.map.hash.THashMap;

public class Bus {

    private final static Map<Class, List<Method>> sClassMethodCache = new THashMap<>();

    private static final int DEFAULT_PRIORITY = 100;

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
        if (mSubscribedObjectMap.get(registeredObject) != null) {
            throw new IllegalArgumentException();
        }

        List<Method> methods = sClassMethodCache.get(registeredObject.getClass());
        final boolean fromCache = methods != null;
        if (!fromCache) {
            methods = Arrays.asList(registeredObject.getClass().getDeclaredMethods());
        }
        final ArrayList<Method> cached = fromCache ? null : new ArrayList<Method>();

        final List<SubscribedMethod> subscribedMethods = new ArrayList<>();
        for (final Method method : methods) {
            final Subscribe subscribe = method.getAnnotation(Subscribe.class);
            if (subscribe == null) {
                continue;
            }

            final SubscribedMethod subscribedMethod = new SubscribedMethod(registeredObject,
                    method, subscribe, priority);
            addSubscribedMethodToMap(subscribedMethod);
            subscribedMethods.add(subscribedMethod);

            if (sticky) {
                final Object event = mStickyEventMap.get(subscribedMethod.getEventClass());
                if (event != null) {
                    postToSubscribers(event, Collections.singletonList(subscribedMethod));
                }
            }
            if (!fromCache) {
                cached.add(method);
            }
        }
        if (!fromCache) {
            cacheSubscribedMethods(registeredObject.getClass(), cached);
        }
        mSubscribedObjectMap.put(registeredObject, subscribedMethods);
    }

    private synchronized void postInternal(final Object event, final boolean sticky) {
        Class eventClass = event.getClass();
        while (eventClass != null) {
            postSingleEventClass(event, eventClass);
            eventClass = eventClass.getSuperclass();
        }

        if (sticky) {
            mStickyEventMap.put(event.getClass(), event);
        }
    }

    private void postSingleEventClass(final Object event, final Class eventClass) {
        final List<SubscribedMethod> methodList = mEventToSubscriberMap.get(eventClass);
        if (methodList != null) {
            postToSubscribers(event, methodList);
        }
    }

    private void postToSubscribers(final Object event, final List<SubscribedMethod> methodList) {
        final PostRunnable runnable = new PostRunnable(event, new EventBatch(methodList));
        runnable.run();
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
        mSubscribedObjectMap.remove(registedObject);
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

    private void cacheSubscribedMethods(final Class<?> registeredClass,
            final List<Method> methods) {
        sClassMethodCache.put(registeredClass, methods);
    }
}