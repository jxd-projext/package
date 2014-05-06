package com.android.deskclock.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
// Elink_liaobz SetAlarmRingtone 2014-4-14
public class SetAlarmRingtoneServiceReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.e("liaobz", "onReceive");
		try {
			Intent serviceIntent = new Intent(context,
					SetAlarmRingtoneService.class);
			serviceIntent.putExtra("alert", intent.getStringExtra("alert"));
			context.startService(serviceIntent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
