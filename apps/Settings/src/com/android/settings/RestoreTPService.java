package com.android.settings;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RestoreTPService extends Service {
	// private static final String TAG = "RestoreTPService";

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

        SharedPreferences prefs = PreferenceManager
        	.getDefaultSharedPreferences(this);
        try {
            File file = new File("/sys/elink/tp_alsps_en");
            FileWriter writer = new FileWriter(file);
            if(prefs.getBoolean("tpproximity", false)){
                writer.write("en");
            } else {
                writer.write("no");
            }
            writer.flush();
            writer.close(); 
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
	}

}
