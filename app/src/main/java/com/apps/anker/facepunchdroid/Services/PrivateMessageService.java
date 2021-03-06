package com.apps.anker.facepunchdroid.Services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.CookieManager;
import android.widget.Toast;

import com.apps.anker.facepunchdroid.MainActivity;
import com.apps.anker.facepunchdroid.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.hypertrack.smart_scheduler.Job;
import io.hypertrack.smart_scheduler.SmartScheduler;

/**
 * Created by Mikkel on 07-12-2016.
 */

public class PrivateMessageService extends Service {
    private NotificationManager mNM;

    private int NOTIFICATION = 1;

    private List<Integer> notifiedMessages = new ArrayList<>();

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        getUnreadMessages();

        SmartScheduler.JobScheduledCallback callback = new SmartScheduler.JobScheduledCallback() {
            @Override
            public void onJobScheduled(Context context, Job job) {
                getUnreadMessages();
            }
        };

        SmartScheduler jobScheduler = SmartScheduler.getInstance(getApplicationContext());

        // Get saved interval
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Integer interval = Integer.valueOf(sharedPref.getString("pm_check_interval", "900000") );

        Job.Builder builder = new Job.Builder(1, callback, Job.Type.JOB_TYPE_PERIODIC_TASK, "com.apps.anker.facepunchdroid.JobPeriodicTask")
                .setIntervalMillis(interval).setPeriodic(interval);

        Job job = builder.build();

        boolean result = jobScheduler.addJob(job);

        if (result) {
            Log.d("Job", "Job added! Interval: "+interval);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("PrivateMessageService", "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Tell the user we stopped.
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void getUnreadMessages() {
        final String bb_sessionhash = getCookie("https://facepunch.com/", "bb_sessionhash");
        final String bb_password = getCookie("https://facepunch.com/", "bb_password");
        final String bb_userid = getCookie("https://facepunch.com/", "bb_userid");

        new Thread(new Runnable() {
            public void run() {
                try {
                    Document doc = Jsoup.connect("https://facepunch.com/private.php")
                            .cookie("bb_sessionhash", bb_sessionhash)
                            .cookie("bb_password", bb_password)
                            .cookie("bb_userid", bb_userid)
                            .get();

                    Elements messages = doc.select("#pmfolderlist .blockrow:has(.unread)");

                    if(!messages.isEmpty()) {
                        Log.d("Service Messages", "You have unread messages");
                    } else {
                        Log.d("Service Messages", "You have NO unread messages");
                    }

                    for (Element message : messages) {
                        //Elements unreadmessages = message.children().select(".unread");

                        String subject = message.select(".unread").text();
                        String user = message.select(".commalist").text();
                        String messageUrl = message.select(".unread a").attr("href");
                        Integer pmid = Integer.parseInt( messageUrl.replaceAll("[^0-9]", "") );

                        if(!notifiedMessages.contains(pmid)) {


                            notifiedMessages.add(pmid);

                            Log.d("Service message User", message.select(".commalist").text());
                            Log.d("Service message Subject", message.select(".unread").text());
                            Log.d("Service message URL", messageUrl);
                            Log.d("Service message id", String.valueOf( pmid ));

                            // The PendingIntent to launch our activity if the user selects this notification
                            Intent intent = new Intent(PrivateMessageService.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            intent.putExtra("viewMessage", messageUrl);


                            PendingIntent contentIntent = PendingIntent.getActivity(PrivateMessageService.this, 0,
                                    intent, PendingIntent.FLAG_UPDATE_CURRENT);


                            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

                            // Get notification settings
                            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            Boolean shouldVibrate = sharedPref.getBoolean("pm_check_vibrate", true);
                            Boolean shouldPlaySound = sharedPref.getBoolean("pm_check_sound", true);
                            Boolean shouldUseLight = sharedPref.getBoolean("pm_check_light", true);

                            // Set the info for the views that show in the notification panel.
                            Notification.Builder notificationbuilder = new Notification.Builder(PrivateMessageService.this)
                                    .setSmallIcon(R.drawable.ic_stat_placeholder_trans)  // the status icon
                                    .setTicker("From: " + user)  // the status text
                                    .setWhen(System.currentTimeMillis())  // the time stamp
                                    .setContentTitle(subject)  // the label of the entry
                                    .setContentText("From: " + user)  // the contents of the entry
                                    .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                                    .setPriority(Notification.PRIORITY_DEFAULT)
                                    .setAutoCancel(true);

                            // set notification ligt
                            if(shouldUseLight) {
                                notificationbuilder.setLights(0xff00ff00, 1000, 1000);
                            }

                            Notification notification = notificationbuilder.build();

                            // Set notification sound
                            if(shouldPlaySound) {
                                notification.defaults |= Notification.DEFAULT_SOUND;
                            }

                            // Set notification vibration
                            if(shouldVibrate) {
                                notification.defaults |= Notification.DEFAULT_VIBRATE;
                            }

                            // Send the notification.
                            mNM.notify(pmid, notification);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public String getCookie(String siteName,String CookieName){
        String CookieValue = null;

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(siteName);
        String[] temp=cookies.split(";");
        for (String ar1 : temp ){
            if(ar1.contains(CookieName)){
                String[] temp1=ar1.split("=");
                CookieValue = temp1[1];
            }
        }
        return CookieValue;
    }
}
