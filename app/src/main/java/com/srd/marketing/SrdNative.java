package com.srd.marketing;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

public class SrdNative implements LocationListener {
  final Activity a;
  final LocationManager lm;
  Location best;
  boolean running, asked;
  long lastDlg;

  SrdNative(Activity a) {
    this.a = a;
    lm = (LocationManager) a.getSystemService(Context.LOCATION_SERVICE);
  }

  boolean has() {
    return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        || a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  boolean on(String p) {
    try { return lm.isProviderEnabled(p); } catch (Exception e) { return false; }
  }

  void consider(Location l) {
    if (l == null) return;
    if (best == null || l.getAccuracy() <= best.getAccuracy() || l.getTime() - best.getTime() > 20000) best = l;
  }

  void start() {
    if (!has()) return;
    try {
      String[] ps = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
      for (String p : ps) {
        if (!lm.getAllProviders().contains(p)) continue;
        if (!running) lm.requestLocationUpdates(p, 3000, 0, this, Looper.getMainLooper());
        consider(lm.getLastKnownLocation(p));
      }
      running = true;
    } catch (SecurityException e) { }
  }

  void stop() {
    try { lm.removeUpdates(this); } catch (Exception e) { }
    running = false;
  }

  @Override public void onLocationChanged(Location l) { consider(l); }
  @Override public void onProviderEnabled(String p) { }
  @Override public void onProviderDisabled(String p) { }
  @Override public void onStatusChanged(String p, int s, Bundle b) { }

  @JavascriptInterface
  public String getLoc() {
    if (!has()) return "{\"ok\":false,\"reason\":\"NO_PERMISSION\"}";
    if (!running) a.runOnUiThread(new Runnable() { public void run() { start(); } });
    if (!on(LocationManager.GPS_PROVIDER) && !on(LocationManager.NETWORK_PROVIDER)) return "{\"ok\":false,\"reason\":\"GPS_OFF\"}";
    Location l = best;
    if (l == null || System.currentTimeMillis() - l.getTime() > 600000) return "{\"ok\":false,\"reason\":\"NO_FIX\"}";
    return String.format(Locale.US, "{\"ok\":true,\"lat\":%.7f,\"lng\":%.7f,\"acc\":%.1f,\"t\":%d}", l.getLatitude(), l.getLongitude(), l.getAccuracy(), l.getTime());
  }

  @JavascriptInterface
  public void ensure() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (!has()) {
        if (!asked || a.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
          asked = true;
          a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
          dialog("Location permission needed", "Please allow Location for SRD Marketing in App Settings.", new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
        }
        return;
      }
      if (!on(LocationManager.GPS_PROVIDER) && !on(LocationManager.NETWORK_PROVIDER)) {
        dialog("Location is OFF", "Please turn ON phone Location (GPS) to save visits and calls.", new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
      }
    } });
  }

  void dialog(String t, String m, final Intent go) {
    if (System.currentTimeMillis() - lastDlg < 60000) return;
    lastDlg = System.currentTimeMillis();
    new AlertDialog.Builder(a).setTitle(t).setMessage(m)
      .setPositiveButton("Open Settings", new android.content.DialogInterface.OnClickListener() {
        public void onClick(android.content.DialogInterface d, int w) { try { a.startActivity(go); } catch (Exception e) { } }
      }).setNegativeButton("Cancel", null).show();
  }

  void toast(final String s) {
    a.runOnUiThread(new Runnable() { public void run() { Toast.makeText(a, s, Toast.LENGTH_LONG).show(); } });
  }

  @JavascriptInterface
  public void saveFile(String b64, String name, String mime) {
    try {
      byte[] d = Base64.decode(b64, Base64.DEFAULT);
      name = (name == null || name.isEmpty()) ? "download" : name.replaceAll("[^A-Za-z0-9._ -]", "_");
      if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
      Uri uri;
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, mime);
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        uri = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        OutputStream o = a.getContentResolver().openOutputStream(uri);
        o.write(d);
        o.close();
      } else {
        File dir = a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        dir.mkdirs();
        File f = new File(dir, name);
        FileOutputStream o = new FileOutputStream(f);
        o.write(d);
        o.close();
        uri = FileProvider.getUriForFile(a, "com.srd.marketing.fileprovider", f);
      }
      toast("Saved in Downloads: " + name);
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setDataAndType(uri, mime);
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      try { a.startActivity(i); } catch (ActivityNotFoundException e) { }
    } catch (Exception e) {
      toast("Download failed");
    }
  }

  @JavascriptInterface
  public void shareWa(final String text, String phone) {
    final String ph0 = phone == null ? "" : phone.replaceAll("[^0-9]", "");
    final String ph = ph0.length() == 10 ? "91" + ph0 : ph0;
    final String tx = text == null ? "" : text;
    a.runOnUiThread(new Runnable() { public void run() {
      try {
        Intent i;
        if (!ph.isEmpty()) {
          i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + ph + "?text=" + Uri.encode(tx)));
        } else {
          i = new Intent(Intent.ACTION_SEND);
          i.setType("text/plain");
          i.putExtra(Intent.EXTRA_TEXT, tx);
          i.setPackage("com.whatsapp");
        }
        a.startActivity(i);
      } catch (Exception e) {
        try {
          Intent s = new Intent(Intent.ACTION_SEND);
          s.setType("text/plain");
          s.putExtra(Intent.EXTRA_TEXT, tx);
          a.startActivity(Intent.createChooser(s, "Share"));
        } catch (Exception e2) { }
      }
    } });
  }

  boolean micAsked;

  @JavascriptInterface
  public boolean hasMic() {
    return a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
  }

  @JavascriptInterface
  public void ensureMic() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (hasMic()) return;
      if (!micAsked || a.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
        micAsked = true;
        a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
      } else {
        lastDlg = 0;
        dialog("Microphone permission needed", "Please allow Microphone for SRD Marketing in App Settings to record voice notes.", new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
      }
    } });
  }

  boolean updShown;

  boolean canInstall() {
    return Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls();
  }

  @JavascriptInterface
  public void showUpdate(final String ver, final String url) {
    a.runOnUiThread(new Runnable() { public void run() {
      if (updShown) return;
      updShown = true;
      new AlertDialog.Builder(a).setTitle("Update available")
        .setMessage("A new version of SRD Marketing (v" + ver + ") is available. Update now to get the latest features and fixes.")
        .setPositiveButton("Update", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface d, int w) { startUpdate(url); }
        })
        .setNegativeButton("Not now", null)
        .show();
    } });
  }

  void startUpdate(final String url) {
    if (!canInstall()) {
      updShown = false;
      toast("Allow 'Install unknown apps' for SRD Marketing, then come back to update.");
      try { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); } catch (Exception e) { }
      return;
    }
    final android.app.ProgressDialog pd = new android.app.ProgressDialog(a);
    pd.setTitle("Updating SRD Marketing");
    pd.setMessage("Downloading update...");
    pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
    pd.setCancelable(true);
    pd.setMax(100);
    pd.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
      public void onCancel(android.content.DialogInterface d) { updShown = false; }
    });
    pd.show();
    try {
      File dir = a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
      dir.mkdirs();
      final File f = new File(dir, "srd-update.apk");
      if (f.exists()) f.delete();
      final android.app.DownloadManager dm = (android.app.DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
      android.app.DownloadManager.Request rq = new android.app.DownloadManager.Request(Uri.parse(url));
      rq.setTitle("SRD Marketing update");
      rq.setMimeType("application/vnd.android.package-archive");
      rq.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE);
      rq.setDestinationUri(Uri.fromFile(f));
      final long id = dm.enqueue(rq);
      final android.os.Handler h = new android.os.Handler(Looper.getMainLooper());
      h.post(new Runnable() { public void run() {
        if (!pd.isShowing()) return;
        android.database.Cursor c = dm.query(new android.app.DownloadManager.Query().setFilterById(id));
        int st = -1;
        int reason = 0;
        long got = 0, tot = 0;
        if (c != null) {
          if (c.moveToFirst()) {
            st = c.getInt(c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS));
            got = c.getLong(c.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            tot = c.getLong(c.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            reason = c.getInt(c.getColumnIndex(android.app.DownloadManager.COLUMN_REASON));
          }
          c.close();
        }
        if (st == android.app.DownloadManager.STATUS_SUCCESSFUL) {
          pd.dismiss();
          updShown = false;
          try {
            File d2 = new File(a.getCacheDir(), "photos");
            d2.mkdirs();
            File f2 = new File(d2, "update.apk");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            FileOutputStream out = new FileOutputStream(f2);
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            out.close();
            in.close();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(FileProvider.getUriForFile(a, "com.srd.marketing.fileprovider", f2), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
          } catch (Exception ex) { toast("Could not open installer"); }
          return;
        }
        if (st == android.app.DownloadManager.STATUS_FAILED) {
          pd.dismiss();
          updShown = false;
          toast("Update download failed (code " + reason + "). Please try again.");
          return;
        }
        if (tot > 0) pd.setProgress((int) (got * 100 / tot));
        h.postDelayed(this, 500);
      } });
    } catch (Exception e) {
      pd.dismiss();
      updShown = false;
      toast("Update download failed. Please try again later.");
    }
  }

  @JavascriptInterface
  public String getVersion() {
    try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
  }

  @JavascriptInterface
  public void shareFiles(final String text, final String json) {
    final String tx = text == null ? "" : text;
    a.runOnUiThread(new Runnable() { public void run() {
      try {
        org.json.JSONArray arr = new org.json.JSONArray(json);
        File d = new File(a.getCacheDir(), "photos");
        d.mkdirs();
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<Uri>();
        String mime = null;
        for (int k = 0; k < arr.length(); k++) {
          org.json.JSONObject o = arr.getJSONObject(k);
          String nm = o.optString("n", "file").replaceAll("[^A-Za-z0-9._-]", "_");
          String ty = o.optString("t", "application/octet-stream");
          byte[] b = Base64.decode(o.getString("d"), Base64.DEFAULT);
          File f = new File(d, System.currentTimeMillis() + "_" + k + "_" + nm);
          FileOutputStream fo = new FileOutputStream(f);
          fo.write(b);
          fo.close();
          uris.add(FileProvider.getUriForFile(a, "com.srd.marketing.fileprovider", f));
          if (mime == null) mime = ty; else if (!mime.equals(ty)) mime = "*/*";
        }
        try {
          android.content.ClipboardManager cb = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
          cb.setPrimaryClip(android.content.ClipData.newPlainText("SRD details", tx));
        } catch (Exception ce) { }
        if (uris.isEmpty()) { shareWa(tx, ""); return; }
        Intent i = new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TEXT, tx);
        if (uris.size() > 1) i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        else i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        toast("Details copied. If caption is missing, paste it in WhatsApp.");
        try {
          i.setPackage("com.whatsapp");
          a.startActivity(i);
        } catch (Exception e1) {
          i.setPackage(null);
          a.startActivity(Intent.createChooser(i, "Share"));
        }
      } catch (Exception e) {
        toast("Share failed");
      }
    } });
  }

  android.content.SharedPreferences sp() { return a.getSharedPreferences("srd", Context.MODE_PRIVATE); }

  @JavascriptInterface
  public void saveSession(String t) {
    sp().edit().putString("sess", t == null ? "" : t).apply();
  }

  @JavascriptInterface
  public String getSession() {
    return sp().getString("sess", "");
  }

  @JavascriptInterface
  public boolean canLock() {
    android.app.KeyguardManager k = (android.app.KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
    return k != null && k.isDeviceSecure();
  }

  @JavascriptInterface
  public boolean isLock() {
    return sp().getBoolean("lock", false);
  }

  @JavascriptInterface
  public void setLock(boolean on) {
    if (on && !canLock()) {
      toast("First set a screen lock (PIN, pattern or fingerprint) in phone Settings.");
      return;
    }
    sp().edit().putBoolean("lock", on).apply();
    toast(on ? "App lock is ON" : "App lock is OFF");
  }

  android.app.PendingIntent fuIntent(int id, String t, String x) {
    Intent i = new Intent(a, FollowupReceiver.class);
    i.putExtra("t", t);
    i.putExtra("x", x);
    i.putExtra("n", id);
    return android.app.PendingIntent.getBroadcast(a, id, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
  }

  @JavascriptInterface
  public void scheduleFollowups(String json) {
    try {
      android.app.AlarmManager am = (android.app.AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
      int old = sp().getInt("fuN", 0);
      for (int k = 0; k < old; k++) am.cancel(fuIntent(7000 + k, "", ""));
      org.json.JSONArray arr = new org.json.JSONArray(json);
      int n = 0;
      for (int k = 0; k < arr.length() && n < 40; k++) {
        org.json.JSONObject o = arr.getJSONObject(k);
        long at = o.getLong("at");
        if (at <= System.currentTimeMillis()) continue;
        am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, fuIntent(7000 + n, o.optString("t", "Follow-up"), o.optString("x", "")));
        n++;
      }
      sp().edit().putInt("fuN", n).apply();
    } catch (Exception e) { }
  }
}
