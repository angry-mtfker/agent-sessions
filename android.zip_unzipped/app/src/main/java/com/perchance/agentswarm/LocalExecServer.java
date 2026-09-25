package com.perchance.agentswarm;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
public class LocalExecServer {
  private static ServerSocket ss;
  private static Thread th;
  private static int boundPort = 0;
  public static synchronized int port() { return boundPort; }
  public static synchronized boolean isRunning() { return ss != null && !ss.isClosed(); }
  public static synchronized void start(int preferPort) {
    if (isRunning()) return;
    th = new Thread(() -> {
      for (int p = preferPort; p < preferPort + 20; p++) {
        try {
          ServerSocket s = new ServerSocket(p, 8, java.net.InetAddress.getByName("127.0.0.1"));
          synchronized (LocalExecServer.class) { ss = s; boundPort = p; }
          break;
        } catch (Exception ignored) {}
      }
      if (ss == null) return;
      while (!ss.isClosed()) {
        try {
          Socket c = ss.accept();
          Thread h = new Thread(() -> handle(c));
          h.setDaemon(true); h.start();
        } catch (Exception e) { break; }
      }
    });
    th.setDaemon(true); th.start();
  }
  public static synchronized void stop() {
    try { if (ss != null) ss.close(); } catch (Exception ignored) {}
    ss = null; boundPort = 0;
  }
  private static void handle(Socket c) {
    try {
      c.setSoTimeout(310000);
      InputStream in = c.getInputStream();
      OutputStream out = c.getOutputStream();
      ByteArrayOutputStream head = new ByteArrayOutputStream();
      int b, last4 = 0;
      while ((b = in.read()) != -1) {
        head.write(b);
        last4 = ((last4 << 8) | (b & 0xFF));
        if (head.size() > 65536) break;
        if (head.size() >= 4 && head.toString("ISO-8859-1").endsWith("\r\n\r\n")) break;
      }
      String hs = head.toString("ISO-8859-1");
      int sp1 = hs.indexOf(' ');
      int sp2 = hs.indexOf(' ', sp1 + 1);
      String method = sp1 > 0 ? hs.substring(0, sp1) : "GET";
      String path = (sp1 > 0 && sp2 > sp1) ? hs.substring(sp1 + 1, sp2) : "/";
      int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
      int contentLen = 0;
      for (String line : hs.split("\r\n")) {
        if (line.toLowerCase().startsWith("content-length:")) {
          try { contentLen = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) {}
        }
      }
      byte[] body = new byte[0];
      if (contentLen > 0 && contentLen < 4 * 1024 * 1024) {
        body = new byte[contentLen];
        int off = 0;
        while (off < contentLen) {
          int r = in.read(body, off, contentLen - off);
          if (r == -1) break;
          off += r;
        }
      }
      if (method.equals("OPTIONS")) {
        reply(out, 204, "{}", true);
      } else if (path.equals("/health")) {
        JSONObject o = new JSONObject();
        o.put("ok", true); o.put("name", "agent-swarm-embedded"); o.put("port", boundPort);
        reply(out, 200, o.toString(), true);
      } else if (path.equals("/exec") && method.equals("POST")) {
        reply(out, 200, runCmd(new String(body, StandardCharsets.UTF_8)), true);
      } else {
        reply(out, 404, "{\"ok\":false,\"error\":\"not found\"}", true);
      }
      c.close();
    } catch (Exception ignored) { try { c.close(); } catch (Exception e2) {} }
  }
  private static String runCmd(String body) {
    try {
      JSONObject req = new JSONObject(body);
      String cmd = req.optString("cmd", "");
      int timeoutMs = req.optInt("timeoutMs", 120000);
      if (timeoutMs <= 0) timeoutMs = 120000;
      if (timeoutMs > 300000) timeoutMs = 300000;
      Process p = new ProcessBuilder("sh", "-c", cmd).start();
      Gob g1 = new Gob(p.getInputStream()); Gob g2 = new Gob(p.getErrorStream());
      Thread t1 = new Thread(g1); t1.start();
      Thread t2 = new Thread(g2); t2.start();
      boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
      if (!done) p.destroyForcibly();
      t1.join(2000); t2.join(2000);
      JSONObject o = new JSONObject();
      o.put("ok", done && p.exitValue() == 0);
      o.put("code", done ? p.exitValue() : 124);
      String so = g1.buf.toString("UTF-8"); String se = g2.buf.toString("UTF-8");
      o.put("stdout", so.length() > 16000 ? so.substring(so.length() - 16000) : so);
      o.put("stderr", se.length() > 4000 ? se.substring(se.length() - 4000) : se);
      o.put("timedOut", !done);
      return o.toString();
    } catch (Exception e) {
      return "{\"ok\":false,\"code\":1,\"stdout\":\"\",\"stderr\":\"" + JSONObject.quote(String.valueOf(e.getMessage())) + "\"}";
    }
  }
  private static void reply(OutputStream out, int code, String json, boolean cors) throws Exception {
    byte[] b = json.getBytes(StandardCharsets.UTF_8);
    String st = code == 200 ? "OK" : code == 204 ? "No Content" : "Not Found";
    StringBuilder h = new StringBuilder();
    h.append("HTTP/1.1 ").append(code).append(" ").append(st).append("\r\n");
    h.append("Content-Type: application/json; charset=utf-8\r\n");
    h.append("Content-Length: ").append(b.length).append("\r\n");
    h.append("Connection: close\r\n");
    if (cors) {
      h.append("Access-Control-Allow-Origin: *\r\n");
      h.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
      h.append("Access-Control-Allow-Headers: *\r\n");
    }
    h.append("\r\n");
    out.write(h.toString().getBytes("ISO-8859-1"));
    out.write(b);
    out.flush();
  }
  private static class Gob implements Runnable {
    final InputStream in; final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    Gob(InputStream i) { in = i; }
    public void run() {
      byte[] t = new byte[8192];
      try { int r; while ((r = in.read(t)) != -1) buf.write(t, 0, r); }
      catch (Exception ignored) {}
    }
  }
}
