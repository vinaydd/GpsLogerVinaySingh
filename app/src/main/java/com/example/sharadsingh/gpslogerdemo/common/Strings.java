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


import android.content.Context;
import android.os.Build;

import com.google.android.gms.location.DetectedActivity;


import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strings {
    /**
     * Converts seconds into friendly, understandable description of the duration.
     *
     * @param numberOfSeconds
     * @return
     */

    /**
     * Converts given bearing degrees into a rough cardinal direction that's
     * more understandable to humans.
     *
     * @param bearingDegrees
     * @return
     */


    /**
     * Makes string safe for writing to XML file. Removes lt and gt. Best used
     * when writing to file.
     *
     * @param desc
     * @return
     */
    public static String cleanDescriptionForXml(String desc) {
        desc = desc.replace("<", "");
        desc = desc.replace(">", "");
        desc = desc.replace("&", "&amp;");
        desc = desc.replace("\"", "&quot;");

        return desc;
    }

    public static String cleanDescriptionForJson(String desc){
        desc = desc.replace("\"", "");
        desc = desc.replace("\\","");
        return desc;
    }

    /**
     * Given a Date object, returns an ISO 8601 date time string in UTC.
     * Example: 2010-03-23T05:17:22Z but not 2010-03-23T05:17:22+04:00
     *
     * @param dateToFormat The Date object to format.
     * @return The ISO 8601 formatted string.
     */
    public static String getIsoDateTime(Date dateToFormat) {
        /**
         * This function is used in gpslogger.loggers.* and for most of them the
         * default locale should be fine, but in the case of HttpUrlLogger we
         * want machine-readable output, thus  Locale.US.
         *
         * Be wary of the default locale
         * http://developer.android.com/reference/java/util/Locale.html#default_locale
         */

        // GPX specs say that time given should be in UTC, no local time.
        SimpleDateFormat sdf = new SimpleDateFormat(getIsoDateTimeFormat(),
                Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        return sdf.format(dateToFormat);
    }

    public static String getIsoDateTimeFormat() {
        return "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    }

    public static String getReadableDateTime(Date dateToFormat) {
        /**
         * Similar to getIsoDateTime(), this function is used in
         * AutoEmailManager, and we want machine-readable output.
         */
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm",
                Locale.US);
        return sdf.format(dateToFormat);
    }

    /**
     * Checks if a string is null or empty
     *
     * @param text
     * @return
     */
    public static boolean isNullOrEmpty(String text) {
        return text == null ||  text.trim().length() == 0;
    }

    public static String htmlDecode(String text) {
        if (isNullOrEmpty(text)) {
            return text;
        }

        return text.replace("&amp;", "&").replace("&quot;", "\"");
    }

    public static String getBuildSerial() {
        try {
            return Build.SERIAL;
        } catch (Throwable t) {
            return "";
        }
    }
    public static int toInt(String number, int defaultVal) {
        try {
            return Integer.parseInt(number);
        } catch (Exception e) {
            return defaultVal;
        }
    }



    public static String getSanitizedMarkdownForFaqView(String md){
        if(Strings.isNullOrEmpty(md)){ return "";}

        //         \[[^\]]+\]\(((?!http)[^\)]+)\)
        //         Matches any Markdown link that isn't starting with http...
        String output = md;

        Matcher imgMatcher = Pattern.compile("(!\\[[^\\]]+\\]\\((?!http)[^\\)]+\\))", Pattern.MULTILINE).matcher(md);
        while(imgMatcher.find()){
            String group = imgMatcher.group(1);
            output = output.replace(group,"");
        }

        Matcher linkMatcher = Pattern.compile("\\[[^\\]]+\\]\\(((?!http)[^\\)]+)\\)", Pattern.MULTILINE).matcher(md);
        while(linkMatcher.find()){
            String group = linkMatcher.group(1);
            output = output.replace(group,"http://code.mendhak.com/gpslogger/"+group);
        }

        return output;
    }

    public static String getDegreesMinutesSeconds(double decimaldegrees, boolean isLatitude) {
        String cardinality = (decimaldegrees<0) ? "S":"N";

        if(!isLatitude){
            cardinality = (decimaldegrees<0) ? "W":"E";
        }

        //Remove negative sign
        decimaldegrees = Math.abs(decimaldegrees);

        int deg =  (int) Math.floor(decimaldegrees);
        double minfloat = (decimaldegrees-deg)*60;
        int min = (int) Math.floor(minfloat);
        double secfloat = (minfloat-min)*60;
        double sec = Math.round(secfloat * 10000.0)/10000.0;

        // After rounding, the seconds might become 60. These two
        // if-tests are not necessary if no rounding is done.
        if (sec==60) {
            min++;
            sec=0;
        }
        if (min==60) {
            deg++;
            min=0;
        }


        return ("" + deg + "° " + min + "' " + sec + "\" " + cardinality);
    }

    public static String getDegreesDecimalMinutes(double decimaldegrees, boolean isLatitude) {
        String cardinality = (decimaldegrees<0) ? "S":"N";

        if(!isLatitude){
            cardinality = (decimaldegrees<0) ? "W":"E";
        }

        //Remove negative sign
        decimaldegrees = Math.abs(decimaldegrees);

        int deg =  (int) Math.floor(decimaldegrees);
        double minfloat = (decimaldegrees-deg)*60;
        double min = Math.round(minfloat*10000.0)/10000.0;

        return ("" + deg + "° " + min + "' " + cardinality);
    }

    public static String getDecimalDegrees(double decimaldegrees) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(6);
        return nf.format(decimaldegrees);
    }

    public static String getFormattedLatitude(double decimaldegrees){
        return getFormattedDegrees(decimaldegrees, true, PreferenceHelper.getInstance());
    }

    public static String getFormattedLongitude(double decimaldegrees){
        return getFormattedDegrees(decimaldegrees, false, PreferenceHelper.getInstance());
    }

    static String getFormattedDegrees(double decimaldegrees, boolean isLatitude, PreferenceHelper ph){
        switch(ph.getDisplayLatLongFormat()){

            case DEGREES_MINUTES_SECONDS:
                return getDegreesMinutesSeconds(decimaldegrees, isLatitude);

            case DEGREES_DECIMAL_MINUTES:
                return getDegreesDecimalMinutes(decimaldegrees, isLatitude);

            case DECIMAL_DEGREES:
            default:
                return getDecimalDegrees(decimaldegrees);

        }
    }

    public static  String getDetectedActivityName(DetectedActivity detectedActivity) {

        if(detectedActivity == null){
            return "";
        }

        switch(detectedActivity.getType()) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.UNKNOWN:
            case 6:
            default:
                return "UNKNOWN";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.WALKING:
                return "WALKING";
            case DetectedActivity.RUNNING:
                return "RUNNING";
        }
    }

}
