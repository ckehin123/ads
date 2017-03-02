package com.mdc.ads;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.facebook.ads.Ad;
import com.facebook.ads.InterstitialAdListener;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.mediation.admob.AdMobExtras;
import com.jirbo.adcolony.AdColony;
import com.jirbo.adcolony.AdColonyAdAvailabilityListener;
import com.jirbo.adcolony.AdColonyVideoAd;
import com.millennialmedia.InlineAd;
import com.millennialmedia.MMException;
import com.millennialmedia.MMSDK;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubView;
import com.nineoldandroids.animation.Animator;
import com.yrkfgo.assxqx4.AdConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * Created by chiennguyen on 7/20/15.
 */
public class AdsManager {

    static String tag = AdsManager.class.getSimpleName();
    private Activity activity;
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
    }

    public static AdsManager getInstant(){
        if(instant==null){
            instant = new AdsManager();
        }
        return instant;
    }

    public void setActivity(Activity activity){
        this.activity = activity;
    }


    public View getAdsView(){

        View _adsView = null;
        AdsItem _item = null;

        if(iIndexAds < listAdsItem.size() && adsConfig.adsController==1){
            _item = listAdsItem.get(iIndexAds);
            Log.i(tag,"GetAdsView: " + _item.name +  " Id : "+_item.id);
            if(_item.name.equals("admob")) _adsView = getAdmobView(_item.id);
            else if(_item.name.equals("mopub")) _adsView = getMopubView(_item.id);
            else if(_item.name.equals("mmedia")) _adsView = getMMediView(_item.id);
            else if(_item.name.equals("airpush")) _adsView = getAirpushView(_item.id);
            else if(_item.name.equals("fb")) _adsView = getFbView(_item.id);
        }

        if(_adsView!=null){
            _adsView.setId(R.id.ads_id);
            flContainer = new FrameLayout(context);
            flContainer.setId(R.id.fl_ads_id);
            flContainer.addView(_adsView, new ViewGroup.LayoutParams(-2, -2));
        }

        //hide ads and show it after a delay time
        if(adsConfig.adsDelay > 0 && flContainer !=null) {

            flContainer.setVisibility(View.INVISIBLE);
            hander.postDelayed(new Runnable() {
                @Override
                public void run() {
                    flContainer.setVisibility(View.VISIBLE);
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
                dismissAdsView(); //dismiss the current ads view
                if(getAdsView()==null) // show next ads
                    Log.e(tag, "next to ads index " + iIndexAds + " failed");
                Log.i(tag,"nextads:"+iIndexAds);
            }
        }
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

    private View getAirpushView(String airpushId){
        AdConfig.setAppId(Integer.parseInt(airpushId));
        AdConfig.setApiKey("1345103177648703821");
        AdConfig.setAdListener(listenerAirpush);
        AdConfig.setCachingEnabled(true);
        AdConfig.setTestMode(false);

        final com.yrkfgo.assxqx4.AdView adView = new com.yrkfgo.assxqx4.AdView(activity);
        adView.setId(R.id.ads_id);
        adView.setBannerType(com.yrkfgo.assxqx4.AdView.BANNER_TYPE_IN_APP_AD);
        adView.setBannerAnimation(com.yrkfgo.assxqx4.AdView.ANIMATION_TYPE_FADE);
        adView.showMRinInApp(false);
        adView.loadAd();
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
        final MoPubView mopubview = (MoPubView) View.inflate(context,R.layout.view_mopub,null);
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
        //lAds = "fb";
        //lId = "1629229100661008_1632204343696817";
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
        lAds = "mmedia";
        lId = "168362";
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
            if(flContainer.findViewById(R.id.ads_id)!=null)
                YoYo.with(Techniques.BounceInRight).duration(3000).withListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        /*
                        if (flContainer != null && flContainer.findViewById(R.id.ads_id) != null) {
                            flContainer.bringToFront();
                            flContainer.requestFocus();
                            flContainer.findViewById(R.id.ads_id).bringToFront();
                        }
                        */
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

    public void setConfig(JSONObject obj) throws JSONException
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
        if(adsConfig.bShowNextAds && listInterstitialItem.size() > 1){
            if(iIndexInterstitial < listInterstitialItem.size() -1) iIndexInterstitial ++;
            else iIndexInterstitial = 0;
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

        if(adsInterstitial.equals("admob")){
            // Create the interstitial.
            interstitialAd = new InterstitialAd(activity);
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
            // Create ad request.
            AdRequest adRequest = new AdRequest.Builder().build();

            // Begin loading your interstitial.
            interstitialAd.loadAd(adRequest);
        }else if(adsInterstitial.equals("adcolony")){
            bAdcolonyConfigure = true;
            Log.i(tag,"init adcolony");
            String ZONE_ID = "vzc65587b6da6e4755821f86";
            String ADS_ID = adsInterstitialId;
            int index = adsInterstitialId.indexOf("*");
            if(index!=-1){
                ZONE_ID = adsInterstitialId.substring(index+1, adsInterstitialId.length());
                ADS_ID = adsInterstitialId.substring(0,index);
            }
            try{AdColony.configure(activity, "version:1.0,store:google", ADS_ID, ZONE_ID);}catch (Exception e){}
            AdColony.addAdAvailabilityListener(new AdColonyAdAvailabilityListener() {

                @Override
                public void onAdColonyAdAvailabilityChange(boolean arg0, String arg1) {
                    Log.i(tag, "adcolony = " + arg0);
                    if (!arg0) {
                        if (nextAdsInterstitial()) {
                            initInterstitial(activity);
                        }
                    }

                }
            });
        }else if(adsInterstitial.equals("mmedia")){
            Log.i(tag,"init mmedia");
            try {
                MMSDK.initialize(activity);
                mmInterstitial = com.millennialmedia.InterstitialAd.createInstance(adsInterstitialId);
                mmInterstitial.setListener(listenerMmediaInterstitial);
                com.millennialmedia.InterstitialAd.InterstitialAdMetadata interstitialAdMetadata =
                        new com.millennialmedia.InterstitialAd.InterstitialAdMetadata();
                mmInterstitial.load(activity, interstitialAdMetadata);
            } catch (MMException e) {
                e.printStackTrace();
            }

        }else if(adsInterstitial.equals("mopub")){
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
            mopubInterstitial.load();
        }else if(adsInterstitial.equals("fb")){

            facebookInterstitial = new com.facebook.ads.InterstitialAd(activity, adsInterstitialId);
            facebookInterstitial.setAdListener(listenerFacebookInterstitial);
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
        Log.i(tag,"show interstitial");
        if(adsInterstitial.equals("admob")){
            Log.i(tag,"show interstitial admob");
            if(interstitialAd!=null){
                if (interstitialAd.isLoaded()) {
                    interstitialAd.show();
                    initInterstitial(activity);
                }else {
                    Log.i(tag,"admob interstitial loading...");

                }

            }

        }else if(adsInterstitial.equals("adcolony")){
            Log.i(tag,"show adcolony");
            int index = adsInterstitialId.indexOf("*");
            String ZONE_ID = "vzc65587b6da6e4755821f86";
            if(index!=-1){
                ZONE_ID = adsInterstitialId.substring(index+1, adsInterstitialId.length());
            }
            if(bAdcolonyConfigure){
                AdColonyVideoAd ad = new AdColonyVideoAd(ZONE_ID);
                if(ad.isReady()){
                    ad.show();
                }else{
                    Log.i(tag,"adcolony is not available, next...");
                    if(nextAdsInterstitial()){
                        initInterstitial(activity);
                    }
                }
            }else initInterstitial(activity);


        }else if(adsInterstitial.equals("mmedia") && mmInterstitial!=null){
            if(mmInterstitial.isReady()){
                try {
                    mmInterstitial.show(activity);
                    //load new ad
                    com.millennialmedia.InterstitialAd.InterstitialAdMetadata interstitialAdMetadata =
                            new com.millennialmedia.InterstitialAd.InterstitialAdMetadata();
                    mmInterstitial.load(activity, interstitialAdMetadata);
                } catch (MMException e) {
                    e.printStackTrace();
                }
            }

        }else if(adsInterstitial.equals("mopub") && mopubInterstitial!=null){
            if(mopubInterstitial.isReady()){
                mopubInterstitial.show();
                mopubInterstitial.load();
            }else{
                if(nextAdsInterstitial()){
                    initInterstitial(activity);
                }
            }
        }

        else if(adsInterstitial.equals("fb") && facebookInterstitial!=null){
            if(facebookInterstitial.isAdLoaded()) facebookInterstitial.show();
            else facebookInterstitial.loadAd();
        }

    }


    InlineAd.InlineListener listenerMmedia = new InlineAd.InlineListener() {
        @Override
        public void onRequestSucceeded(InlineAd inlineAd) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onLoadDone(activity);
                }
            });

        }

        @Override
        public void onRequestFailed(InlineAd inlineAd, InlineAd.InlineErrorStatus inlineErrorStatus) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nextAds();
                }
            });

        }

        @Override
        public void onClicked(InlineAd inlineAd) {
            activity.runOnUiThread(new Runnable() {
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
            onLoadDone(activity);
        }

        @Override
        public void onAdClicked(Ad ad) {
            onAdsClick();
        }
    };

     private InterstitialAdListener listenerFacebookInterstitial = new InterstitialAdListener() {
        @Override
        public void onError(Ad ad, com.facebook.ads.AdError adError) {
            if(nextAdsInterstitial())
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





    com.yrkfgo.assxqx4.AdListener listenerAirpush = new com.yrkfgo.assxqx4.AdListener() {
        @Override
        public void onAdCached(AdConfig.AdType adType) {

        }

        @Override
        public void onIntegrationError(String s) {

        }

        @Override
        public void onAdError(String s) {
            nextAds();

        }

        @Override
        public void noAdListener() {

        }

        @Override
        public void onAdShowing() {

        }

        @Override
        public void onAdClosed() {

        }

        @Override
        public void onAdLoadingListener() {

        }

        @Override
        public void onAdLoadedListener() {
            onLoadDone(context);

        }

        @Override
        public void onCloseListener() {

        }

        @Override
        public void onAdExpandedListner() {

        }

        @Override
        public void onAdClickedListener() {
            onAdsClick();

        }
    };

    com.millennialmedia.InterstitialAd.InterstitialListener listenerMmediaInterstitial = new com.millennialmedia.InterstitialAd.InterstitialListener() {
        @Override
        public void onLoaded(com.millennialmedia.InterstitialAd interstitialAd) {

        }

        @Override
        public void onLoadFailed(com.millennialmedia.InterstitialAd interstitialAd, com.millennialmedia.InterstitialAd.InterstitialErrorStatus interstitialErrorStatus) {
            if(nextAdsInterstitial()){
                activity.runOnUiThread(new Runnable() {
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
