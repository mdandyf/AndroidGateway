package com.uni.stuttgart.ipvs.androidgateway.thread;

import android.util.Log;

import java.util.concurrent.ThreadFactory;

public class ThreadTrackingPriority implements ThreadFactory {
    private static final String TAG = "Thread Factory";
    private static int count = 0;
    private final int threadPriority;
    private Thread thread;

    public ThreadTrackingPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    @Override
    public Thread newThread(Runnable r) {
        thread = new Thread(r);
        thread.setPriority(threadPriority);
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "Thread = " + t.getName() + ", error = " +
                        e.getMessage());
            }
        });
        return thread;
    }

    public void interruptThread() {
        thread.interrupt();
    }

    public long getThreadId() {return thread.getId();}

    public void setThreadJoin() throws InterruptedException {thread.join();}

    public void setThreadPriority(int priority) {thread.setPriority(priority);}

    public int getThreadPriority() {return thread.getPriority();}

}