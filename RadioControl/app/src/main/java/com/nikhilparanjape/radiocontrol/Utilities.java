package com.nikhilparanjape.radiocontrol;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;

import java.io.IOException;

/**
 * Created by Nikhil on 2/3/2016.
 */
public class Utilities {
    /*
 * isOnline - Check if there is a NetworkConnection
 * @return boolean
 */
    public static boolean isOnline() {
        Runtime runtime = Runtime.getRuntime();
        try {

            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int exitValue = ipProcess.waitFor();
            Log.d("RadioControl", "Val: " + exitValue);
            return (exitValue == 0);

        }
        catch (IOException e){ e.printStackTrace(); }
        catch (InterruptedException e) { e.printStackTrace(); }

        return false;
    }

    /**
     * gets network ssid
     * @param context
     * @return
     */
    public static String getCurrentSsid(Context context) {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            ssid = connectionInfo.getSSID();
            ssid = ssid.substring(1, ssid.length()-1);
        }
        return ssid;
    }

    /**
     * Checks link speed
     * @param c
     * @return
     */
    public static int linkSpeed(Context c){
        WifiManager wifiManager = (WifiManager)c.getSystemService(Context.WIFI_SERVICE);
        int linkSpeed = wifiManager.getConnectionInfo().getLinkSpeed();
        Log.d("RadioControl", "Link speed = " + linkSpeed + "Mbps");
        return linkSpeed;
    }

    /**
     * Makes a network alert
     * @param context
     * @return
     */
    public static void sendNote(Context context, String mes, boolean vibrate, boolean sound, boolean heads){
        int priority;
        if(!heads){
            priority = 0;
        }
        else if(heads){
            priority = 1;
        }
        else{
            priority = 1;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 1, new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK), 0);
        //Resources r = getResources();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_warning_black_48dp)
                .setContentTitle("RadioControl Network Alert")
                .setContentIntent(pi)
                .setContentText(mes)
                .setPriority(priority)
                .setAutoCancel(true)
                .build();

        if(vibrate){
            notification.defaults|= Notification.DEFAULT_VIBRATE;
        }
        if(sound){
            notification.defaults|= Notification.DEFAULT_SOUND;
        }


        notificationManager.notify(1, notification);

    }

    /**
     * Get the network info
     * @param context
     * @return
     */
    public static NetworkInfo getNetworkInfo(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    /**
     * Enable WiFi without root
     *@param context
     */
    public void enableWifi(Context context){
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);

    }

    /**
     * Disable WiFi without root
     *@param context
     */
    public void disableWifi(Context context){
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);

    }

    /**
     * Check if there is any connectivity to a Wifi network
     * @param context
     * @return
     */
    public static boolean isConnectedWifi(Context context){
        NetworkInfo info = getNetworkInfo(context);
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * Check if there is any connectivity
     * @param context
     * @return
     */
    public static boolean isConnected(Context context){
        NetworkInfo info = getNetworkInfo(context);
        return info != null && info.isConnectedOrConnecting();
    }

    /**
     * Check if there is any connectivity to a mobile network
     * @param context
     * @return
     */
    public static boolean isConnectedMobile(Context context){
        NetworkInfo info = getNetworkInfo(context);
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE);
    }

    /**
     * Check if there is any active call
     * @param context
     * @return
     */
    public boolean isCallActive(Context context){ AudioManager manager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(manager.getMode()== AudioManager.MODE_IN_CALL){
            return true;
        }
        else{
            return false;
        }
    }

    /**
     * Check if there is fast connectivity
     * @param context
     * @return
     */
    public static boolean isConnectedFast(Context context){
        NetworkInfo info = getNetworkInfo(context);
        return (info != null && info.isConnected() && isConnectionFast(info.getType(), info.getSubtype()));
    }

    public static boolean isAirplaneMode(Context context){
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static String getNetworkType(Context context){
        NetworkInfo info = getNetworkInfo(context);
        int type = info.getType();
        int subType = info.getSubtype();

        if(type==ConnectivityManager.TYPE_WIFI){
            return "WIFI";
        }else if(type == ConnectivityManager.TYPE_MOBILE){
            switch(subType){
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return "1xRTT"; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    return "CDMA"; // ~ 14-64 kbps
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return "EDGE"; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    return "EVDO_0"; // ~ 400-1000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    return "EVDO_A"; // ~ 600-1400 kbps
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return "GPRS"; // ~ 100 kbps
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    return "HSDPA"; // ~ 2-14 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    return "HSPA"; // ~ 700-1700 kbps
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    return "HSUPA"; // ~ 1-23 Mbps
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return "UMTS"; // ~ 400-7000 kbps
            /*
             * Above API level 7, make sure to set android:targetSdkVersion
             * to appropriate level to use these
             */
                case TelephonyManager.NETWORK_TYPE_EHRPD: // API level 11
                    return "EHRPD"; // ~ 1-2 Mbps
                case TelephonyManager.NETWORK_TYPE_EVDO_B: // API level 9
                    return "EVDO_B"; // ~ 5 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPAP: // API level 13
                    return "HSPAP"; // ~ 10-20 Mbps
                case TelephonyManager.NETWORK_TYPE_IDEN: // API level 8
                    return "IDEN"; // ~25 kbps
                case TelephonyManager.NETWORK_TYPE_LTE: // API level 11
                    return "LTE"; // ~ 10+ Mbps
                // Unknown
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                default:
                    return "UNKNOWN";
            }
        }else{
            return "UNKNOWN";
        }

    }

    /**
     * Check if the connection is fast
     * @param type
     * @param subType
     * @return
     */
    public static boolean isConnectionFast(int type, int subType){
        if(type==ConnectivityManager.TYPE_WIFI){
            return true;
        }else if(type == ConnectivityManager.TYPE_MOBILE){
            switch(subType){
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    return false; // ~ 14-64 kbps
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    return true; // ~ 400-1000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    return true; // ~ 600-1400 kbps
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return false; // ~ 100 kbps
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    return true; // ~ 2-14 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    return true; // ~ 700-1700 kbps
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    return true; // ~ 1-23 Mbps
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return true; // ~ 400-7000 kbps
            /*
             * Above API level 7, make sure to set android:targetSdkVersion
             * to appropriate level to use these
             */
                case TelephonyManager.NETWORK_TYPE_EHRPD: // API level 11
                    return true; // ~ 1-2 Mbps
                case TelephonyManager.NETWORK_TYPE_EVDO_B: // API level 9
                    return true; // ~ 5 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPAP: // API level 13
                    return true; // ~ 10-20 Mbps
                case TelephonyManager.NETWORK_TYPE_IDEN: // API level 8
                    return false; // ~25 kbps
                case TelephonyManager.NETWORK_TYPE_LTE: // API level 11
                    return true; // ~ 10+ Mbps
                // Unknown
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                default:
                    return false;
            }
        }else{
            return false;
        }
    }
}