package com.srd.marketing;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class MainActivity extends Activity {
  static final String U = "https://script.google.com/a/macros/srdlogistics.com/s/AKfycbwyqk1fY4bwEdbEFHU_zSJ_TfXgsInfv5qjxMpGAjPMwkpVS3YGnAQIjx4ql8h_jOcGcA/exec";
  WebView web;
  FrameLayout root;
  View splash;
  SrdNative nat;
  ValueCallback<Uri[]> fcb;
  Uri cu;
  GeolocationPermissions.Callback gcb;
  String go;
  PermissionRequest pq;
  long lastBack;
  boolean docStart, splashOff;
  long pausedAt = 1;
  View lockView;
  boolean skipLock, lockBusy;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(0xFF0F6B5C);
    root = new FrameLayout(this);
    web = new WebView(this);
    root.addView(web, new FrameLayout.LayoutParams(-1, -1));
    splash = buildSplash();
    root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    nat = new SrdNative(this);
    web.addJavascriptInterface(nat, "SRDNative");
    ask();
    nat.start();
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setGeolocationEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(web, true);
    final String poly = readAsset("poly.js");
    docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
    if (docStart) WebViewCompat.addDocumentStartJavaScript(web, poly, new HashSet<String>(Arrays.asList("*")));
    web.setDownloadListener(new DownloadListener() {
      public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
        try {
          DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
          String ck = CookieManager.getInstance().getCookie(url);
          if (ck != null) r.addRequestHeader("cookie", ck);
          r.setMimeType(mime);
          r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
          r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime));
          ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
          Toast.makeText(MainActivity.this, "Downloading...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e2) { }
        }
      }
    });
    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        String h = u.getHost() == null ? "" : u.getHost();
        boolean w = "https".equals(u.getScheme()) || "http".equals(u.getScheme());
        if (w && (h.endsWith("google.com") || h.endsWith("googleusercontent.com") || h.endsWith("gstatic.com"))) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
        return true;
      }
      @Override
      public void onPageFinished(WebView v, String url) {
        if (!docStart) v.evaluateJavascript(poly, null);
      }
      @Override
      public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
        if (r.isForMainFrame()) {
          hideSplash();
          v.loadDataWithBaseURL(null, "<body style='font-family:sans-serif;text-align:center;padding:40px'><h2>No internet</h2><p>Check your network and try again.</p><p><a href='" + U + "' style='padding:12px 22px;background:#0f6b5c;color:#fff;border-radius:10px;text-decoration:none'>Retry</a></p>", "text/html", "utf-8", null);
        }
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView v, int p) {
        if (p >= 100) v.postDelayed(new Runnable() { public void run() { hideSplash(); } }, 1200);
      }
      @Override
      public void onGeolocationPermissionsShowPrompt(String o, GeolocationPermissions.Callback c) {
        if (nat.has()) c.invoke(o, true, false); else { gcb = c; go = o; ask(); }
      }
      @Override
      public void onPermissionRequest(final PermissionRequest q) {
        runOnUiThread(new Runnable() { public void run() {
          boolean au = false;
          for (String r : q.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) au = true;
          if (au && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pq = q;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
          } else q.grant(q.getResources());
        } });
      }
      @Override
      public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
        if (fcb != null) fcb.onReceiveValue(null);
        fcb = c;
        Intent cam = null;
        try {
          File d = new File(getCacheDir(), "photos");
          d.mkdirs();
          cu = FileProvider.getUriForFile(MainActivity.this, "com.srd.marketing.fileprovider", File.createTempFile("IMG_", ".jpg", d));
          cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
          cam.putExtra(MediaStore.EXTRA_OUTPUT, cu);
          cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) { cam = null; cu = null; }
        try {
          Intent g;
          if (p.isCaptureEnabled() && cam != null) g = cam;
          else {
            g = Intent.createChooser(p.createIntent(), "Select");
            if (cam != null) g.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
          }
          skipLock = true;
          startActivityForResult(g, 2);
        } catch (Exception e) { fcb = null; c.onReceiveValue(null); return false; }
        return true;
      }
    });
    web.postDelayed(new Runnable() { public void run() { hideSplash(); } }, 15000);
    web.loadUrl(U);
  }

    void checkLock() {
    if (!nat.isLock() || lockView != null || lockBusy) return;
    if (skipLock) { skipLock = false; return; }
    if (System.currentTimeMillis() - pausedAt < 300000) return;
    showLock();
  }

  void showLock() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setGravity(Gravity.CENTER);
    l.setBackgroundColor(0xFF16161A);
    l.setClickable(true);
    TextView t = new TextView(this);
    t.setText("SRD Marketing is locked");
    t.setTextColor(Color.WHITE);
    t.setTextSize(20);
    t.setGravity(Gravity.CENTER);
    t.setPadding(0, 0, 0, 32);
    l.addView(t);
    android.widget.Button b = new android.widget.Button(this);
    b.setText("Unlock");
    b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askUnlock(); } });
    l.addView(b);
    lockView = l;
    root.addView(l, new FrameLayout.LayoutParams(-1, -1));
    askUnlock();
  }

  void askUnlock() {
    if (lockBusy) return;
    try {
      KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
      Intent i = km.createConfirmDeviceCredentialIntent("SRD Marketing", "Unlock to continue");
      if (i == null) { unlock(); return; }
      lockBusy = true;
      startActivityForResult(i, 9);
    } catch (Exception e) { lockBusy = false; unlock(); }
  }

  void unlock() {
    if (lockView != null) { root.removeView(lockView); lockView = null; }
    pausedAt = System.currentTimeMillis();
  }

  View buildSplash() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setGravity(Gravity.CENTER);
    l.setBackgroundColor(0xFF16161A);
    l.setClickable(true);
    ImageView iv = new ImageView(this);
    iv.setImageResource(R.mipmap.ic_srd);
    int sz = (int) (130 * getResources().getDisplayMetrics().density);
    l.addView(iv, new LinearLayout.LayoutParams(sz, sz));
    TextView t = new TextView(this);
    t.setText("SRD Marketing");
    t.setTextColor(Color.WHITE);
    t.setTextSize(22);
    t.setGravity(Gravity.CENTER);
    t.setPadding(0, 36, 0, 24);
    l.addView(t);
    l.addView(new ProgressBar(this));
    return l;
  }

  void hideSplash() {
    if (splashOff) return;
    splashOff = true;
    splash.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() { public void run() { splash.setVisibility(View.GONE); } });
  }

  String readAsset(String n) {
    try {
      InputStream in = getAssets().open(n);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int r;
      while ((r = in.read(b)) > 0) o.write(b, 0, r);
      in.close();
      return o.toString("UTF-8");
    } catch (Exception e) { return ""; }
  }

  void ask() {
    String[] all = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECORD_AUDIO};
    ArrayList<String> n = new ArrayList<String>();
    for (String p : all) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) n.add(p);
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) n.add("android.permission.POST_NOTIFICATIONS");
    nat.asked = true;
    if (!n.isEmpty()) requestPermissions(n.toArray(new String[0]), 1);
  }

  @Override
  public void onRequestPermissionsResult(int c, String[] p, int[] r) {
    super.onRequestPermissionsResult(c, p, r);
    nat.start();
    if (gcb != null) { gcb.invoke(go, nat.has(), false); gcb = null; }
    if (pq != null) {
      try {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pq.grant(pq.getResources()); else pq.deny();
      } catch (Exception e) { }
      pq = null;
    }
  }

  @Override
  protected void onActivityResult(int q, int r, Intent d) {
    if (q == 9) { lockBusy = false; if (r == Activity.RESULT_OK) unlock(); return; }
    if (q != 2) { super.onActivityResult(q, r, d); return; }
    if (fcb == null) return;
    Uri[] o = null;
    if (r == Activity.RESULT_OK) {
      if (d != null && d.getData() != null) o = new Uri[]{d.getData()};
      else if (d != null && d.getClipData() != null && d.getClipData().getItemCount() > 0) {
        int n = d.getClipData().getItemCount();
        o = new Uri[n];
        for (int i = 0; i < n; i++) o[i] = d.getClipData().getItemAt(i).getUri();
      } else if (cu != null) o = new Uri[]{cu};
    }
    fcb.onReceiveValue(o);
    fcb = null;
  }

  @Override
  public void onBackPressed() {
    if (web != null && web.canGoBack()) { web.goBack(); return; }
    long n = System.currentTimeMillis();
    if (n - lastBack < 2000) super.onBackPressed();
    else { lastBack = n; Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show(); }
  }

  @Override
  protected void onResume() { super.onResume(); web.onResume(); nat.start(); checkLock(); }

  @Override
  protected void onPause() { super.onPause(); pausedAt = System.currentTimeMillis(); CookieManager.getInstance().flush(); nat.stop(); web.onPause(); }
}
