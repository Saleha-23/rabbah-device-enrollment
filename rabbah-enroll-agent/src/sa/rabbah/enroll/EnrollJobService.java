package sa.rabbah.enroll;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Runs the enroll call; re-runs every 15 minutes until it gets a 200 (or a 401). */
public class EnrollJobService extends JobService {
    static final int JOB_ID = 5521;

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, EnrollJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    static void cancel(Context c) {
        ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
    }

    @Override public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            Enroller.Result r = Enroller.enroll(getApplicationContext());
            if (r != Enroller.Result.RETRY) cancel(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
