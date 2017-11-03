package com.example.sharadsingh.gpslogerdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.example.sharadsingh.gpslogerdemo.common.BundleConstants;
import com.example.sharadsingh.gpslogerdemo.common.PreferenceHelper;
import com.example.sharadsingh.gpslogerdemo.common.Session;
import com.example.sharadsingh.gpslogerdemo.common.events.CommandEvents;
import com.example.sharadsingh.gpslogerdemo.common.slf4j.SessionLogcatAppender;

import org.w3c.dom.Text;

import java.util.HashSet;
import java.util.Set;

import de.greenrobot.event.EventBus;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

public class MainActivity extends AppCompatActivity {
    private PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
    private Session session = Session.getInstance();
    private static Intent serviceIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadVersionSpecificProperties();
        setContentView(R.layout.activity_main);

        startAndBindService();

        if(preferenceHelper.shouldStartLoggingOnAppLaunch()){

            EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));
        }


        TextView textView = (TextView) findViewById(R.id.start);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLogging();
            }
        });
    }

    private void loadVersionSpecificProperties(){
        PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            int versionCode = packageInfo.versionCode;

            if( preferenceHelper.getLastVersionSeen() <= 71 ){

                //Specifically disable passive provider... just once
                if(preferenceHelper.getChosenListeners().contains(BundleConstants.PASSIVE)){
                    Set<String> listeners = new HashSet<>();
                    if(preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER)){ listeners.add(LocationManager.GPS_PROVIDER); }
                    if(preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)){ listeners.add(LocationManager.NETWORK_PROVIDER); }
                    preferenceHelper.setChosenListeners(listeners);
                }
            }

            if(preferenceHelper.getLastVersionSeen() <= 74){


                if(preferenceHelper.getMinimumAccuracy() == 0){
                    preferenceHelper.setMinimumAccuracy(40);
                }
            }

            if(preferenceHelper.getLastVersionSeen() <= 80){

                boolean usingCustomEmailProvider = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("autoemail_preset","") == "99";

                if(preferenceHelper.getCustomLoggingUrl().toLowerCase().contains("https") ||
                        preferenceHelper.getOwnCloudServerName().toLowerCase().contains("https") ||
                        preferenceHelper.getOpenGTSServerCommunicationMethod().toLowerCase().contains("https") ||
                        preferenceHelper.shouldFtpUseFtps() ||
                        preferenceHelper.getFtpProtocol().toLowerCase().contains("ssl") ||
                        preferenceHelper.getFtpProtocol().toLowerCase().contains("tls") ||
                        (preferenceHelper.isSmtpSsl() && usingCustomEmailProvider) ) {

                    new MaterialDialog.Builder(this)
                            .title("Using SSL Certificates?")
                            .negativeText(R.string.cancel)
                            .positiveText(R.string.faq_screen_title)
                            .content(Html.fromHtml("If you use a <strong>custom SSL certificate</strong> you will need to validate it with this app. Please see the FAQ for more information.<br /><br />If you don't know what an SSL certificate is you can probably cancel this message."))
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                                }
                            }).show();
                }

            }

            preferenceHelper.setLastVersionSeen(versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }
    private void startAndBindService() {
        serviceIntent = new Intent(this, GpsLoggingService.class);
        // Start the service in case it isn't already running
        startService(serviceIntent);
        // Now bind to service
        bindService(serviceIntent, gpsServiceConnection, Context.BIND_AUTO_CREATE);
        session.setBoundToService(true);
    }
    /**
     * Provides a connection to the GPS Logging Service
     */
    private final ServiceConnection gpsServiceConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {

            //loggingService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {

            //loggingService = ((GpsLoggingService.GpsLoggingBinder) service).getService();
        }
    };


    private void registerEventBus() {
        EventBus.getDefault().register(this);
    }

    private void unregisterEventBus(){
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }
    @Override
    protected void onDestroy() {
        stopAndUnbindServiceIfRequired();
        unregisterEventBus();
        super.onDestroy();

    }

    @Override
    protected void onStart() {
        super.onStart();
        startAndBindService();
    }



    @Override
    protected void onResume() {
        super.onResume();
        startAndBindService();

        if (session.hasDescription()) {
            setAnnotationReady();
        }


    }
    public void setAnnotationReady() {
        session.setAnnotationMarked(true);

    }

    @Override
    protected void onPause() {
        stopAndUnbindServiceIfRequired();
        super.onPause();
    }

    /**
     * Stops the service if it isn't logging. Also unbinds.
     */
    private void stopAndUnbindServiceIfRequired() {
        if (session.isBoundToService()) {

            try {
                unbindService(gpsServiceConnection);
                session.setBoundToService(false);
            } catch (Exception e) {

            }
        }

        if (!session.isStarted()) {

            try {
                stopService(serviceIntent);
            } catch (Exception e) {

            }
        }
    }



    public void toggleLogging() {

/*
        Intent serviceIntent = new Intent(this, GpsLoggingService.class);
        this.startService(serviceIntent);*/

       EventBus.getDefault().post(new CommandEvents.RequestToggle());
    }
}
