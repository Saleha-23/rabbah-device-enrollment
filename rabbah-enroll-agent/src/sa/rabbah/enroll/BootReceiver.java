package sa.rabbah.enroll;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!"enrolled".equals(Enroller.prefs(c).getString(Enroller.K_STATE, null))) EnrollJobService.schedule(c);
    }
}
