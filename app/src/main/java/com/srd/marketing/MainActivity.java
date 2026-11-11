package com.srd.marketing;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.*;
import androidx.core.content.FileProvider;
import java.io.File;
public class MainActivity extends Activity {
static final String U = "https://script.google.com/a/macros/srdlogistics.com/s/AKfycbwyqk1fY4bwEdbEFHU_zSJ_TfXgsInfv5qjxMpGAjPMwkpVS3YGnAQIjx4ql8h_jOcGcA/exec";
WebView web; ValueCallback<Uri[]> fcb; Uri cu; GeolocationPermissions.Callback gcb; String go;
@Override protected void onCreate(Bundle b) {
super.onCreate(b);
web = new WebView(this);
setContentView(web);
ask();
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
web.setWebViewClient(new WebViewClient() {
@Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
Uri u = r.getUrl();
String h = u.getHost() == null ? "" : u.getHost();
boolean w = "https".equals(u.getScheme()) || "http".equals(u.getScheme());
if (w && (h.endsWith("google.com") || h.endsWith("googleusercontent.com") || h.endsWith("gstatic.com"))) return false;
try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
return true;
}
@Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
if (r.isForMainFrame()) v.loadDataWithBaseURL(null, "<body style='font-family:sans-serif;text-align:center;padding:40px'><h2>No internet</h2><p><a href='" + U + "'>Retry</a></p>", "text/html", "utf-8", null);
}
});
web.setWebChromeClient(new WebChromeClient() {
@Override public void onGeolocationPermissionsShowPrompt(String o, GeolocationPermissions.Callback c) {
if (loc()) c.invoke(o, true, false); else { gcb = c; go = o; ask(); }
}
@Override public void onPermissionRequest(final PermissionRequest q) {
runOnUiThread(new Runnable() { public void run() { q.grant(q.getResources()); } });
}
@Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
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
else { g = Intent.createChooser(p.createIntent(), "Select"); if (cam != null) g.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam}); }
startActivityForResult(g, 2);
} catch (Exception e) { fcb = null; c.onReceiveValue(null); return false; }
return true;
}
});
web.loadUrl(U);
}
boolean loc() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
void ask() {
String[] all = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECORD_AUDIO};
java.util.ArrayList<String> n = new java.util.ArrayList<String>();
for (String p : all) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) n.add(p);
if (!n.isEmpty()) requestPermissions(n.toArray(new String[0]), 1);
}
@Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
super.onRequestPermissionsResult(c, p, r);
if (gcb != null) { gcb.invoke(go, loc(), false); gcb = null; }
}
@Override protected void onActivityResult(int q, int r, Intent d) {
if (q != 2) { super.onActivityResult(q, r, d); return; }
if (fcb == null) return;
Uri[] o = null;
if (r == Activity.RESULT_OK) {
if (d != null && d.getData() != null) o = new Uri[]{d.getData()};
else if (d != null && d.getClipData() != null && d.getClipData().getItemCount() > 0) { int n = d.getClipData().getItemCount(); o = new Uri[n]; for (int i = 0; i < n; i++) o[i] = d.getClipData().getItemAt(i).getUri(); }
else if (cu != null) o = new Uri[]{cu};
}
fcb.onReceiveValue(o);
fcb = null;
}
@Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
@Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
}
