package com.mdc.ads;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.facebook.ads.Ad;
import com.facebook.ads.InterstitialAdListener;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.mediation.admob.AdMobExtras;
import com.millennialmedia.InlineAd;
import com.millennialmedia.MMException;
import com.millennialmedia.MMSDK;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubView;
import com.vungle.publisher.EventListener;
import com.vungle.publisher.VunglePub;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Created by chiennguyen on 7/20/15.
 */
public class AdsManager {
    private String PATH_SERVER_CONFIG = "http://mdcgate.com/config/get_ads_config.php";
    private String SHARE_FILE = AdsManager.class.getName();
    private String SHARE_CONFIG = "config";
    static String tag = AdsManager.class.getSimpleName();
    //private Activity activity;
    private Context context;
    private int showInterstitialCounter = 0;
    private ArrayList<AdsItem> listAdsItem = new ArrayList<>();
    private ArrayList<AdsItem> listInterstitialItem = new ArrayList<>();
    private int iIndexAds = 0;
    private  int iIndexInterstitial = 0;
    private AdsConfig adsConfig = new AdsConfig();
    private FrameLayout flContainer;
    private static AdsManager instant;
    private static Handler hander = new Handler();
    private Handler mainHandler;

    public AdsDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(AdsDelegate delegate) {
        this.delegate = delegate;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    private class EventHandler{
        public static final int ADS_ADD_CLOSE_BTN = 1;
    }
    private AdsDelegate delegate;
    public AdsManager(){

    }

    public interface AdsDelegate{
        void onAdsClick(AdsManager adsManager, boolean bDestroyAds);
        void onNextAds(AdsManager adsManager);
        void onCloseBtnClick(AdsManager adsManager);
        Activity getActivity();
    }

    public static AdsManager getInstant(){
        if(instant==null){
            instant = new AdsManager();
            instant.mainHandler = new Handler(Looper.getMainLooper());
        }
        return instant;
    }

    public void loadConfig(Context context,String appId,String appVersion) throws JSONException
    {
        //load from share preference
        String sConfig = context.getSharedPreferences(SHARE_FILE,0).getString(SHARE_CONFIG,null);
        if(sConfig!=null) setConfig(new JSONObject(sConfig));
        //load config from server
        Object sConfigServer = connectToServer(PATH_SERVER_CONFIG+"?AppId="+appId+"&AppVersion="+appVersion,10,10);
        if(sConfigServer instanceof String){
            Log.i(tag,"ADS-CONFIG="+sConfigServer);
            JSONObject obj = new JSONObject((String) sConfigServer);
            int result = obj.getInt("Result");
            if(result == 1) {
                String sAdsConfig = obj.getString("ads_config");
                //save config
                context.getSharedPreferences(SHARE_FILE,0).edit().putString(SHARE_CONFIG,sAdsConfig).apply();
                setConfig(new JSONObject(sAdsConfig));
            }else Log.i(tag,"Load config failed =" +sConfigServer);

        }else {
            Log.i(tag,"loadConfig failed = "+sConfigServer);
        }
    }

    private Object connectToServer(String path,int connectTimeout,int socketTimeout){
        if(path==null) return null;
        path = path.replaceAll(" ", "%20");
        URL url;
        try {
            url = new URL(path);
            // inputstream
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDefaultUseCaches(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("content-type", "text/plain; charset=utf-8");
            if(connectTimeout> 0)
                connection.setConnectTimeout(connectTimeout * 1000);
            if(socketTimeout > 0)
                connection.setReadTimeout(socketTimeout * 1000);
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if(responseCode == 200){
                InputStream input = connection.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[1024];
                int len = 0;
                while ((len = input.read(buffer)) != -1) {
                    sb.append(new String(buffer,0,len));
                }
                input.close();
                connection.disconnect();
                return sb.toString();
            }else {
                return responseCode;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public View getAdsView(){
        View _adsView = null;
        AdsItem _item = null;
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(-2,-2);
        if(iIndexAds < listAdsItem.size() && adsConfig.adsController==1){
            _item = listAdsItem.get(iIndexAds);
            Log.i(tag,"GetAdsView: " + _item.name +  " Id : "+_item.id);
            if(_item.name.equals("admob")) _adsView = getAdmobView(_item.id);
            else if(_item.name.equals("mopub")) _adsView = getMopubView(_item.id);
            else if(_item.name.equals("mmedia")) _adsView = getMMediView(_item.id);
            else if(_item.name.equals("fb")) _adsView = getFbView(_item.id);
            else if(_item.name.equals("mdc")){
                _adsView = getMDCView(_item.id);
                lp = new ViewGroup.LayoutParams((int)dpToPixels(context,320),(int) dpToPixels(context,50));
            }
        }

        if(_adsView!=null){
            _adsView.setId(R.id.ads_id);
            if(flContainer==null){
                flContainer = new FrameLayout(context);
                flContainer.setBackgroundColor(Color.RED);
                flContainer.setId(R.id.fl_ads_id);
            }
            flContainer.addView(_adsView,  lp);
            if(_item.name.equals("mdc")) addCloseBtn(context); //add close btn
        }

        //hide ads and show it after a delay time
        if(adsConfig.adsDelay > 0 && flContainer !=null) {

            flContainer.setVisibility(View.INVISIBLE);
            hander.postDelayed(new Runnable() {
                @Override
                public void run() {
                    flContainer.setVisibility(View.VISIBLE);
                    flContainer.bringToFront();
                }
            },adsConfig.adsDelay * 1000);

        }

        return  flContainer;
    }

    public void destroyAdsView(){
        Log.i(tag,"destroyAdsView");
        dismissAdsView();
        flContainer = null;
    }

    private void dismissAdsView(){
        bAdsLoadDone = false;
        if(flContainer !=null){
            View _adView = flContainer.findViewById(R.id.ads_id);
            if(_adView instanceof AdView){
                ((AdView) _adView).destroy();
            }
            if(_adView instanceof  MoPubView) ((MoPubView) _adView).destroy();
            if(_adView instanceof com.facebook.ads.AdView) ((com.facebook.ads.AdView) _adView).destroy();
            unbindDrawables(flContainer);
        }
        hander.removeCallbacksAndMessages(null);
        if(adsHander!=null) adsHander.removeCallbacksAndMessages(null);
    }

    private View getMDCView(String id)
    {
        int width = (int) dpToPixels(context,320);
        int height = (int) dpToPixels(context,50);
        final WebView adsWebView = new WebView(context);
        adsWebView.setPadding(0, 0, 0, 0);
        adsWebView.setVerticalScrollBarEnabled(false);
        if(Build.VERSION.SDK_INT >=9) adsWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adsWebView.setBackgroundColor(0);
        WebSettings setting = adsWebView.getSettings();
        setting.setJavaScriptEnabled(true);
        setting.setLoadsImagesAutomatically(true);
        setting.setDefaultTextEncodingName("utf-8");
        final WebViewClientEX webviewHandler = new WebViewClientEX();
        String url = "http://edge.mdcgate.com/ads/get_ads.php?ads_id=" + id + "&width=" + width + "&height=" + height;
        webviewHandler.rootUrl = url;
        adsWebView.setWebViewClient(webviewHandler);
        adsWebView.loadUrl(url);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                webviewHandler.bFinishRootPage = true;
            }
        },5000);
        return adsWebView;
    }

    private class WebViewClientEX extends WebViewClient {
        public String rootUrl = null;
        public boolean bFinishRootPage = false;

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (bFinishRootPage){
                Log.i(tag,"ads Url ="+url);
                openWeb(url);
                onAdsClick();
                return true;
            }else return super.shouldOverrideUrlLoading(view,url);


        }
    }

    private void openWeb(String url){
        Activity activity = null;
        if(delegate!=null) activity = delegate.getActivity();
        if(activity==null) return;
        if(url == null) return;
        Intent browserIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url));
        try {
            activity.startActivity(browserIntent);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    private void unbindDrawables(View view){
        if(view == null) return;
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));

            }
            if (!AdapterView.class.isInstance(view)) {
                ((ViewGroup) view).removeAllViews();
            }
        }
    }

    private void nextAds(){
        if(!bAdsLoadDone && adsConfig.bShowNextAds && listAdsItem.size() > 1){
            if(iIndexAds < listAdsItem.size() -1){
                iIndexAds ++;
                Log.i(tag,"nextads:"+iIndexAds);
                dismissAdsView(); //dismiss the current ads view
                if(getAdsView()==null) // show next ads
                    Log.e(tag, "next to ads index " + iIndexAds + " failed");

            }
        }
    }

    public void resetAdsIndex()
    {
        Log.i(tag,"resetAdsIndex");
        iIndexAds = 0;
        iIndexInterstitial = 0;
    }

    private void onAdsClick(){
        if(delegate!=null) delegate.onAdsClick(this,adsConfig.bCloseAdbAfterClick);
    }

    private View getFbView(String fbId){

        com.facebook.ads.AdView adView = null;
        try {adView = new com.facebook.ads.AdView(context, fbId, com.facebook.ads.AdSize.BANNER_HEIGHT_50);}catch (Exception e){}
        if(adView!=null)
        {
            adView.setId(R.id.ads_id);
            adView.setAdListener(listenerFacebook);
            //AdSettings.addTestDevice("0d5af1ad16dd5784cc26a27f40ed35ed");
            adView.loadAd();
        }
        return adView;
    }

    private View getAdmobView(String admobId){
        AdView adView = new AdView(context);
        adView.setAdUnitId(admobId);
        adView.setId(R.id.ads_id);
        adView.setAdSize(adsConfig.admobBannerType ==0?AdSize.BANNER:AdSize.SMART_BANNER);
        adView.setAdListener(listenerAdmob);
        Bundle bundle = new Bundle();
        AdMobExtras extras = new AdMobExtras(bundle);
        AdRequest adRequest = new AdRequest.Builder()
                .addNetworkExtras(extras)
                        //.addTestDevice("E415EAFB5B790AB85AE4CE815E91C114")
                .build();
        ((AdView)adView).loadAd(adRequest);
        return adView;
    }

    private View getMopubView(String mopubId){
        final MoPubView mopubview = new MoPubView(context);
        mopubview.setId(R.id.ads_id);
        mopubview.setBannerAdListener(listenerMopub);
        mopubview.setAdUnitId(mopubId);
        mopubview.loadAd();
        mopubview.setAutorefreshEnabled(true);
        return mopubview;
    }

    private View getMMediView(String mmediaId){
        if(flContainer==null){
            flContainer = new FrameLayout(context);
            flContainer.setId(R.id.fl_ads_id);
        }
        else flContainer.removeAllViews();
        try {
            Activity activity = null;
            if(delegate!=null) activity = delegate.getActivity();
             if(activity==null) return null;
            MMSDK.initialize(activity);
            InlineAd ad = InlineAd.createInstance(mmediaId,flContainer);
            ad.setListener(listenerMmedia);
            ad.setRefreshInterval(30000);
            //The AdRequest instance is used to pass additional metadata to the server to improve ad selection
            final InlineAd.InlineAdMetadata inlineAdMetadata = new InlineAd.InlineAdMetadata().
                    setAdSize(InlineAd.AdSize.BANNER);

            //Request ads from the server.  If automatic refresh is enabled for your placement new ads will be shown
            //automatically
            ad.request(inlineAdMetadata);
        } catch (MMException e) {
            e.printStackTrace();
        }
        return null;
    };

    public void setAdsInterstitialType(String lAds, String lId){

        Log.i(tag,"ads Interstitial id ="+ lId);
        Log.i(tag,"ads Interstitial type = "+ lAds);
        lAds = "mmedia|mopub|admob|vungle|fb";
        lId = "123|123|ca-app-pub-6180273347195538/9132012000|58b534317fa1e2510600044c|1629229100661008_1632204343696817";
        if(lAds==null || lId==null) return;
        listInterstitialItem.clear();
        ArrayList<String> aAds = new ArrayList<String>();
        ArrayList<String> aId = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(lAds, "|");
        while(st.hasMoreTokens()){
            aAds.add(st.nextToken());
        }
        st = new StringTokenizer(lId,"|");
        while(st.hasMoreTokens()){
            aId.add(st.nextToken());
        }
        if(aAds.size() == aId.size()){
            int length = aAds.size();
            for(int i=0;i<length;i++){
                listInterstitialItem.add(new AdsItem(aAds.get(i), aId.get(i)));
            }
        }
    }

    public void setAdsType(String lAds, String lId){
        Log.i(tag,"ads id ="+ lId);
        Log.i(tag,"ads type = "+ lAds);
        lAds = "admob";
        lId = "ca-app-pub-6180273347195538/6484962009";
        if(lAds==null || lId==null) return;
        listAdsItem.clear();
        ArrayList<String> aAds = new ArrayList<String>();
        ArrayList<String> aId = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(lAds, "|");
        while(st.hasMoreTokens()){
            aAds.add(st.nextToken());
        }
        st = new StringTokenizer(lId,"|");
        while(st.hasMoreTokens()){
            aId.add(st.nextToken());
        }
        if(aAds.size() == aId.size()){
            int length = aAds.size();
            for(int i=0;i<length;i++){
                listAdsItem.add(new AdsItem(aAds.get(i), aId.get(i)));
            }
        }
    }

    private ADSHandler adsHander = null;
    private  boolean bAdsLoadDone = false;
    private void onLoadDone(Context context){
        bAdsLoadDone = true;
        if(adsHander==null) adsHander = new ADSHandler(context);
        if(flContainer !=null){
            flContainer.bringToFront();
            //add close btn if need
            if(adsConfig.showCloseBtnType==AdsConfig.LARGE_CLOSE || adsConfig.showCloseBtnType == AdsConfig.SMALL_CLOSE){
                if(flContainer.findViewById(R.id.ads_close_btn)==null) {
                    adsHander.removeMessages(EventHandler.ADS_ADD_CLOSE_BTN);
                    adsHander.setOwner(context);
                    adsHander.sendEmptyMessageDelayed(EventHandler.ADS_ADD_CLOSE_BTN, adsConfig.adsDelayAddCloseBtn*1000);
                }else{
                    flContainer.findViewById(R.id.ads_close_btn).bringToFront();
                }
            }

            //start animation
            /*
            if(flContainer.findViewById(R.id.ads_id)!=null)
                YoYo.with(Techniques.BounceInRight).duration(3000).withListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        if(flContainer!=null && flContainer.findViewById(R.id.ads_close_btn)!=null) flContainer.findViewById(R.id.ads_close_btn).bringToFront();
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {

                    }
                }).playOn(flContainer.findViewById(R.id.ads_id));
             */
            //if(flContainer!=null) YoYo.with(Techniques.BounceInRight).duration(3000).playOn(flContainer);

        }
    }

    private void addCloseBtn(Context context){
        Log.i(tag,"addCloseBtn");
        if(flContainer !=null && flContainer.findViewById(R.id.ads_close_btn)==null){
            ImageView close = new ImageView(context);
            close.setId(R.id.ads_close_btn);

            FrameLayout.LayoutParams lp;
            if(adsConfig.showCloseBtnType == AdsConfig.LARGE_CLOSE){
                close.setImageResource(R.drawable.ic_remove_ads);
                lp = new FrameLayout.LayoutParams(-2,-2);
                lp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
                lp.rightMargin = (int) dpToPixels(context, 2);

            }else {
                int btnHeight = (int)dpToPixels(context,20);
                close.setImageResource(R.drawable.close_red);
                lp = new FrameLayout.LayoutParams(btnHeight,btnHeight);
                lp.gravity = Gravity.RIGHT;
                if(flContainer!=null && flContainer.findViewById(R.id.ads_id)!=null)
                {
                    FrameLayout.LayoutParams adsLayoutParam = (FrameLayout.LayoutParams) flContainer.findViewById(R.id.ads_id).getLayoutParams();
                    adsLayoutParam.topMargin = btnHeight/2;
                    adsLayoutParam.leftMargin = btnHeight/2;
                    adsLayoutParam.rightMargin = btnHeight/2;
                    flContainer.findViewById(R.id.ads_id).setLayoutParams(adsLayoutParam);
                }
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(delegate!=null) delegate.onCloseBtnClick(instant);
                    }
                });
            }
            flContainer.addView(close, lp);

        }
    }

    private float dpToPixels(Context context, float dp){
        Resources r = context.getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
        return px;
    }

    private void setConfig(JSONObject obj) throws JSONException
    {

        if(obj.has("ads")) {
            adsConfig.adsController = obj.getInt("ads");
        }
        if(obj.has("ads_types") && obj.has("ads_ids")){
            adsConfig.adsId = obj.getString("ads_ids");
            adsConfig.adsType = obj.getString("ads_types");
        }
        setAdsType(adsConfig.adsType, adsConfig.adsId);
        if(obj.has("ads_delay")){
            adsConfig.adsDelay =  obj.getInt("ads_delay");
        }

        if(obj.has("interstitial_types") && obj.has("interstitial_ids")){
            adsConfig.interstitialType = obj.getString("interstitial_types");
            adsConfig.interstitialId = obj.getString("interstitial_ids");
        }
        setAdsInterstitialType(adsConfig.interstitialType, adsConfig.interstitialId);

        if(obj.has("interstitial_interval")) adsConfig.adsInterstitialInterval = obj.getInt("interstitial_interval");
        if(obj.has("ads_add_close_btn_delay")) adsConfig.adsDelayAddCloseBtn = obj.getInt("ads_add_close_btn_delay");
        if(obj.has("ads_show_close_btn")) adsConfig.showCloseBtnType = obj.getInt("ads_show_close_btn");
        if(obj.has("ads_show_next")) adsConfig.bShowNextAds = obj.getInt("ads_show_next")==0?false:true;
        if(obj.has("admob_banner_type")) adsConfig.admobBannerType = obj.getInt("admob_banner_type");
        if(obj.has("ads_close_after_click")) adsConfig.bCloseAdbAfterClick = obj.getInt("ads_close_after_click")==0?false:true;
    }



    public class AdsConfig{

        public static final int LARGE_CLOSE = 1;
        public static final int SMALL_CLOSE = 2;

        public int adsController = 1;
        public int adsOption = 0;
        public int adsDelay = 5; /*second*/
        public int adsDelayAddCloseBtn=5; /*second*/
        public String adsType = "admob";
        public String adsId = "ca-app-pub-6180273347195538/3153962406";
        public String interstitialType = "admob";
        public String interstitialId = "ca-app-pub-6180273347195538/7584162001";
        public int showCloseBtnType = LARGE_CLOSE;
        public boolean bShowNextAds;
        public int adsInterstitialInterval;
        public int admobBannerType;
        public boolean bCloseAdbAfterClick = true;
    }

    private class AdsItem{
        String name;
        String id;

        public AdsItem(String name, String id) {
            super();
            this.name = name;
            this.id = id;
        }

        public void log(){
            Log.i(tag,"ads = "+name + " id = "+id);
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof AdsItem){
                if(((AdsItem) o).name != null && ((AdsItem) o).name.equals(name)){
                    return true;
                }
            }
            return false;
        }
    }

    private class ADSHandler extends WeakHandler<Context> {
        public ADSHandler(Context context){
            super(context);

        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(tag,"handleMessage="+msg.what);
            Context context = getOwner();
            if(context==null) return;
            switch (msg.what){
                case EventHandler.ADS_ADD_CLOSE_BTN:
                    addCloseBtn(context);
                    break;
            }



        }
    }

    private abstract class WeakHandler<T> extends Handler {
        private WeakReference<T> mOwner;

        public WeakHandler(T owner) {
            mOwner = new WeakReference<T>(owner);
        }
        public void setOwner(T owner){
            mOwner = new WeakReference<T>(owner);
        }
        public T getOwner() {
            return mOwner.get();
        }
    }

    private boolean nextAdsInterstitial(){
        boolean _result = false;
        if(adsConfig.bShowNextAds && iIndexInterstitial < listInterstitialItem.size() -1){
            iIndexInterstitial ++;
            _result = true;
            Log.i(tag,"nextinterstitial:"+iIndexInterstitial);
        }
        return _result;
    }

    static InterstitialAd interstitialAd;
    static com.millennialmedia.InterstitialAd mmInterstitial;
    private MoPubInterstitial mopubInterstitial;
    private boolean bAdcolonyConfigure;
    com.facebook.ads.InterstitialAd facebookInterstitial;

    public void initInterstitial(final Activity activity){
        //adsInterstitial = "adcolony";
        //adsInterstitialId = "appf21856ac91d84e0e83*vz631bd14be2ed4af1bc";
        //adsInterstitialInterval = 1;
        String adsInterstitialId = null;
        String adsInterstitial = null;

        if(iIndexInterstitial < listInterstitialItem.size()){
            adsInterstitial = listInterstitialItem.get(iIndexInterstitial).name;
            adsInterstitialId = listInterstitialItem.get(iIndexInterstitial).id;
        }else return;

        Log.i(tag,"init interstitial with :"+adsInterstitial + " id :"+adsInterstitialId);
        if(adsInterstitial.equals("vungle")){
            com.vungle.sdk.VunglePub.setSoundEnabled(true);
            VunglePub.getInstance().init(activity,adsInterstitialId);
            VunglePub.getInstance().setEventListeners(new EventListener() {
                @Override
                public void onAdEnd(boolean b, boolean b1) {

                }

                @Override
                public void onAdStart() {

                }

                @Override
                public void onAdUnavailable(String s) {
                    if(nextAdsInterstitial()) initInterstitial(activity);
                }

                @Override
                public void onAdPlayableChanged(boolean b) {

                }

                @Override
                public void onVideoView(boolean b, int i, int i1) {

                }
            });
        }else if(adsInterstitial.equals("admob")){
            // Create the interstitial.
            if(interstitialAd==null)
            {
                interstitialAd = new InterstitialAd(context);
                interstitialAd.setAdUnitId(adsInterstitialId);
                interstitialAd.setAdListener(new AdListener() {

                    @Override
                    public void onAdFailedToLoad(int errorCode) {
                        Log.e(tag,"admob interstitial load failed");
                        if(nextAdsInterstitial())
                            initInterstitial(activity);
                        super.onAdFailedToLoad(errorCode);
                    }

                    @Override
                    public void onAdLoaded() {
                        // TODO Auto-generated method stub
                        super.onAdLoaded();
                    }
                });
            }

            // Create ad request.
            AdRequest adRequest = new AdRequest.Builder().build();

            // Begin loading your interstitial.
            interstitialAd.loadAd(adRequest);
        }else if(adsInterstitial.equals("mmedia")){
            Log.i(tag,"init mmedia");
            try {
                if(mmInterstitial==null)
                {
                    MMSDK.initialize(activity);
                    mmInterstitial = com.millennialmedia.InterstitialAd.createInstance(adsInterstitialId);
                    mmInterstitial.setListener(listenerMmediaInterstitial);
                }

                com.millennialmedia.InterstitialAd.InterstitialAdMetadata interstitialAdMetadata =
                        new com.millennialmedia.InterstitialAd.InterstitialAdMetadata();
                mmInterstitial.load(activity, interstitialAdMetadata);
            } catch (MMException e) {
                e.printStackTrace();
            }

        }else if(adsInterstitial.equals("mopub")){
            if(mopubInterstitial==null)
            {
                mopubInterstitial = new MoPubInterstitial(activity, adsInterstitialId);
                mopubInterstitial.setInterstitialAdListener(new MoPubInterstitial.InterstitialAdListener() {
                    @Override
                    public void onInterstitialLoaded(MoPubInterstitial interstitial) {

                    }

                    @Override
                    public void onInterstitialFailed(MoPubInterstitial interstitial, MoPubErrorCode errorCode) {
                        if (nextAdsInterstitial())
                            initInterstitial(activity);
                    }

                    @Override
                    public void onInterstitialShown(MoPubInterstitial interstitial) {

                    }

                    @Override
                    public void onInterstitialClicked(MoPubInterstitial interstitial) {

                    }

                    @Override
                    public void onInterstitialDismissed(MoPubInterstitial interstitial) {

                    }
                });
            }
            mopubInterstitial.load();
        }else if(adsInterstitial.equals("fb")){
            if(facebookInterstitial==null)
            {
                facebookInterstitial = new com.facebook.ads.InterstitialAd(activity, adsInterstitialId);
                facebookInterstitial.setAdListener(listenerFacebookInterstitial);
            }
            facebookInterstitial.loadAd();

        }
    }

    public void destroyInterstitial(){
        bAdcolonyConfigure = false;
        if(mopubInterstitial!=null) mopubInterstitial.destroy();
        if(facebookInterstitial!=null) facebookInterstitial.destroy();
    }



    public void showInterstitial(Activity activity){
        String adsInterstitialId = null;
        String adsInterstitial = null;

        if(iIndexInterstitial < listInterstitialItem.size()){
            adsInterstitial = listInterstitialItem.get(iIndexInterstitial).name;
            adsInterstitialId = listInterstitialItem.get(iIndexInterstitial).id;
        }else return;



        if(showInterstitialCounter % adsConfig.adsInterstitialInterval != 0){
            showInterstitialCounter ++;
            return;
        }
        showInterstitialCounter ++;
        Log.i(tag,"show interstitial with name = "+adsInterstitial + " id = "+adsInterstitialId);


        if(adsInterstitial.equals("vungle")){
            if(VunglePub.getInstance().isAdPlayable()){
                VunglePub.getInstance().playAd();
            }
            else if(nextAdsInterstitial()) initInterstitial(activity);
        }else if(adsInterstitial.equals("admob")){
            if(interstitialAd!=null){
                if (interstitialAd.isLoaded()) {
                    interstitialAd.show();
                    //interstitialAd.loadAd(new AdRequest.Builder().build());
                }

            }

        }else if(adsInterstitial.equals("mmedia") && mmInterstitial!=null){
            if(mmInterstitial.isReady()){
                try {
                    mmInterstitial.show(activity);
                    //load new ad
//                    com.millennialmedia.InterstitialAd.InterstitialAdMetadata interstitialAdMetadata =
//                            new com.millennialmedia.InterstitialAd.InterstitialAdMetadata();
//                    mmInterstitial.load(activity, interstitialAdMetadata);
                } catch (MMException e) {
                    e.printStackTrace();
                }
            }

        }else if(adsInterstitial.equals("mopub") && mopubInterstitial!=null){
            if(mopubInterstitial.isReady()){
                mopubInterstitial.show();
                //mopubInterstitial.load();
            }
        }

        else if(adsInterstitial.equals("fb") && facebookInterstitial!=null){
            if(facebookInterstitial.isAdLoaded()){
                facebookInterstitial.show();
                //facebookInterstitial.loadAd();
            }

        }

    }


    InlineAd.InlineListener listenerMmedia = new InlineAd.InlineListener() {
        @Override
        public void onRequestSucceeded(InlineAd inlineAd) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onLoadDone(context);
                }
            });

        }

        @Override
        public void onRequestFailed(InlineAd inlineAd, InlineAd.InlineErrorStatus inlineErrorStatus) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    nextAds();
                }
            });

        }

        @Override
        public void onClicked(InlineAd inlineAd) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onAdsClick();
                }
            });
        }

        @Override
        public void onResize(InlineAd inlineAd, int i, int i1) {

        }

        @Override
        public void onResized(InlineAd inlineAd, int i, int i1, boolean b) {

        }

        @Override
        public void onExpanded(InlineAd inlineAd) {

        }

        @Override
        public void onCollapsed(InlineAd inlineAd) {

        }

        @Override
        public void onAdLeftApplication(InlineAd inlineAd) {

        }
    };

    MoPubView.BannerAdListener listenerMopub = new MoPubView.BannerAdListener() {

        @Override
        public void onBannerLoaded(MoPubView arg0) {
            onLoadDone(context);
        }

        @Override
        public void onBannerFailed(MoPubView arg0, MoPubErrorCode arg1) {
            nextAds();
        }

        @Override
        public void onBannerExpanded(MoPubView arg0) {


        }

        @Override
        public void onBannerCollapsed(MoPubView arg0) {

        }

        @Override
        public void onBannerClicked(MoPubView arg0) {
            onAdsClick();

        }
    };

    AdListener listenerAdmob = new AdListener() {
        @Override
        public void onAdClosed() {
            Log.i(tag,"onAdClosed");
            super.onAdClosed();
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            nextAds();
            super.onAdFailedToLoad(errorCode);
        }

        @Override
        public void onAdLeftApplication() {
            onAdsClick();
            super.onAdLeftApplication();
        }

        @Override
        public void onAdLoaded() {
            onLoadDone(context);
            super.onAdLoaded();
        }

        @Override
        public void onAdOpened() {
            Log.i(tag, "onAdOpened");
            super.onAdOpened();
        }
    };


    private com.facebook.ads.AdListener listenerFacebook = new com.facebook.ads.AdListener() {
        @Override
        public void onError(Ad ad, com.facebook.ads.AdError adError) {
            nextAds();
        }

        @Override
        public void onAdLoaded(Ad ad) {
            onLoadDone(context);
        }

        @Override
        public void onAdClicked(Ad ad) {
            onAdsClick();
        }
    };

     private InterstitialAdListener listenerFacebookInterstitial = new InterstitialAdListener() {
        @Override
        public void onError(Ad ad, com.facebook.ads.AdError adError) {
            Activity activity = null;
            if(delegate!=null) activity = delegate.getActivity();
            if(nextAdsInterstitial() && activity!=null)
                initInterstitial(activity);
        }

        @Override
        public void onAdLoaded(Ad ad) {

        }

        @Override
        public void onAdClicked(Ad ad) {

        }

        @Override
        public void onInterstitialDisplayed(Ad ad) {

        }

        @Override
        public void onInterstitialDismissed(Ad ad) {
            Log.i(tag,"Facebook: onInterstitialDismissed");
            ad.loadAd();

        }
    };

    com.millennialmedia.InterstitialAd.InterstitialListener listenerMmediaInterstitial = new com.millennialmedia.InterstitialAd.InterstitialListener() {
        @Override
        public void onLoaded(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onLoadFailed(com.millennialmedia.InterstitialAd interstitialAd, com.millennialmedia.InterstitialAd.InterstitialErrorStatus interstitialErrorStatus) {
            if(delegate!=null){
                final Activity activity = delegate.getActivity();
                if(nextAdsInterstitial() && activity!=null)
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            initInterstitial(activity);
                        }
                    });
            }
        }

        @Override
        public void onShown(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onShowFailed(com.millennialmedia.InterstitialAd interstitialAd, com.millennialmedia.InterstitialAd.InterstitialErrorStatus interstitialErrorStatus) {

        }

        @Override
        public void onClosed(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onClicked(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onAdLeftApplication(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onExpired(com.millennialmedia.InterstitialAd interstitialAd) {

        }
    };


    

}
