package com.perchance.agentswarm;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
public class TermuxBridge {
  private final Context ctx;
  public TermuxBridge(Context c) { ctx = c; }
  @JavascriptInterface
  public String exec(String cmd, int timeoutMs) {
    int to = timeoutMs <= 0 ? 15000 : Math.min(timeoutMs, 300000);
    try {
      Process p = new ProcessBuilder("sh", "-c", cmd == null ? "" : cmd).redirectErrorStream(false).start();
      StreamGobbler out = new StreamGobbler(p.getInputStream());
      StreamGobbler err = new StreamGobbler(p.getErrorStream());
      Thread t1 = new Thread(out); t1.start();
      Thread t2 = new Thread(err); t2.start();
      boolean done = p.waitFor(to, TimeUnit.MILLISECONDS);
      if (!done) { p.destroyForcibly(); }
      t1.join(2000); t2.join(2000);
      JSONObject o = new JSONObject();
      o.put("ok", done && p.exitValue() == 0);
      o.put("code", done ? p.exitValue() : 124);
      o.put("stdout", cap(out.buf.toString("UTF-8"), 16000));
      o.put("stderr", cap(err.buf.toString("UTF-8"), 4000));
      o.put("timedOut", !done);
      return o.toString();
    } catch (Exception e) {
      try {
        JSONObject o = new JSONObject();
        o.put("ok", false); o.put("code", 1);
        o.put("stdout", ""); o.put("stderr", String.valueOf(e.getMessage()));
        o.put("timedOut", false);
        return o.toString();
      } catch (Exception j) { return "{\"ok\":false}"; }
    }
  }
  @JavascriptInterface
  public String info() {
    try {
      JSONObject o = new JSONObject();
      o.put("embedded", true);
      o.put("sdk", Build.VERSION.SDK_INT);
      o.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
      o.put("termuxApp", isPkg("com.termux"));
      o.put("termuxApi", isPkg("com.termux.api"));
      o.put("execServer", LocalExecServer.isRunning() ? LocalExecServer.port() : 0);
      return o.toString();
    } catch (Exception e) { return "{}"; }
  }
  @JavascriptInterface
  public String setupScript() {
    return TermuxIntents.SETUP_SH;
  }
  private boolean isPkg(String p) {
    try { ctx.getPackageManager().getPackageInfo(p, 0); return true; }
    catch (PackageManager.NameNotFoundException e) { return false; }
  }
  private static String cap(String s, int n) {
    if (s == null) return "";
    return s.length() > n ? s.substring(s.length() - n) : s;
  }
  private static class StreamGobbler implements Runnable {
    final InputStream in; final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    StreamGobbler(InputStream i) { in = i; }
    public void run() {
      byte[] b = new byte[8192];
      try { int r; while ((r = in.read(b)) != -1) { buf.write(b, 0, r); } }
      catch (Exception ignored) {}
    }
  }
}
