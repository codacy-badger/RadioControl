package com.nikhilparanjape.radiocontrol.rootUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * Created by Nikhil Paranjape on 11/8/2015.
 */
public class WifiReceiver extends BroadcastReceiver {
    private static final String PRIVATE_PREF = "prefs";

    //Root commands which disable cell only
    String[] airCmd = {"su", "settings put global airplane_mode_radios  \"cell\"", "content update --uri content://settings/global --bind value:s:'cell' --where \"name='airplane_mode_radios'\"", "settings put global airplane_mode_on 1", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"};
    //runs command to disable airplane mode on wifi loss, while restoring previous airplane settings
    String[] airOffCmd2 = {"su", "settings put global airplane_mode_on 0", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false", "settings put global airplane_mode_radios  \"cell,bluetooth,nfc,wimax\"", "content update --uri content://settings/global --bind value:s:'cell,bluetooth,nfc,wimax' --where \"name='airplane_mode_radios'\""};

    Utilities util = new Utilities(); //Network and other related utilities

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);


        SharedPreferences sp = context.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences disabledPref = context.getSharedPreferences("disabled-networks", Context.MODE_PRIVATE);

        Set<String> h = new HashSet<>(Arrays.asList("")); //Set default set for SSID check
        Set<String> selections = prefs.getStringSet("ssid", h); //Gets stringset, if empty sets default
        boolean networkAlert= prefs.getBoolean("isNetworkAlive",false);


        //Check if user wants the app on
        if(sp.getInt("isActive",0) == 1){
            //Check if we just lost WiFi signal
            if(util.isConnectedWifi(context) == false){
                Log.d("RadioControl","WiFi signal LOST");
                writeLog("WiFi Signal lost",context);
                if(util.isAirplaneMode(context)){
                    RootAccess.runCommands(airOffCmd2);
                    Log.d("RadioControl","Airplane mode has been turned off");
                    writeLog("Airplane mode has been turned off",context);

                }
            }

            //If network is connected and airplane mode is off
            if (util.isConnectedWifi(context) && !util.isAirplaneMode(context)) {
                //boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI; //Boolean to check for an active WiFi connection
                //Check the list of disabled networks
                if(!disabledPref.contains(util.getCurrentSsid(context))){
                    Log.d("RadioControl",util.getCurrentSsid(context) + " was not found in the disabled list");
                    //Checks that user is not in call
                    if(!util.isCallActive(context)){
                        //Checks if the user doesnt' want network alerts
                        if(!networkAlert){
                            RootAccess.runCommands(airCmd);
                            Log.d("RadioControl", "Airplane mode has been turned on");
                            writeLog("Airplane mode has been turned on, SSID: " + util.getCurrentSsid(context),context);


                        }
                        //The user does want network alert notifications
                        else if(networkAlert){
                            new AsyncPingTask(context).execute("");
                        }

                    }
                    //Checks that user is currently in call and pauses execution till the call ends
                    else if(util.isCallActive(context)){
                        while(util.isCallActive(context)){
                            waitFor(1000);//Wait for call to end
                        }
                    }
                }
                //Pauses because WiFi network is in the list of disabled SSIDs
                else if(selections.contains(util.getCurrentSsid(context))){
                    Log.d("RadioControl",util.getCurrentSsid(context) + " was blocked from list " + selections);
                    writeLog(util.getCurrentSsid(context) + " was blocked from list " + selections,context);
                }
            }

        }
        if(sp.getInt("isActive",0) == 0){
            Log.d("RadioControl","RadioControl has been disabled");
            if(networkAlert){
                new AsyncPingTask(context).execute("");
            }
        }

    }
    public void writeLog(String data, Context c){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
        if(preferences.getBoolean("enableLogs", false)){
            try{
                String h = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString();
                File log = new File(c.getFilesDir(), "radiocontrol.log");
                if(!log.exists()) {
                    log.createNewFile();
                }
                String logPath = "radiocontrol.log";
                String string = "\n" + h + ": " + data;

                FileOutputStream fos = c.openFileOutput(logPath, Context.MODE_APPEND);
                fos.write(string.getBytes());
                fos.close();
            } catch(IOException e){
                Log.d("RadioControl", "There was an error saving the log: " + e);
            }
        }
    }

    private class AsyncPingTask extends AsyncTask<String, Void, Boolean> {
        Context context;

        public AsyncPingTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute(){
            try {
                //Wait for network to be connected fully
                while(!util.isConnected(context)){
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        protected Boolean doInBackground(String... params) {

            Runtime runtime = Runtime.getRuntime();
            try {
                Thread.sleep(1000);
                Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");//Send 1 packet to google and check if it came back
                int exitValue = ipProcess.waitFor();
                Log.d("RadioControl", "Ping test returned " + exitValue);
                return (exitValue == 0);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            SharedPreferences sp = context.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            boolean alertPriority = prefs.getBoolean("networkPriority", false);//Setting for network notifier
            boolean alertSounds = prefs.getBoolean("networkSound",false);
            boolean alertVibrate = prefs.getBoolean("networkVibrate",false);


            if(sp.getInt("isActive",0) == 0){
                //If the connection can't reach Google
                if(!result){
                    util.sendNote(context, "You are not connected to the internet",alertVibrate,alertSounds,alertPriority);
                    writeLog("Not connected to the internet",context);
                }
            }
            else if(sp.getInt("isActive",0) == 1){
                //If the connection can't reach Google
                if(!result){
                    util.sendNote(context, "You are not connected to the internet",alertVibrate,alertSounds,alertPriority);
                    writeLog("Not connected to the internet",context);
                }
                else{
                    RootAccess.runCommands(airCmd);
                    Log.d("RadioControl", "Airplane mode has been turned on");
                    writeLog("Airplane mode has been turned on, SSID: " + util.getCurrentSsid(context),context);
                }
            }


        }
    }
    public void waitFor(long timer){
        try {
            Thread.sleep(timer);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
};