package de.grinder.android_fi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Autostarter extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d("Workload", "BOOT_COMPLETED received; starting Workload");

            final Intent activity = new Intent(context, Workload.class);
            activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activity);
        }
    }

}
