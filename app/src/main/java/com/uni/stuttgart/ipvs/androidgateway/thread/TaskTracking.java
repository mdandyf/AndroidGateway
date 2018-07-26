package com.uni.stuttgart.ipvs.androidgateway.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskTracking extends ThreadPoolExecutor {
    private AtomicInteger mTaskCount = new AtomicInteger(0);
    protected AtomicInteger mThreadCount = new AtomicInteger(0);

    public TaskTracking(int corePoolSize, int maxPoolSize) {
        super(corePoolSize, maxPoolSize, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        mTaskCount.getAndIncrement();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        mTaskCount.getAndDecrement();
    }

    public int getNumberOfTasks() {
        return mTaskCount.get();
    }

    public int getNumberOfThreads() {return mThreadCount.get();}

    public int getAvailableProcessor() {
        return Runtime.getRuntime().availableProcessors();
    }

}
