/*
 * Copyright (C) 2016 mendhak
 *
 * This file is part of GPSLogger for Android.
 *
 * GPSLogger for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GPSLogger for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.sharadsingh.gpslogerdemo;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.example.sharadsingh.gpslogerdemo.common.*;
import com.example.sharadsingh.gpslogerdemo.common.events.CommandEvents;
import com.example.sharadsingh.gpslogerdemo.common.events.ProfileEvents;
import com.example.sharadsingh.gpslogerdemo.common.events.ServiceEvents;
import com.example.sharadsingh.gpslogerdemo.common.slf4j.Logs;
import com.example.sharadsingh.gpslogerdemo.common.slf4j.SessionLogcatAppender;
import com.example.sharadsingh.gpslogerdemo.senders.AlarmReceiver;

import de.greenrobot.event.EventBus;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GpsLoggingService extends Service  {
    private static NotificationManager notificationManager;
    private static int NOTIFICATION_ID = 8675309;
    private final IBinder binder = new GpsLoggingBinder();
    AlarmManager nextPointAlarmManager;
    private NotificationCompat.Builder nfc = null;
    private static final Logger LOG = Logs.of(GpsLoggingService.class);
    // ---------------------------------------------------
    // Helpers and managers
    // ---------------------------------------------------
    private PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
    private Session session = Session.getInstance();
    protected LocationManager gpsLocationManager;
    private LocationManager passiveLocationManager;
    private LocationManager towerLocationManager;
    private GeneralLocationListener gpsLocationListener;
    private GeneralLocationListener towerLocationListener;
    private GeneralLocationListener passiveLocationListener;
    private Intent alarmIntent;
    private Handler handler = new Handler();

    PendingIntent activityRecognitionPendingIntent;
    GoogleApiClient googleApiClient;
    // ---------------------------------------------------


    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    @Override
    public void onCreate() {
        nextPointAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        registerEventBus();
    }

    private void requestActivityRecognitionUpdates() {
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnectionSuspended(int arg) {
                    }

                    @Override
                    public void onConnected(Bundle arg0) {
                        try {
                            LOG.debug("Requesting activity recognition updates");
                            Intent intent = new Intent(getApplicationContext(), GpsLoggingService.class);
                            activityRecognitionPendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, preferenceHelper.getMinimumLoggingInterval() * 200, activityRecognitionPendingIntent);
                        }
                        catch(Throwable t){
                            LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Can't connect to activity recognition service", t);
                        }
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult arg0) {

                    }
                });

        googleApiClient = builder.build();
        googleApiClient.connect();
    }

    private void stopActivityRecognitionUpdates(){
        try{
            LOG.debug("Stopping activity recognition updates");
            if(googleApiClient != null && googleApiClient.isConnected()){
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient, activityRecognitionPendingIntent);
                googleApiClient.disconnect();
            }
        }
        catch(Throwable t){
            LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Tried to stop activity recognition updates", t);
        }

    }

    private void registerEventBus() {
        EventBus.getDefault().registerSticky(this);
    }

    private void unregisterEventBus(){
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        handleIntent(intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "GpsLoggingService is being destroyed by Android OS.");
        unregisterEventBus();
        removeNotification();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        LOG.error("Android is low on memory!");
        super.onLowMemory();
    }

    private void handleIntent(Intent intent) {

        ActivityRecognitionResult arr = ActivityRecognitionResult.extractResult(intent);
        if(arr != null){
            EventBus.getDefault().post(new ServiceEvents.ActivityRecognitionEvent(arr));
            return;
        }

        if (intent != null) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                boolean needToStartGpsManager = false;

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_START)) {
                    LOG.info("Intent received - Start Logging Now");
                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));
                }

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_STOP)) {
                    LOG.info("Intent received - Stop logging now");
                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(false));
                }

                if (bundle.getBoolean(IntentConstants.GET_STATUS)) {
                    LOG.info("Intent received - Sending Status by broadcast");
                    EventBus.getDefault().postSticky(new CommandEvents.GetStatus());
                }


                if (bundle.getBoolean(IntentConstants.AUTOSEND_NOW)) {
                    LOG.info("Intent received - Send Email Now");
                    EventBus.getDefault().postSticky(new CommandEvents.AutoSend(null));
                }

                if (bundle.getBoolean(IntentConstants.GET_NEXT_POINT)) {
                    LOG.info("Intent received - Get Next Point");
                    needToStartGpsManager = true;
                }

                if (bundle.getString(IntentConstants.SET_DESCRIPTION) != null) {
                    LOG.info("Intent received - Set Next Point Description: " + bundle.getString(IntentConstants.SET_DESCRIPTION));
                    EventBus.getDefault().post(new CommandEvents.Annotate(bundle.getString(IntentConstants.SET_DESCRIPTION)));
                }

                if(bundle.getString(IntentConstants.SWITCH_PROFILE) != null){
                    LOG.info("Intent received - switch profile: " + bundle.getString(IntentConstants.SWITCH_PROFILE));
                    EventBus.getDefault().post(new ProfileEvents.SwitchToProfile(bundle.getString(IntentConstants.SWITCH_PROFILE)));
                }

                if (bundle.get(IntentConstants.PREFER_CELLTOWER) != null) {
                    boolean preferCellTower = bundle.getBoolean(IntentConstants.PREFER_CELLTOWER);
                    LOG.debug("Intent received - Set Prefer Cell Tower: " + String.valueOf(preferCellTower));

                    if(preferCellTower){
                        preferenceHelper.setChosenListeners(0);
                    } else {
                        preferenceHelper.setChosenListeners(1,2);
                    }

                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.TIME_BEFORE_LOGGING) != null) {
                    int timeBeforeLogging = bundle.getInt(IntentConstants.TIME_BEFORE_LOGGING);
                    LOG.debug("Intent received - logging interval: " + String.valueOf(timeBeforeLogging));
                    preferenceHelper.setMinimumLoggingInterval(timeBeforeLogging);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.DISTANCE_BEFORE_LOGGING) != null) {
                    int distanceBeforeLogging = bundle.getInt(IntentConstants.DISTANCE_BEFORE_LOGGING);
                    LOG.debug("Intent received - Set Distance Before Logging: " + String.valueOf(distanceBeforeLogging));
                    preferenceHelper.setMinimumDistanceInMeters(distanceBeforeLogging);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.GPS_ON_BETWEEN_FIX) != null) {
                    boolean keepBetweenFix = bundle.getBoolean(IntentConstants.GPS_ON_BETWEEN_FIX);
                    LOG.debug("Intent received - Set Keep Between Fix: " + String.valueOf(keepBetweenFix));
                    preferenceHelper.setShouldKeepGPSOnBetweenFixes(keepBetweenFix);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.RETRY_TIME) != null) {
                    int retryTime = bundle.getInt(IntentConstants.RETRY_TIME);
                    LOG.debug("Intent received - Set duration to match accuracy: " + String.valueOf(retryTime));
                    preferenceHelper.setLoggingRetryPeriod(retryTime);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.ABSOLUTE_TIMEOUT) != null) {
                    int absoluteTimeout = bundle.getInt(IntentConstants.ABSOLUTE_TIMEOUT);
                    LOG.debug("Intent received - Set absolute timeout: " + String.valueOf(absoluteTimeout));
                    preferenceHelper.setAbsoluteTimeoutForAcquiringPosition(absoluteTimeout);
                    needToStartGpsManager = true;
                }

                if(bundle.get(IntentConstants.LOG_ONCE) != null){
                    boolean logOnceIntent = bundle.getBoolean(IntentConstants.LOG_ONCE);
                    LOG.debug("Intent received - Log Once: " + String.valueOf(logOnceIntent));
                    needToStartGpsManager = false;
                    logOnce();
                }

                try {
                    if(bundle.get(Intent.EXTRA_ALARM_COUNT) != "0"){
                        needToStartGpsManager = true;
                    }
                }
                catch (Throwable t){
                    LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Received a weird EXTRA_ALARM_COUNT value. Cannot continue.");
                    needToStartGpsManager = false;
                }


                if (needToStartGpsManager && session.isStarted()) {
                    startGpsManager();
                }
            }
        } else {
            // A null intent is passed in if the service has been killed and restarted.
            LOG.debug("Service restarted with null intent. Were we logging previously - " + session.isStarted());
            if(session.isStarted()){
                startLogging();
            }

        }
    }

    /**
     * Sets up the auto email timers based on user preferences.
     */
    @TargetApi(23)
    public void setupAutoSendTimers() {
        LOG.debug("Setting up autosend timers. Auto Send Enabled - " + String.valueOf(preferenceHelper.isAutoSendEnabled())
                + ", Auto Send Delay - " + String.valueOf(session.getAutoSendDelay()));

        if (preferenceHelper.isAutoSendEnabled() && session.getAutoSendDelay() > 0) {
            long triggerTime = System.currentTimeMillis() + (long) (session.getAutoSendDelay() * 60 * 200);

            alarmIntent = new Intent(this, AlarmReceiver.class);
            cancelAlarm();

            PendingIntent sender = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if(Systems.isDozing(this)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, sender);
            }
            else {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, sender);
            }
            LOG.debug("Autosend alarm has been set");

        } else {
            if (alarmIntent != null) {
                LOG.debug("alarmIntent was null, canceling alarm");
                cancelAlarm();
            }
        }
    }


    public void logOnce() {
        session.setSinglePointMode(true);

        if (session.isStarted()) {
            startGpsManager();
        } else {
            startLogging();
        }
    }

    private void cancelAlarm() {
        if (alarmIntent != null) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            PendingIntent sender = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(sender);
        }
    }

    /**
     * Method to be called if user has chosen to auto email log files when he
     * stops logging
     */




    private void resetAutoSendTimersIfNecessary() {

        if (session.getAutoSendDelay() != preferenceHelper.getAutoSendInterval()) {
            session.setAutoSendDelay(preferenceHelper.getAutoSendInterval());
            setupAutoSendTimers();
        }
    }

    /**
     * Resets the form, resets file name if required, reobtains preferences
     */
    protected void startLogging() {
        LOG.debug(".");
        session.setAddNewTrackSegment(true);



        try {
            startForeground(NOTIFICATION_ID, new Notification());
        } catch (Exception ex) {
            LOG.error("Could not start GPSLoggingService in foreground. ", ex);
        }

        session.setStarted(true);

        resetAutoSendTimersIfNecessary();
        setupAutoSendTimers();

        notifyClientsStarted(true);
        startPassiveManager();
        startGpsManager();
        requestActivityRecognitionUpdates();

    }

    private void notifyByBroadcast(boolean loggingStarted) {
            LOG.debug("Sending a custom broadcast");
            String event = (loggingStarted) ? "started" : "stopped";
            Intent sendIntent = new Intent();
            sendIntent.setAction("com.mendhak.gpslogger.EVENT");
            sendIntent.putExtra("gpsloggerevent", event);
            sendIntent.putExtra("filename", session.getCurrentFormattedFileName());
            sendIntent.putExtra("startedtimestamp", session.getStartTimeStamp());
            sendBroadcast(sendIntent);
    }

    /**
     * Informs main activity and broadcast listeners whether logging has started/stopped
     */
    private void notifyClientsStarted(boolean started) {
        LOG.info((started)? getString(R.string.started) : getString(R.string.stopped));
        notifyByBroadcast(started);
        EventBus.getDefault().post(new ServiceEvents.LoggingStatus(started));
    }

    /**
     * Notify status of logger
     */
    private void notifyStatus(boolean started) {
        LOG.info((started)? getString(R.string.started) : getString(R.string.stopped));
        notifyByBroadcast(started);
    }

    /**
     * Stops logging, removes notification, stops GPS manager, stops email timer
     */
    public void stopLogging() {
        LOG.debug(".");
        session.setAddNewTrackSegment(true);
        session.setTotalTravelled(0);
        session.setPreviousLocationInfo(null);
        session.setStarted(false);
        session.setUserStillSinceTimeStamp(0);
        session.setLatestTimeStamp(0);
        stopAbsoluteTimer();
        cancelAlarm();
        session.setCurrentLocationInfo(null);
        session.setSinglePointMode(false);
        stopForeground(true);

        removeNotification();
        stopAlarm();
        stopGpsManager();
        stopPassiveManager();
        stopActivityRecognitionUpdates();
        notifyClientsStarted(false);
        session.setCurrentFileName("");
        session.setCurrentFormattedFileName("");
    }

    /**
     * Hides the notification icon in the status bar if it's visible.
     */
    private void removeNotification() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    /**
     * Shows a notification icon in the status bar for GPS Logger
     */

    @SuppressWarnings("ResourceType")
    private void startPassiveManager() {
        if(preferenceHelper.getChosenListeners().contains(LocationManager.PASSIVE_PROVIDER)){
            LOG.debug("Starting passive location listener");
            if(passiveLocationListener== null){
                passiveLocationListener = new GeneralLocationListener(this, BundleConstants.PASSIVE);
            }
            passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            passiveLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 200, 0, passiveLocationListener);
        }
    }

    /**
     * Starts the location manager. There are two location managers - GPS and
     * Cell Tower. This code determines which manager to request updates from
     * based on user preference and whichever is enabled. If GPS is enabled on
     * the phone, that is used. But if the user has also specified that they
     * prefer cell towers, then cell towers are used. If neither is enabled,
     * then nothing is requested.
     */
    @SuppressWarnings("ResourceType")
    private void startGpsManager() {

        //If the user has been still for more than the minimum seconds
        if(userHasBeenStillForTooLong()) {
            LOG.info("No movement detected in the past interval, will not log");
            setAlarmForNextPoint();
            return;
        }

        if (gpsLocationListener == null) {
            gpsLocationListener = new GeneralLocationListener(this, "GPS");
        }

        if (towerLocationListener == null) {
            towerLocationListener = new GeneralLocationListener(this, "CELL");
        }

        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        towerLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        checkTowerAndGpsStatus();

        if (session.isGpsEnabled() && preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER)) {
            LOG.info("Requesting GPS location updates");
            // gps satellite based
            gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, 0, gpsLocationListener);
            gpsLocationManager.addGpsStatusListener(gpsLocationListener);
            gpsLocationManager.addNmeaListener(gpsLocationListener);

            session.setUsingGps(true);
            startAbsoluteTimer();
        }

        if (session.isTowerEnabled() &&  ( preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)  || !session.isGpsEnabled() ) ) {
            LOG.info("Requesting cell and wifi location updates");
            session.setUsingGps(false);
            // Cell tower and wifi based
            towerLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 200, 0, towerLocationListener);

            startAbsoluteTimer();
        }

        if(!session.isTowerEnabled() && !session.isGpsEnabled()) {
            LOG.error("No provider available!");
            session.setUsingGps(false);
            LOG.error(getString(R.string.gpsprovider_unavailable));
            stopLogging();
            setLocationServiceUnavailable();
            return;
        }

        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(true));
        session.setWaitingForLocation(true);
    }

    private boolean userHasBeenStillForTooLong() {
        return !session.hasDescription() && !session.isSinglePointMode() &&
                (session.getUserStillSinceTimeStamp() > 0 && (System.currentTimeMillis() - session.getUserStillSinceTimeStamp()) > (preferenceHelper.getMinimumLoggingInterval() * 200));
    }

    private void startAbsoluteTimer() {
        if (preferenceHelper.getAbsoluteTimeoutForAcquiringPosition() >= 1) {
            handler.postDelayed(stopManagerRunnable, preferenceHelper.getAbsoluteTimeoutForAcquiringPosition() * 200);
        }
    }

    private Runnable stopManagerRunnable = new Runnable() {
        @Override
        public void run() {
            LOG.warn("Absolute timeout reached, giving up on this point");
            stopManagerAndResetAlarm();
        }
    };

    private void stopAbsoluteTimer() {
        handler.removeCallbacks(stopManagerRunnable);
    }

    /**
     * This method is called periodically to determine whether the cell tower /
     * gps providers have been enabled, and sets class level variables to those
     * values.
     */
    private void checkTowerAndGpsStatus() {
        session.setTowerEnabled(towerLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        session.setGpsEnabled(gpsLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }
    /**
     * Stops the location managers
     */
    @SuppressWarnings("ResourceType")
    private void stopGpsManager() {

        if (towerLocationListener != null) {
            LOG.debug("Removing towerLocationManager updates");
            towerLocationManager.removeUpdates(towerLocationListener);
        }

        if (gpsLocationListener != null) {
            LOG.debug("Removing gpsLocationManager updates");
            gpsLocationManager.removeUpdates(gpsLocationListener);
            gpsLocationManager.removeGpsStatusListener(gpsLocationListener);
        }

        session.setWaitingForLocation(false);
        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(false));

    }

    @SuppressWarnings("ResourceType")
    private void stopPassiveManager(){
        if(passiveLocationManager!=null){
            LOG.debug("Removing passiveLocationManager updates");
            passiveLocationManager.removeUpdates(passiveLocationListener);
        }
    }

    void setLocationServiceUnavailable(){
        EventBus.getDefault().post(new ServiceEvents.LocationServicesUnavailable());
    }

    /**
     * Stops location manager, then starts it.
     */
    void restartGpsManagers() {
        LOG.debug("Restarting location managers");
        stopGpsManager();
        startGpsManager();
    }

    /**
     * This event is raised when the GeneralLocationListener has a new location.
     * This method in turn updates notification, writes to file, reobtains
     * preferences, notifies main service client and resets location managers.
     *
     * @param loc Location object
     */
    void onLocationChanged(Location loc) {




        if (!session.isStarted()) {
            LOG.debug("onLocationChanged called, but session.isStarted is false");
            stopLogging();
            return;
        }

        long currentTimeStamp = System.currentTimeMillis();

        LOG.debug("Has description? " + session.hasDescription() + ", Single point? " + session.isSinglePointMode() + ", Last timestamp: " + session.getLatestTimeStamp());

        // Don't log a point until the user-defined time has elapsed
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() && !session.isSinglePointMode() && (currentTimeStamp - session.getLatestTimeStamp()) < (preferenceHelper.getMinimumLoggingInterval() * 200)) {
            return;
        }

        //Don't log a point if user has been still
        // However, if user has set an annotation, just log the point, disregard any filters
        if(userHasBeenStillForTooLong()) {
            LOG.info("Received location but the user hasn't moved, ignoring");
            return;
        }

        if(!isFromValidListener(loc)){
            return;
        }


        boolean isPassiveLocation = loc.getExtras().getBoolean(BundleConstants.PASSIVE);
        


        //Check if a ridiculous distance has been travelled since previous point - could be a bad GPS jump
        if(session.getCurrentLocationInfo() != null){
            double distanceTravelled = Maths.calculateDistance(loc.getLatitude(), loc.getLongitude(), session.getCurrentLocationInfo().getLatitude(), session.getCurrentLocationInfo().getLongitude());
            long timeDifference = Math.abs(loc.getTime() - session.getCurrentLocationInfo().getTime())/200;
            double speed = distanceTravelled/timeDifference;
            if(speed > 357){ //357 m/s ~=  1285 km/h
                LOG.warn(String.format("Very large jump detected - %d meters in %d sec - discarding point", (long)distanceTravelled, timeDifference));
                return;
            }
        }

        // Don't do anything until the user-defined accuracy is reached
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() &&  preferenceHelper.getMinimumAccuracy() > 0) {


            if(!loc.hasAccuracy() || loc.getAccuracy() == 0){
                return;
            }

            //Don't apply the retry interval to passive locations
            if (!isPassiveLocation && preferenceHelper.getMinimumAccuracy() < Math.abs(loc.getAccuracy())) {

                if(session.getFirstRetryTimeStamp() == 0){
                    session.setFirstRetryTimeStamp(System.currentTimeMillis());
                }

                if (currentTimeStamp - session.getFirstRetryTimeStamp() <= preferenceHelper.getLoggingRetryPeriod() * 200) {
                    LOG.warn("Only accuracy of " + String.valueOf(loc.getAccuracy()) + " m. Point discarded." + getString(R.string.inaccurate_point_discarded));
                    //return and keep trying
                    return;
                }

                if (currentTimeStamp - session.getFirstRetryTimeStamp() > preferenceHelper.getLoggingRetryPeriod() * 200) {
                    LOG.warn("Only accuracy of " + String.valueOf(loc.getAccuracy()) + " m and timeout reached." + getString(R.string.inaccurate_point_discarded));
                    //Give up for now
                    stopManagerAndResetAlarm();

                    //reset timestamp for next time.
                    session.setFirstRetryTimeStamp(0);
                    return;
                }

                //Success, reset timestamp for next time.
                session.setFirstRetryTimeStamp(0);
            }
        }

        //Don't do anything until the user-defined distance has been traversed
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() && !session.isSinglePointMode() && preferenceHelper.getMinimumDistanceInterval() > 0 && session.hasValidLocation()) {

            double distanceTraveled = Maths.calculateDistance(loc.getLatitude(), loc.getLongitude(),
                    session.getCurrentLatitude(), session.getCurrentLongitude());

            if (preferenceHelper.getMinimumDistanceInterval() > distanceTraveled) {

                stopManagerAndResetAlarm();
                return;
            }
        }
        LOG.info(SessionLogcatAppender.MARKER_LOCATION, String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude()));
        Log.d("SinghlocationChange?",String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude()));
        loc = Locations.getLocationWithAdjustedAltitude(loc, preferenceHelper);
        if(isConnectingToInternet()){
            gotoObjectWebCAll(String.valueOf(loc.getLatitude()),String.valueOf(loc.getLongitude()));
        }
        session.setLatestTimeStamp(System.currentTimeMillis());
        session.setFirstRetryTimeStamp(0);
        session.setCurrentLocationInfo(loc);
        setDistanceTraveled(loc);

        if(isPassiveLocation){
            LOG.debug("Logging passive location to file");
        }

        resetAutoSendTimersIfNecessary();
        stopManagerAndResetAlarm();

        EventBus.getDefault().post(new ServiceEvents.LocationUpdate(loc));

        if (session.isSinglePointMode()) {
            LOG.debug("Single point mode - stopping now");
            stopLogging();
        }
    }
    private void gotoObjectWebCAll(String lat, String longit) {
        String URL ="http://crm.truxapp.com/truxapiv2/transport/addPartnerCapturedLocation";
        final RequestQueue queue = Volley.newRequestQueue(this);
        queue.getCache().remove(URL);
        queue.getCache().clear();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("deviceId", "7838490077");
            jsonObject.put("mobileNumber","7838490077");
            jsonObject.put("vehicleLat", lat);
            jsonObject.put("vehicleLong", longit);
            Log.d("Before sucess",jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.POST, URL, jsonObject, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject resultJson) {
                String resultObj = resultJson.toString();
               Log.d("sucess",resultObj);
                try {
                    if (resultObj.equals("")) {
                        return;
                    }
                    JSONObject resultObject = new JSONObject(resultObj);
                    if (resultObject.getString("errorCode").equals("100")) {

                    } else {
                    }

                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

                System.out.println(error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError, NullPointerException {
                Map<String, String> params = new HashMap<>();
               /* if(token!=null)
                    params.put("authKey", token);*/
                return params;
            }
        };
        jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(300000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(jsObjRequest);
    }
    public boolean isConnectingToInternet() {
        ConnectivityManager connectivity = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null)
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
        }
        return false;
    }

    private boolean isFromValidListener(Location loc) {

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER) && !preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)){
            return true;
        }

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)){
            return loc.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER);
        }

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER)){
            return !loc.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER);
        }

        return true;
    }

    private void setDistanceTraveled(Location loc) {
        // Distance
        if (session.getPreviousLocationInfo() == null) {
            session.setPreviousLocationInfo(loc);
        }
        // Calculate this location and the previous location location and add to the current running total distance.
        // NOTE: Should be used in conjunction with 'distance required before logging' for more realistic values.
        double distance = Maths.calculateDistance(
                session.getPreviousLatitude(),
                session.getPreviousLongitude(),
                loc.getLatitude(),
                loc.getLongitude());
        session.setPreviousLocationInfo(loc);
        session.setTotalTravelled(session.getTotalTravelled() + distance);
    }

    protected void stopManagerAndResetAlarm() {
        if (!preferenceHelper.shouldKeepGPSOnBetweenFixes()) {
            stopGpsManager();
        }

        stopAbsoluteTimer();
        setAlarmForNextPoint();
    }


    private void stopAlarm() {
        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);
    }

    @TargetApi(23)
    private void setAlarmForNextPoint() {
        LOG.debug("Set alarm for " + preferenceHelper.getMinimumLoggingInterval() + " seconds");

        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);

        if(Systems.isDozing(this)){
            //Only invoked once per 15 minutes in doze mode
            LOG.warn("Device is dozing, using infrequent alarm");
            nextPointAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + preferenceHelper.getMinimumLoggingInterval() * 200, pi);
        }
        else {
            nextPointAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + preferenceHelper.getMinimumLoggingInterval() * 200, pi);
        }
    }

    /**
     * Informs the main service client of the number of visible satellites.
     *
     * @param count Number of Satellites
     */
    void setSatelliteInfo(int count) {
        session.setVisibleSatelliteCount(count);
        EventBus.getDefault().post(new ServiceEvents.SatellitesVisible(count));
    }


    /**
     * Can be used from calling classes as the go-between for methods and
     * properties.
     */
    public class GpsLoggingBinder extends Binder {
        public GpsLoggingService getService() {
            return GpsLoggingService.this;
        }
    }


    @EventBusHook
    public void onEvent(CommandEvents.RequestToggle requestToggle){
        if (session.isStarted()) {
            stopLogging();
        } else {
            startLogging();
        }
    }

    @EventBusHook
    public void onEvent(CommandEvents.RequestStartStop startStop){
        if(startStop.start){
            startLogging();
        }
        else {
            stopLogging();
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.RequestStartStop.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.GetStatus getStatus){
        notifyStatus(session.isStarted());
    }

    @EventBusHook
    public void onEvent(CommandEvents.AutoSend autoSend){


        EventBus.getDefault().removeStickyEvent(CommandEvents.AutoSend.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.Annotate annotate){
        final String desc = annotate.annotation;
        if (desc.length() == 0) {
            LOG.debug("Clearing annotation");
            session.clearDescription();
        } else {
            LOG.debug("Pending annotation: " + desc);
            session.setDescription(desc);
            EventBus.getDefault().post(new ServiceEvents.AnnotationStatus(false));

            if(session.isStarted()){
                startGpsManager();
            }
            else {
                logOnce();
            }
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.Annotate.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.LogOnce logOnce){
        logOnce();
    }

    @EventBusHook
    public void onEvent(ServiceEvents.ActivityRecognitionEvent activityRecognitionEvent){

        session.setLatestDetectedActivity(activityRecognitionEvent.result.getMostProbableActivity());

        if(!preferenceHelper.shouldNotLogIfUserIsStill()){
            session.setUserStillSinceTimeStamp(0);
            return;
        }

        if(activityRecognitionEvent.result.getMostProbableActivity().getType() == DetectedActivity.STILL){
            LOG.debug(activityRecognitionEvent.result.getMostProbableActivity().toString());
            if(session.getUserStillSinceTimeStamp() == 0){
                LOG.debug("Just entered still state, attempt to log");
                startGpsManager();
                session.setUserStillSinceTimeStamp(System.currentTimeMillis());
            }

        }
        else {
            LOG.debug(activityRecognitionEvent.result.getMostProbableActivity().toString());
            //Reset the still-since timestamp
            session.setUserStillSinceTimeStamp(0);
            LOG.debug("Just exited still state, attempt to log");
            startGpsManager();
        }
    }



}
