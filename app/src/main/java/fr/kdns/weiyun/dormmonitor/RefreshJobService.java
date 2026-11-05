package fr.kdns.weiyun.dormmonitor;

import android.app.job.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Network work owned by Android, rather than an unowned receiver thread. */
public class RefreshJobService extends JobService {
    private static final int IMMEDIATE = 27106, PERIODIC = 27107;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer, FutureTask<Void>> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, JobParameters> active = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static JobScheduler scheduler(Context c) {
        return (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }
    private static JobInfo.Builder builder(Context c, int id) {
        return new JobInfo.Builder(id, new ComponentName(c, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
    }
    static boolean enqueue(Context c) {
        JobScheduler s = scheduler(c);
        if (s.getPendingJob(IMMEDIATE) == null) {
            if (Build.VERSION.SDK_INT >= 31
                    && s.schedule(builder(c, IMMEDIATE).setExpedited(true).build()) == JobScheduler.RESULT_SUCCESS) return true;
            return s.schedule(builder(c, IMMEDIATE).setMinimumLatency(0).build()) == JobScheduler.RESULT_SUCCESS;
        }
        return true;
    }
    static void ensurePeriodic(Context c) {
        JobScheduler s = scheduler(c);
        if (s.getPendingJob(PERIODIC) == null) {
            s.schedule(builder(c, PERIODIC).setPeriodic(15 * 60_000L).setPersisted(true).build());
        }
    }
    static void cancel(Context c) {
        scheduler(c).cancel(IMMEDIATE);
        scheduler(c).cancel(PERIODIC);
    }
    @Override public boolean onStartJob(JobParameters params) {
        active.put(params.getJobId(), params);
        FutureTask<Void> task = new FutureTask<>(() -> {
            boolean success = false;
            try {
                success = DormWidgetProvider.refreshAll(getApplicationContext());
            } finally {
                final boolean retry = !success;
                main.post(() -> {
                    if (active.remove(params.getJobId(), params)) {
                        running.remove(params.getJobId());
                        jobFinished(params, retry);
                    }
                });
            }
            return null;
        });
        running.put(params.getJobId(), task);
        executor.execute(task);
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        active.remove(params.getJobId(), params);
        FutureTask<Void> task = running.remove(params.getJobId());
        if (task != null) task.cancel(true);
        DormWidgetProvider.recordRefreshState(this, "等待系统重试");
        return true;
    }
    @Override public void onDestroy() {
        for (FutureTask<Void> task : running.values()) task.cancel(true);
        running.clear();
        active.clear();
        executor.shutdownNow();
        super.onDestroy();
    }
}
