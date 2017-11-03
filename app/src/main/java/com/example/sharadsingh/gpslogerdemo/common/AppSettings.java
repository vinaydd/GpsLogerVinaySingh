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

package com.example.sharadsingh.gpslogerdemo.common;

import android.app.Application;


import com.example.sharadsingh.gpslogerdemo.BuildConfig;
import com.example.sharadsingh.gpslogerdemo.common.slf4j.Logs;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.log.CustomLogger;

import org.slf4j.Logger;

import de.greenrobot.event.EventBus;

public class AppSettings extends Application {

    private static JobManager jobManager;
    private static AppSettings instance;
    private static Logger LOG;


    @Override
    public void onCreate() {
        super.onCreate();

        //Configure the slf4j logger
        Logs.configure();
        LOG = Logs.of(this.getClass());
        LOG.debug("Log4J configured");

        //Configure the Event Bus
        EventBus.builder().logNoSubscriberMessages(false).sendNoSubscriberEvent(false).installDefaultEventBus();
        LOG.debug("EventBus configured");

        //Configure the Job Queue
        Configuration config = new Configuration.Builder(getInstance())
                .networkUtil(new WifiNetworkUtil(getInstance()))
                .consumerKeepAlive(60)
                .minConsumerCount(0)
                .maxConsumerCount(1)
                .customLogger(jobQueueLogger)
                .build();
        jobManager = new JobManager(this, config);
        LOG.debug("Job Queue configured");
    }

    /**
     * Returns a configured Job Queue Manager
     */
    public static JobManager getJobManager() {
        return jobManager;
    }

    public AppSettings() {
        instance = this;
    }

    /**
     * Returns a singleton instance of this class
     */
    public static AppSettings getInstance() {
        return instance;
    }


    private final CustomLogger jobQueueLogger = new CustomLogger() {
        @Override
        public boolean isDebugEnabled() {
            return BuildConfig.DEBUG;
        }

        @Override
        public void d(String text, Object... args) {

            LOG.debug(String.format(text, args));
        }

        @Override
        public void e(Throwable t, String text, Object... args) {
            LOG.error(String.format(text, args), t);
        }

        @Override
        public void e(String text, Object... args) {

            LOG.error(String.format(text, args));
        }
    };



}
