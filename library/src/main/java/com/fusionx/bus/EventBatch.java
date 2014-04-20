package com.fusionx.bus;

import java.util.Collection;
import java.util.PriorityQueue;

/**
 * Represents a batch of events withing which priority makes the difference
 */
public class EventBatch {

    private final PriorityQueue<SubscribedMethod> mPriorityQueue;

    public EventBatch(final Collection<SubscribedMethod> methods) {
        mPriorityQueue = new PriorityQueue<>(methods);
    }

    public SubscribedMethod getNextMethod() {
        return mPriorityQueue.poll();
    }
}