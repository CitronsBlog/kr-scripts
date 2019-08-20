package com.omarea.krscript;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.omarea.common.shell.KeepShellPublic;
import com.omarea.krscript.executor.ExtractAssets;
import com.omarea.krscript.executor.ScriptEnvironmen;
import com.omarea.krscript.executor.SimpleShellWatcher;
import com.omarea.krscript.model.ShellHandlerBase;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;

public class WebViewInjector {
    private WebView webView;
    private Context context;

    @SuppressLint("SetJavaScriptEnabled")
    public WebViewInjector(WebView webView) {
        this.webView = webView;
        this.context = webView.getContext();
    }

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled"})
    public void inject() {
        if (webView != null) {

            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setAllowFileAccess(true);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
            webSettings.setAllowFileAccessFromFileURLs(true);

            webView.addJavascriptInterface(
                    new KrScriptEngine(context),
                    "KrScriptCore" // 由于类名会被混淆，写死吧... KrScriptEngine.class.getSimpleName()
            );
        }
    }

    private class KrScriptEngine {
        private Context context;

        private KrScriptEngine(Context context) {
            this.context = context;
        }

        /**
         * 检查是否具有ROOT权限
         * @return
         */
        @JavascriptInterface
        public boolean rootCheck() {
            return KeepShellPublic.INSTANCE.checkRoot();
        }

        /**
         * 同步执行shell脚本 并返回结果（不包含错误信息）
         * @param script 脚本内容
         * @return 执行过程中的输出内容
         */
        @JavascriptInterface
        public String executeShell(String script) {
            if (script != null && !script.isEmpty()) {
                return ScriptEnvironmen.executeResultRoot(context, script);
            }
            return "";
        }

        /**
         *  @param script
         * @param callbackFunction
         */
        @JavascriptInterface
        public boolean executeShellAsync(String script, String callbackFunction, String env) {
            HashMap<String, String> params = new HashMap<>();
            Process process = null;
            try {
                if (env != null && !env.isEmpty()) {
                    JSONObject paramsObject = new JSONObject(env);
                    for (Iterator<String> it = paramsObject.keys(); it.hasNext(); ) {
                        String key = it.next();
                        params.put(key, paramsObject.getString(key));
                    }
                }
                process = Runtime.getRuntime().exec("su");
            } catch (Exception ex) {
                Toast.makeText(context, ex.getMessage(), Toast.LENGTH_SHORT).show();
            }

            if (process != null) {
                final OutputStream outputStream = process.getOutputStream();
                final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

                setHandler(process, callbackFunction, new Runnable(){
                    @Override
                    public void run() {

                    }
                });

                ScriptEnvironmen.executeShell(context, dataOutputStream, script, params);
                return true;
            } else {
                return false;
            }
        }

        /**
         * 提取assets中的文件
         *  @param assets 要提取的文件
         * @return 提取成功后所在的目录
         */
        @JavascriptInterface
        public String extractAssets(String assets) {
            Log.d("extractAssets", assets);
            String output = new ExtractAssets(context).extractResource(assets);
            Log.d("extractAssets", "" + output);
            return output;
        }

        private void setHandler(Process process, final String callbackFunction, final Runnable onExit) {
            final InputStream inputStream = process.getInputStream();
            final InputStream errorStream = process.getErrorStream();
            final Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    String line;
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                        while ((line = bufferedReader.readLine()) != null) {
                            try {
                                final JSONObject message = new JSONObject();
                                message.put("type", ShellHandlerBase.EVENT_REDE);
                                message.put("message", line + "\n");
                                Log.d("output", callbackFunction + "(" + message.toString() + ")");
                                webView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        webView.evaluateJavascript(callbackFunction + "(" + message.toString() + ")", new ValueCallback<String>() {
                                            @Override
                                            public void onReceiveValue(String value) {

                                            }
                                        });
                                    }
                                });
                            } catch (Exception ex) {}
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            final Thread readerError = new Thread(new Runnable() {
                @Override
                public void run() {
                    String line;
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, "UTF-8"));
                        while ((line = bufferedReader.readLine()) != null) {
                            try {
                                final JSONObject message = new JSONObject();
                                message.put("type", ShellHandlerBase.EVENT_READ_ERROR);
                                message.put("message", line + "\n");
                                webView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        webView.evaluateJavascript(callbackFunction + "(" + message.toString() + ")", new ValueCallback<String>() {
                                            @Override
                                            public void onReceiveValue(String value) {

                                            }
                                        });
                                    }
                                });
                            } catch (Exception ex) {}
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            final Process processFinal = process;
            Thread waitExit = new Thread(new Runnable() {
                @Override
                public void run() {
                    int status = -1;
                    try {
                        status = processFinal.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            final JSONObject message = new JSONObject();
                            message.put("type", ShellHandlerBase.EVENT_EXIT);
                            message.put("message", "" + status);
                            webView.post(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript(callbackFunction + "(" + message.toString() + ")", new ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String value) {

                                        }
                                    });
                                }
                            });
                        } catch (Exception ex) {}

                        if (reader.isAlive()) {
                            reader.interrupt();
                        }
                        if (readerError.isAlive()) {
                            readerError.interrupt();
                        }
                        if (onExit != null) {
                            onExit.run();
                        }
                    }
                }
            });

            reader.start();
            readerError.start();
            waitExit.start();
        }
    }
}
