package com.android.deskclock.provider;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
// Elink_liaobz SetAlarmRingtone 2014-4-14
public class SetAlarmRingtoneService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e("liaobz", "onStartCommand");
		String alert = intent.getStringExtra("alert");
		ContentValues updateValues = new ContentValues();
		Log.e("liaobz", "onStartCommand:" + alert);
		updateValues.put("ringtone", alert);
        ContentResolver resolver = getContentResolver();

		Cursor mCursor = resolver.query(ClockContract.AlarmsColumns.CONTENT_URI,
				 new String[] {"_id"} , null, null, null);

		while (mCursor.moveToNext()) {
			Log.e("liaobz", "while:" + mCursor.getInt(0));
	        resolver.update(ContentUris.withAppendedId(
	                ClockContract.AlarmsColumns.CONTENT_URI,
	                mCursor.getInt(0)),
	        		updateValues, null, null);
		}

		return super.onStartCommand(intent, flags, startId);
	}

}
