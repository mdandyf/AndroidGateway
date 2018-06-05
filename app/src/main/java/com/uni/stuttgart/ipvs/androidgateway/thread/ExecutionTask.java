package com.uni.stuttgart.ipvs.androidgateway.thread;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutionTask<T> extends TaskTrackingThreadPool{
    private ExecutorService executor;
    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;
    private int N = this.getAvailableProcessor();
    private int corePoolSize = N;
    private int maxPoolSize = N * 2;

    public ExecutionTask(int corePoolSize, int maxPoolSize) {
        super(corePoolSize, maxPoolSize);
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }

    public ScheduledFuture<?> getFuture() {
        return future;
    }

    public Thread executeRunnableInThread(Runnable inputRunnable, String threadName, int priority) {
        ThreadTrackingPriority threadTrackingPriority = new ThreadTrackingPriority(priority);
        Thread thread = threadTrackingPriority.newThread(inputRunnable);
        thread.setName(threadName);
        thread.start();
        return thread;
    }

    public void submitRunnableSingleThread(Runnable inputRunnable) {
        this.executor = Executors.newSingleThreadExecutor();
        executor.submit(inputRunnable);
    }

    public Future<T> submitCallableSingleThread(Callable<T> callable) {
        this.executor = Executors.newSingleThreadExecutor();
        return executor.submit(callable);
    }

    public void submitRunnableMultiThread(Runnable inputRunnable) {
        this.executor = Executors.newFixedThreadPool(this.maxPoolSize);
        executor.submit(inputRunnable);
    }

    public Future<T> submitCallableMultiThread(Callable<T> callable) {
        this.executor = Executors.newFixedThreadPool(this.maxPoolSize);
        return executor.submit(callable);
    }

    public List<Future<T>> executeMultipleCallableTasks(List<Callable<T>> tasks) {
        try {
            return executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Future<T>> executeMultipleCallableTasks(List<Callable<T>> tasks, long timeout, TimeUnit timeUnit) {
        try {
            return executor.invokeAll(tasks, timeout, timeUnit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void stopExecutorPools() {
        executor.shutdown();
    }

    public void terminateExecutorPools() {
        executor.shutdownNow();
    }

    public ScheduledThreadPoolExecutor scheduleWithThreadPoolExecutor(Runnable runnable, long initDelay, long repeat, TimeUnit timeUnit) {
        scheduler = new ScheduledThreadPoolExecutor(this.corePoolSize);
        future = scheduler.scheduleAtFixedRate(runnable, initDelay, repeat, timeUnit);
        return scheduler;
    }

    public void stopScheduler() {
        future.cancel(true);
        if(scheduler != null) scheduler.shutdown();
    }

    public void terminateScheduler() {
        future.cancel(true);
        if(scheduler != null) scheduler.shutdownNow();
    }

    public int getAvailableProcessor() {
        return Runtime.getRuntime().availableProcessors();
    }

}
