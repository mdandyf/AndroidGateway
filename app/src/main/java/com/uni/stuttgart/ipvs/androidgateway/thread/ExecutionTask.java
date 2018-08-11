package com.uni.stuttgart.ipvs.androidgateway.thread;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutionTask<T> extends TaskTracking {
    private ExecutorService executor;
    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;
    private Future<T> futureReturn;
    private List<Thread> listThreads;

    private EExecutionType executionType;
    private int N = this.getAvailableProcessor();
    private int corePoolSize = N;
    private int maxPoolSize = N * 2;

    /**
     * Constructor
     * @param corePoolSize
     * @param maxPoolSize
     */
    public ExecutionTask(int corePoolSize, int maxPoolSize) {
        super(corePoolSize, maxPoolSize);
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        setExecutionType(EExecutionType.SINGLE_THREAD_POOL);
        this.listThreads = new ArrayList<>();
    }

    public ExecutionTask() {
        super(1,2);
        this.listThreads = new ArrayList<>();
    }

    public void setExecutionType(EExecutionType executionType) {
        this.executionType = executionType;
        setExecutor();
    }

    /**
     * Section Task Execution using Executor Service
     */

    public ExecutorService getExecutor() {
        return executor;
    }

    public ScheduledFuture<?> getFuture() {
        return future;
    }

    public Future<T> submitRunnable(Runnable inputRunnable) {
        futureReturn = (Future<T>) executor.submit(inputRunnable);
        return futureReturn;
    }

    public Future<T> submitCallable(Callable<T> callable) {
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
        try {
            executor.shutdownNow();
            executor.awaitTermination(1000, TimeUnit.MICROSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Section Scheduled ThreadPool Task Execution
     */

    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
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

    /**
     * Section Thread based Task Execution
     */

    public Thread executeRunnableInThread(Runnable inputRunnable, String threadName, int priority) {
        Thread thread = new Thread(inputRunnable);
        thread.setName(threadName);
        thread.setPriority(priority);
        thread.start();
        mThreadCount.getAndIncrement();
        listThreads.add(thread);
        return thread;
    }

    public void interruptThread(Thread thread) { thread.interrupt();mThreadCount.getAndDecrement(); }

    public long getThreadId(Thread thread) {return thread.getId();}

    public void setThreadJoin(Thread thread) throws InterruptedException {thread.join();}

    public void setThreadPriority(Thread thread, int priority) {thread.setPriority(priority);}

    public int getThreadPriority(Thread thread) {return thread.getPriority();}

    public List<Thread> getListThreads() {return listThreads;}

    /**
     * Other Method routines
     * @return
     */
    private void setExecutor() {
        if(this.executionType.equals(EExecutionType.SINGLE_THREAD_POOL)) {
            this.executor = Executors.newSingleThreadExecutor();
        } else {
            this.executor = Executors.newFixedThreadPool(this.maxPoolSize);
        }
    }

    /**
     * Section Task Execution using Job Scheduler
     */

    @TargetApi(Build.VERSION_CODES.M)
    public void scheduleJob(Context context, Class<?> className, long initDelay, long repeat) {
        ComponentName serviceComponent = new ComponentName(context, className);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);
        builder.setMinimumLatency(initDelay); // wait at least
        builder.setOverrideDeadline(repeat); // maximum delay
        //builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED); // require unmetered network
        builder.setRequiresDeviceIdle(true); // device should be idle
        builder.setRequiresCharging(false); // we don't care if the device is charging or not
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        jobScheduler.schedule(builder.build());
    }

}
