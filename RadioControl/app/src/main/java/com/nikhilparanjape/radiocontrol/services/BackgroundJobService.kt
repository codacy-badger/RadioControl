package com.nikhilparanjape.radiocontrol.services

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import com.nikhilparanjape.radiocontrol.receivers.ConnectivityReceiver
import com.nikhilparanjape.radiocontrol.utilities.AlarmSchedulers
import com.nikhilparanjape.radiocontrol.utilities.Utilities
import com.topjohnwu.superuser.Shell
import org.jetbrains.anko.doAsync
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.*
/**
 * This service starts the BackgroundAirplaneService as a foreground service if on Android Oreo or higher.
 *
 *
 *
 * @author Nikhil Paranjape
 */
class BackgroundJobService : JobService(), ConnectivityReceiver.ConnectivityReceiverListener {

    internal var util = Utilities() //Network and other related utilities
    private var alarmUtil = AlarmSchedulers()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JobScheduler created")
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "Job started")
        //Utilities.scheduleJob(applicationContext) // reschedule the job

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {})
        } else {
            Log.d("RadioControl-Job", "Test")
        }
        val activeNetwork = connectivityManager.activeNetworkInfo
        Log.d("RadioControl-Job", "Active: $activeNetwork")

        val context = applicationContext
        val sp = applicationContext.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val disabledPref = applicationContext.getSharedPreferences("disabled-networks", Context.MODE_PRIVATE)

        val h = HashSet(listOf("")) //Set default empty set for SSID check
        val selections = prefs.getStringSet("ssid", h) //Gets stringset, if empty sets default
        val networkAlert = prefs.getBoolean("isNetworkAlive", false)

        //Check if user wants the app on
        if (sp.getInt("isActive", 0) == 0) {
            Log.d("RadioControl-Job", "RadioControl has been disabled-job")
            if (networkAlert) {
                pingTask()
            }
            //Adds wifi signal lost log for nonrooters
            if (!Utilities.isConnectedWifi(applicationContext)) {
                Log.d("RadioControl-Job", "WiFi signal LOST")
                writeLog("WiFi Signal lost", applicationContext)
            }
        } else if(sp.getInt("isActive", 0) == 1) {
            Log.d("RadioControl-Job", "Begin the program has")
            //Check if we just lost WiFi signal
            if (!Utilities.isConnectedWifi(applicationContext) && activeNetwork == null) {
                Log.d("RadioControl-Job", "WiFi signal LOST")
                writeLog("WiFi Signal lost", applicationContext)
                // Ensures that Airplane mode is on, or the cell radio is off
                if (Utilities.isAirplaneMode(applicationContext) || !Utilities.isConnectedMobile(applicationContext)) {
                    //Checks that user is not in call
                    if (!util.isCallActive(applicationContext)) {
                        //Runs the alternate root command
                        if (prefs.getBoolean("altRootCommand", true)) {
                            if (Utilities.getCellStatus(applicationContext) == 1) {
                                val output = Shell.su("service call phone 27").exec().out
                                Utilities.writeLog("root accessed: $output", applicationContext)
                                Log.d("RadioControl-Job", "Cell Radio has been turned on")
                                writeLog("Cell radio has been turned on", applicationContext)
                            }
                        } else {
                            val output = Shell.su("settings put global airplane_mode_on 0", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false").exec().out
                            Utilities.writeLog("root accessed: $output", applicationContext)
                            //RootAccess.runCommands(airOffCmd3)
                            Log.d("RadioControl-Job", "Airplane mode has been turned off")
                            writeLog("Airplane mode has been turned off", applicationContext)

                        }
                    } else if (util.isCallActive(applicationContext)) {
                        while (util.isCallActive(applicationContext)) {
                            waitFor(1000)//Wait for call to end
                            Log.d("RadioControl-Job", "waiting for call to end")
                        }
                        //Utilities.scheduleJob(context)
                    }//Checks that user is currently in call and pauses execution till the call ends
                }
                //if (Utilities.isConnectedWifi(context) && !Utilities.isAirplaneMode(context) || Utilities.isConnectedMobile(context))
            } else if (Utilities.isConnectedWifi(applicationContext) && !Utilities.isAirplaneMode(applicationContext)) {
                //boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI; //Boolean to check for an active WiFi connection
                Log.d("RadioControl-Job", "WiFi signal got")
                //Check the list of disabled networks
                if (!disabledPref.contains(Utilities.getCurrentSsid(applicationContext))) {
                    Log.d("RadioControl-Job", "The current SSID was not found in the disabled list")
                    //Checks that user is not in call
                    if (!util.isCallActive(applicationContext)) {
                        //Checks if the user doesn't want network alerts
                        if (!networkAlert) {
                            //Runs the alternate root command
                            if (prefs.getBoolean("altRootCommand", false)) {

                                if (Utilities.getCellStatus(applicationContext) == 0) {
                                    val output = Shell.su("service call phone 27").exec().out
                                    Utilities.writeLog("root accessed: $output", applicationContext)
                                    Log.d("RadioControl-Job", "Cell Radio has been turned off")
                                    writeLog("Cell radio has been turned off", applicationContext)
                                } else if (Utilities.getCellStatus(applicationContext) == 1) {
                                    Log.d("RadioControl-Job", "Cell Radio is already off")
                                }

                            } else {
                                val output = Shell.su("settings put global airplane_mode_radios  \"cell\"", "content update --uri content://settings/global --bind value:s:'cell' --where \"name='airplane_mode_radios'\"", "settings put global airplane_mode_on 1", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true").exec().out
                                Utilities.writeLog("root accessed: $output", applicationContext)
                                //RootAccess.runCommands(airCmd)
                                Log.d("RadioControl-Job", "Airplane mode has been turned on")
                                writeLog("Airplane mode has been turned on", applicationContext)
                            }

                        } else {
                            pingTask()
                        }//The user does want network alert notifications

                    } else if (util.isCallActive(applicationContext)) {
                        while (util.isCallActive(applicationContext)) {
                            waitFor(1000)//Wait for call to end
                            Log.d("RadioControl-Job", "waiting for call to end")
                        }
                    }//Checks that user is currently in call and pauses execution till the call ends
                } else if (selections!!.contains(Utilities.getCurrentSsid(applicationContext))) {
                    Log.d("RadioControl-Job", "The current SSID was blocked from list $selections")
                    writeLog("The current SSID was blocked from list $selections", applicationContext)
                }//Pauses because WiFi network is in the list of disabled SSIDs
            } else {
                if (activeNetwork!!.isConnected) {
                    Log.d("RadioControl-Job", "Yeah, we connected")
                } else {
                    Log.d("RadioControl-Job", "EGADS")
                }
            }

        } else {
            Log.d("RadioControl-Job", "Something's wrong, I can feel it")
        }

        return true
    }

    private fun writeLog(data: String, c: Context) {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(c)
        if (preferences.getBoolean("enableLogs", false)) {
            try {
                val h = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString()
                val log = File(c.filesDir, "radiocontrol.log")
                if (!log.exists()) {
                    log.createNewFile()
                }
                val logPath = "radiocontrol.log"
                val string = "\n$h: $data"

                val fos = c.openFileOutput(logPath, Context.MODE_APPEND)
                fos.write(string.toByteArray())
                fos.close()
            } catch (e: IOException) {
                Log.d("RadioControl-Job", "There was an error saving the log: $e")
            }

        }
    }

    private fun pingTask() {
        doAsync {
            try {
                //Wait for network to be connected fully
                while (!Utilities.isConnected(applicationContext)) {
                    Thread.sleep(1000)
                }
                val address = InetAddress.getByName("1.1.1.1")
                val reachable = address.isReachable(4000)
                Log.d("RadioControl-Job", "Reachable?: $reachable")

                val sp = applicationContext.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE)
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)

                val alertPriority = prefs.getBoolean("networkPriority", false)//Setting for network notifier
                val alertSounds = prefs.getBoolean("networkSound", false)
                val alertVibrate = prefs.getBoolean("networkVibrate", false)


                if (sp.getInt("isActive", 0) == 0) {
                    //If the connection can't reach Google
                    if (!reachable) {
                        Utilities.sendNote(applicationContext, applicationContext.getString(com.nikhilparanjape.radiocontrol.R.string.not_connected_alert), alertVibrate, alertSounds, alertPriority)
                        writeLog("Not connected to the internet", applicationContext)
                    }
                } else if (sp.getInt("isActive", 0) == 1) {
                    //If the connection can't reach Google
                    if (!reachable) {
                        Utilities.sendNote(applicationContext, applicationContext.getString(com.nikhilparanjape.radiocontrol.R.string.not_connected_alert), alertVibrate, alertSounds, alertPriority)
                        writeLog("Not connected to the internet", applicationContext)
                    } else {
                        //Runs the alternate root command
                        if (prefs.getBoolean("altRootCommand", false)) {
                            val output = Shell.su("service call phone 27").exec().out
                            Utilities.writeLog("root accessed: $output", applicationContext)
                            alarmUtil.scheduleRootAlarm(applicationContext)
                            Log.d("RadioControl-Job", "Cell Radio has been turned off")
                            writeLog("Cell radio has been turned off", applicationContext)
                        } else if (!prefs.getBoolean("altRootCommand", false)) {
                            val output = Shell.su("settings put global airplane_mode_radios  \"cell\"", "content update --uri content://settings/global --bind value:s:'cell' --where \"name='airplane_mode_radios'\"", "settings put global airplane_mode_on 1", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true").exec().out
                            Utilities.writeLog("root accessed: $output", applicationContext)
                            //RootAccess.runCommands(airCmd)
                            Log.d("RadioControl-Job", "Airplane mode has been turned on")
                            writeLog("Airplane mode has been turned on", applicationContext)
                        }
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

    }

    private fun waitFor(timer: Long) {
        try {
            Thread.sleep(timer)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    companion object {

        private const val TAG = "SyncService"
        private const val PRIVATE_PREF = "prefs"
    }

}
