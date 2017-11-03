/*
 * Copyright (C) 2016 mendhak
 *
 * This file is part of gpslogger.
 *
 * gpslogger is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * gpslogger is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with gpslogger.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.sharadsingh.gpslogerdemo.common.network;


import android.content.Context;
import android.os.Handler;
import android.provider.MediaStore;


import com.example.sharadsingh.gpslogerdemo.common.slf4j.Logs;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class Networks {

    private static final Logger LOG = Logs.of(Networks.class);

    static String LOCAL_TRUSTSTORE_FILENAME = "knownservers.bks";
    static String LOCAL_TRUSTSTORE_PASSWORD = "politelemon";

    public static KeyStore getKnownServersStore(Context context)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {

        KeyStore mKnownServersStore = KeyStore.getInstance(KeyStore.getDefaultType());

        return mKnownServersStore;
    }



    public static CertificateValidationException extractCertificateValidationException(Exception e) {

        if (e == null) { return null ; }

        CertificateValidationException result = null;

        if (e instanceof CertificateValidationException) {
            return (CertificateValidationException)e;
        }
        Throwable cause = e.getCause();
        Throwable previousCause = null;
        while (cause != null && cause != previousCause && !(cause instanceof CertificateValidationException)) {
            previousCause = cause;
            cause = cause.getCause();
        }
        if (cause != null && cause instanceof CertificateValidationException) {
            result = (CertificateValidationException)cause;
        }
        return result;
    }

    public static SSLSocketFactory getSocketFactory(Context context){
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            LocalX509TrustManager atm = null;

            atm = new LocalX509TrustManager(getKnownServersStore(context));

            TrustManager[] tms = new TrustManager[] { atm };
            sslContext.init(null, tms, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            LOG.error("Could not get SSL Socket factory ", e);
        }

        return null;
    }



    public static TrustManager getTrustManager(Context context)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CertStoreException {
        return new LocalX509TrustManager(getKnownServersStore(context));
    }
}
