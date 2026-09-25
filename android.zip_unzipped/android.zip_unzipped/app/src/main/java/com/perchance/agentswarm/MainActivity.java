package com.perchance.agentswarm;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
public class MainActivity extends Activity {
  private WebView wv;
  @Override protected void onCreate(Bundle s) {
    super.onCreate(s);
    LocalExecServer.start(8766);
    wv = new WebView(this);
    WebSettings st = wv.getSettings();
    st.setJavaScriptEnabled(true);
    st.setDomStorageEnabled(true);
    st.setMediaPlaybackRequiresUserGesture(false);
    st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    st.setAllowFileAccess(false);
    wv.addJavascriptInterface(new TermuxBridge(this), "TermuxBridge");
    wv.setWebViewClient(new WebViewClient());
    wv.loadUrl("https://perchance.org/agent-swarm");
    setContentView(wv);
  }
  @Override protected void onDestroy() {
    LocalExecServer.stop();
    super.onDestroy();
  }
  @Override public void onBackPressed() {
    if (wv != null && wv.canGoBack()) wv.goBack();
    else super.onBackPressed();
  }
}
