package com.uni.stuttgart.ipvs.androidgateway.thread;

import java.util.concurrent.ThreadFactory;

public class ProcessPriority implements ThreadFactory {

    private final int threadPriority;
    private Thread thread;

    public ProcessPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    @Override
    public Thread newThread(Runnable r) {
        thread = new Thread(r);
        thread.setPriority(threadPriority);
        return thread;
    }

    public void interruptThread() {
        thread.interrupt();
    }

}