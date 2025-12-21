package io.securenode.nodekit.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Self-heal: reschedule daily sync after reboot.
 */
public class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    SyncScheduler.scheduleDailySync(context);
  }
}





