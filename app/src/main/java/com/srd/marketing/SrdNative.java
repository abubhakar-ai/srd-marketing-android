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
    pd.setCancelable(false);
    pd.setMax(100);
    pd.show();
    new Thread(new Runnable() { public void run() {
      try {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int len = c.getContentLength();
        File d = new File(a.getCacheDir(), "photos");
        d.mkdirs();
        final File f = new File(d, "update.apk");
        java.io.InputStream in = c.getInputStream();
        FileOutputStream o = new FileOutputStream(f);
        byte[] b = new byte[16384];
        int n, got = 0, last = -1;
        while ((n = in.read(b)) > 0) {
          o.write(b, 0, n);
          got += n;
          if (len > 0) {
            final int p = (int) (got * 100L / len);
            if (p != last) { last = p; a.runOnUiThread(new Runnable() { public void run() { pd.setProgress(p); } }); }
          }
        }
        o.close();
        in.close();
        if (f.length() < 100000) throw new Exception("bad file");
        a.runOnUiThread(new Runnable() { public void run() {
          pd.dismiss();
          try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(FileProvider.getUriForFile(a, "com.srd.marketing.fileprovider", f), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
          } catch (Exception e) { toast("Could not open installer"); }
        } });
      } catch (Exception e) {
        a.runOnUiThread(new Runnable() { public void run() { pd.dismiss(); updShown = false; toast("Update download failed. Please try again later."); } });
      }
    } }).start();
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
}
