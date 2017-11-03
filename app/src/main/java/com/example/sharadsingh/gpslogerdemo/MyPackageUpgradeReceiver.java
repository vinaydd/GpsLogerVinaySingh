/*
 * Copyright (C) 2017 mendhak
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


import com.example.sharadsingh.gpslogerdemo.common.Session;
import com.example.sharadsingh.gpslogerdemo.common.events.CommandEvents;
import com.example.sharadsingh.gpslogerdemo.common.slf4j.Logs;

import org.slf4j.Logger;

import de.greenrobot.event.EventBus;

public class MyPackageUpgradeReceiver extends BroadcastReceiver {

    private static final Logger LOG = Logs.of(MyPackageUpgradeReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean shouldResumeLogging = Session.getInstance().isStarted();
            LOG.debug("Package has been replaced. Should resume logging: " + shouldResumeLogging);

            if(shouldResumeLogging){
                EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));

                Intent serviceIntent = new Intent(context, GpsLoggingService.class);
                context.startService(serviceIntent);
            }
        } catch (Exception ex) {
            LOG.error("Package upgrade receiver", ex);
        }
    }
}
