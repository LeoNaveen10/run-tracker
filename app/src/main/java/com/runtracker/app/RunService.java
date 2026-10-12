package com.runtracker.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.os.*;
import org.json.*;

public class RunService extends Service implements LocationListener {
 private LocationManager manager;
 private long startedAt, segmentStart, accumulated;
 private double meters;
 private boolean paused,waiting;
 private int segment;
 private JSONArray route;
 private SharedPreferences prefs;
 private final Handler checkpoints=new Handler(Looper.getMainLooper());
 private final Runnable checkpoint=new Runnable() {
  @Override public void run() {
   if(startedAt!=0) { persist(prefs.getString("status","Finding GPS"));checkpoints.postDelayed(this,2000); }
  }
 };
 private final GpsFilter filter = new GpsFilter();
 private final GnssStatus.Callback satellites = new GnssStatus.Callback() {
  @Override public void onSatelliteStatusChanged(GnssStatus s) {
   int used=0; for(int i=0;i<s.getSatelliteCount();i++) if(s.usedInFix(i)) used++;
   prefs.edit().putInt("satellites",used).apply();
  }
 };
 @Override public void onCreate() {
  super.onCreate(); manager=(LocationManager)getSystemService(LOCATION_SERVICE); prefs=getSharedPreferences("run",0);
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
   new NotificationChannel("running","Active run",NotificationManager.IMPORTANCE_LOW));
 }
 @Override public int onStartCommand(Intent intent,int flags,int id) {
  String action=intent==null?"":intent.getAction();
  if("RECOVER".equals(action) && startedAt==0 && prefs.getLong("checkpoint_date",0)>0) {
   startedAt=prefs.getLong("checkpoint_date",0);accumulated=prefs.getLong("checkpoint_elapsed",0);
   segmentStart=SystemClock.elapsedRealtime();meters=prefs.getFloat("meters",0);paused=true;filter.reset();
   try { route=new JSONArray(prefs.getString("live_route","[]")); } catch(JSONException e) { route=new JSONArray(); }
   waiting=route.length()==0;
   segment=route.length()>0?route.optJSONObject(route.length()-1).optInt("segment",0)+1:0;
   prefs.edit().putLong("speed_fix_elapsed",0).apply();foreground();persist("Run recovered · paused until you resume");checkpoints.post(checkpoint);
  } else if("START".equals(action) && startedAt==0) {
   startedAt=System.currentTimeMillis(); segmentStart=SystemClock.elapsedRealtime();
   accumulated=0; meters=0; paused=false;waiting=true; filter.reset(); route=new JSONArray(); segment=0;
   prefs.edit().putString("live_route","[]").putLong("last_fix_elapsed",0).putLong("speed_fix_elapsed",0).putInt("satellites",0).apply();
   foreground(); subscribe();checkpoints.post(checkpoint);
  } else if("PAUSE".equals(action) && startedAt!=0 && !paused) {
   accumulated=elapsed();
   paused=true; filter.reset(); segment++; unsubscribe(); foreground(); persist("Paused · take a breath");
  } else if("RESUME".equals(action) && startedAt!=0 && paused) {
   paused=false; segmentStart=SystemClock.elapsedRealtime();
   prefs.edit().putLong("last_fix_elapsed",0).putLong("speed_fix_elapsed",0).apply(); foreground(); subscribe();
  } else if("FINISH".equals(action) && startedAt!=0) {
   long completed=elapsed();
   if(!saveRun(completed)) {
    if(!paused) { accumulated=completed; paused=true; unsubscribe(); foreground(); }
    persist("Could not save · free storage and retry"); return START_NOT_STICKY;
   }
   prefs.edit().putBoolean("active",false).putBoolean("paused",false).putLong("accumulated",completed).apply();
   startedAt=0; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
  }
  return START_NOT_STICKY;
 }
 private long elapsed() { return accumulated+(paused || waiting?0:SystemClock.elapsedRealtime()-segmentStart); }
 private void foreground() {
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,RunActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this,"running").setContentTitle(paused?"Run paused":"Recording your run")
   .setContentText("High-frequency GPS active · tap to view your route")
   .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(open).setOngoing(true).build();
  if(Build.VERSION.SDK_INT>=29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
  else startForeground(1,n);
 }
 private void subscribe() {
  persist("Finding GPS · get a clear view of the sky");
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
   persist("Precise location permission is needed"); return;
  }
  try {
   if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) persist("GPS is off · enable phone location");
   // Zero minimum time and distance: accept fixes at the receiver's native update rate.
   manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0L,0f,this);
   manager.registerGnssStatusCallback(satellites,new Handler(Looper.getMainLooper()));
  } catch(IllegalArgumentException|SecurityException e) { persist("GPS unavailable · check location settings"); }
 }
 private void unsubscribe() { manager.removeUpdates(this); manager.unregisterGnssStatusCallback(satellites); }
 private void persist(String status) {
  prefs.edit().putBoolean("active",startedAt!=0).putBoolean("paused",paused).putBoolean("waiting_gps",waiting).putLong("segment",segmentStart)
   .putLong("accumulated",accumulated).putFloat("meters",(float)meters).putString("status",status)
   .putLong("checkpoint_date",startedAt).putLong("checkpoint_elapsed",elapsed()).apply();
 }
 @Override public void onLocationChanged(Location l) {
  if(paused || startedAt==0 || l.getElapsedRealtimeNanos()<segmentStart*1000000L) return;
  long now=SystemClock.elapsedRealtime();
  if(now-l.getElapsedRealtimeNanos()/1000000L>5000L) return;
  long sampleTime=l.getElapsedRealtimeNanos()/1000000L;
  prefs.edit().putLong("last_fix_elapsed",sampleTime).putFloat("accuracy",l.hasAccuracy()?l.getAccuracy():999).apply();
  if(!l.hasAccuracy() || l.getAccuracy()>20) {
   prefs.edit().putLong("speed_fix_elapsed",0).apply();filter.reset(); segment++; persist("Weak GPS · wait for a clearer signal"); return;
  }
  double speed=l.hasSpeed() && (!l.hasSpeedAccuracy() || l.getSpeedAccuracyMetersPerSecond()<=1.5f)?l.getSpeed():Double.NaN;
  GpsFilter.Fix fix=filter.update(l.getLatitude(),l.getLongitude(),l.getAccuracy(),speed,l.getElapsedRealtimeNanos()/1000000L);
  if(fix.jump) { prefs.edit().putLong("speed_fix_elapsed",0).apply();persist("GPS jump filtered · keeping your distance accurate"); return; }
  if(waiting && fix.record) { waiting=false;segmentStart=now; }
  double liveSpeed=Double.isFinite(speed)?(speed<.35?0:speed):fix.speed;
  prefs.edit().putFloat("speed_kmh",(float)RunMath.speedKmh(liveSpeed)).putLong("speed_fix_elapsed",sampleTime).apply();
  if(fix.breakSegment) segment++;
  meters+=fix.distance;
  if(fix.record) {
   try {
    route.put(new JSONObject().put("latitude",l.getLatitude()).put("longitude",l.getLongitude())
     .put("time",l.getTime()).put("segment",segment));
    prefs.edit().putString("live_route",route.toString()).apply();
   } catch(JSONException ignored) { }
  }
  persist("GPS live · ±"+Math.round(l.getAccuracy())+" m");
 }
 private boolean saveRun(long completed) {
  try {
   JSONArray history=new JSONArray(prefs.getString("history","[]"));
   history.put(new JSONObject().put("date",startedAt).put("meters",meters).put("duration",completed).put("route",route));
   return prefs.edit().putString("history",history.toString()).putBoolean("active",false)
    .putBoolean("paused",false).putLong("accumulated",completed).remove("checkpoint_date").remove("checkpoint_elapsed").commit();
  } catch(JSONException e) { return false; }
 }
 @Override public void onProviderDisabled(String p) { filter.reset(); segment++; if(!paused) persist("GPS is off · enable phone location"); }
 @Override public void onProviderEnabled(String p) { if(!paused) persist("Finding GPS · get a clear view of the sky"); }
 @Override public void onStatusChanged(String p,int s,Bundle b) { }
 @Override public IBinder onBind(Intent i) { return null; }
 @Override public void onDestroy() { checkpoints.removeCallbacks(checkpoint);unsubscribe(); if(startedAt!=0) prefs.edit().putBoolean("active",false).apply(); super.onDestroy(); }
}
