package com.perchance.agentswarm;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
public class TermuxIntents {
  public static final String TERMUX_PKG = "com.termux";
  public static final String TERMUX_API_PKG = "com.termux.api";
  public static final String RUN_COMMAND = "com.termux.app.RUN_COMMAND";
  public static final String FDROID_TERMUX = "https://f-droid.org/en/packages/com.termux/";
  public static final String SETUP_SH =
    "#!/data/data/com.termux/files/usr/bin/sh\n" +
    "set -e\n" +
    "pkg update -y && pkg install -y nodejs git\n" +
    "mkdir -p ~/.agent-swarm\n" +
    "cat > ~/.agent-swarm/mcp-server.js <<'JS'\n" +
    "const http=require('http');\n" +
    "const {execFile}=require('child_process');\n" +
    "function send(res,c,o){const b=Buffer.from(JSON.stringify(o));res.writeHead(c,{'content-type':'application/json','content-length':b.length,'access-control-allow-origin':'*','access-control-allow-methods':'GET,POST,OPTIONS','access-control-allow-headers':'*'});res.end(b);}\n" +
    "const srv=http.createServer((req,res)=>{if(req.method==='OPTIONS')return send(res,204,{});if(req.url==='/health')return send(res,200,{ok:true,name:'agent-swarm-termux'});if(req.url==='/exec'&&req.method==='POST'){let a='';req.on('data',c=>a+=c);req.on('end',()=>{let cmd='',to=120000;try{let j=JSON.parse(a);cmd=j.cmd||'';to=Math.max(5000,Math.min(300000,Number(j.timeoutMs)||120000));}catch(e){}execFile('sh',['-c',cmd],{timeout:to,killSignal:'SIGKILL',maxBuffer:1024*512},(e,so,se)=>send(res,200,{ok:!e,code:e&&e.code!=null?e.code:0,stdout:String(so||'').slice(-16000),stderr:String(se||((e&&e.message)||'')).slice(-4000)}));});return;}send(res,404,{ok:false});});\n" +
    "srv.listen(8080,'0.0.0.0',()=>console.log('agent-swarm termux MCP on :8080'));\n" +
    "JS\n" +
    "echo \"run: node ~/.agent-swarm/mcp-server.js\"\n" +
    "echo \"LLM (optional): pkg install -y llama.cpp && mkdir -p ~/models\"\n" +
    "echo \"  CLI: llama-cli -m ~/models/qwen2-1.5b.gguf -p 'say hi' -n 256\"\n" +
    "echo \"  Server (OpenAI API): llama-server -m ~/models/qwen2-1.5b.gguf --host 0.0.0.0 --port 8080\"\n";
  public static boolean installed(Activity a, String pkg) {
    try { a.getPackageManager().getPackageInfo(pkg, 0); return true; }
    catch (PackageManager.NameNotFoundException e) { return false; }
  }
  public static void openTermux(Activity a) {
    try {
      Intent i = a.getPackageManager().getLaunchIntentForPackage(TERMUX_PKG);
      if (i != null) a.startActivity(i);
      else a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_TERMUX)));
    } catch (Exception e) {
      a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_TERMUX)));
    }
  }
  public static void openStore(Activity a) {
    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_TERMUX)));
  }
  public static boolean runCommand(Activity a, String scriptPath) {
    try {
      Intent i = new Intent(RUN_COMMAND);
      i.setClassName(TERMUX_PKG, "com.termux.app.RunCommandService");
      i.putExtra("com.termux.execute.background", false);
      i.putExtra("com.termux.execute.command_path", scriptPath);
      a.startService(i);
      return true;
    } catch (Exception e) { return false; }
  }
}
