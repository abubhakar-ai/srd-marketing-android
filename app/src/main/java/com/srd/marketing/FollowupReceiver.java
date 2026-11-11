package com.srd.marketing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class FollowupReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    try {
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(new NotificationChannel("followup", "Follow-up reminders", NotificationManager.IMPORTANCE_HIGH));
      }
      Intent open = new Intent(c, MainActivity.class);
      open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "followup") : new Notification.Builder(c);
      b.setSmallIcon(R.mipmap.ic_srd).setContentTitle(i.getStringExtra("t")).setContentText(i.getStringExtra("x")).setAutoCancel(true).setContentIntent(pi);
      nm.notify(i.getIntExtra("n", 1), b.build());
    } catch (Exception e) { }
  }
}
