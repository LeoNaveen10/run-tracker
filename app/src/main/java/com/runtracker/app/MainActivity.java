package com.runtracker.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
 static final int BG=Color.rgb(13,18,26), CARD=Color.rgb(24,32,44), WHITE=Color.rgb(243,247,247), MUTED=Color.rgb(143,158,178), LIME=Color.rgb(205,252,133);
 private LinearLayout content,history,runActions;
 private TextView distance,duration,pace,status,gpsMeta,session,weekly,mapLabel;
 private Button start,pause,finish;
 private RouteMapView map;
 private SharedPreferences prefs;
 private String lastHistory="",lastRoute="";
 private boolean openingRun;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable update=new Runnable() {
  @Override public void run() { refresh();handler.postDelayed(this,500); }
 };
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);prefs=getSharedPreferences("run",0);
  if(prefs.getBoolean("active",false) || prefs.getLong("checkpoint_date",0)>0) {
   boolean running=false;
   ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
   for(ActivityManager.RunningServiceInfo service:am.getRunningServices(Integer.MAX_VALUE))
    if(RunService.class.getName().equals(service.service.getClassName())) running=true;
   if(!running) {
    prefs.edit().putBoolean("active",false).apply();
    boolean recover=prefs.getLong("checkpoint_date",0)>0;
    if(recover && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) command("RECOVER");
    new AlertDialog.Builder(this).setTitle(recover?"Run recovered":"Tracking was interrupted")
     .setMessage(recover?"Your last saved progress has been recovered and paused. Tap Resume when you're ready. Time while the app was stopped is not counted.":"The previous app version could not recover unfinished runs. This update saves progress during recording.")
     .setPositiveButton("OK",null).show();
   }
  }
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setClipToPadding(false);
  content=column();content.setPadding(dp(20),dp(18),dp(20),dp(28));scroll.addView(content);setContentView(scroll);
  if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener((v,insets)-> {
   Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
   scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
  });
  LinearLayout brand=row();brand.setGravity(Gravity.CENTER_VERTICAL);
  TextView logo=label("↗",26,BG,true);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(LIME,12));
  brand.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
  LinearLayout name=column();name.setPadding(dp(12),0,0,0);
  name.addView(label("RUN TRACKER",14,WHITE,true));name.addView(label("OUTDOOR RUNNING",9,MUTED,true));
  brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView settings=label("⚙",26,MUTED,false);settings.setContentDescription("GPS and battery settings");
  settings.setPadding(dp(10),dp(8),dp(4),dp(8));settings.setOnClickListener(v->settings());brand.addView(settings);content.addView(brand);
  gap(content,26);
  TextView heading=label("Find your stride.",30,WHITE,true);heading.setLetterSpacing(-.035f);content.addView(heading);
  content.addView(label("Fresh air. Clear mind. Your pace.",13,MUTED,false));gap(content,20);
  LinearLayout hero=column();hero.setPadding(dp(22),dp(20),dp(22),dp(21));
  GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,49,61),Color.rgb(23,32,45)});
  gradient.setCornerRadius(dp(24));gradient.setStroke(dp(1),Color.rgb(49,65,71));hero.setBackground(gradient);content.addView(hero);
  LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
  session=label("READY TO RUN",10,LIME,true);session.setLetterSpacing(.15f);top.addView(session,new LinearLayout.LayoutParams(0,-2,1));
  TextView gps=label("◉  GPS",10,MUTED,true);top.addView(gps);hero.addView(top);gap(hero,14);
  distance=label("0.000 km",56,WHITE,true);distance.setLetterSpacing(-.05f);distance.setSingleLine(true);distance.setFontFeatureSettings("tnum");hero.addView(distance);
  TextView distanceLabel=label("DISTANCE",10,MUTED,true);distanceLabel.setLetterSpacing(.14f);hero.addView(distanceLabel);gap(hero,22);
  LinearLayout metrics=row();
  LinearLayout timeColumn=column();duration=label("00:00:00",23,WHITE,true);duration.setFontFeatureSettings("tnum");timeColumn.addView(duration);timeColumn.addView(label("RUN TIME",10,MUTED,true));
  LinearLayout paceColumn=column();pace=label("— /km",23,WHITE,true);paceColumn.addView(pace);paceColumn.addView(label("AVG PACE",10,MUTED,true));
  metrics.addView(timeColumn,new LinearLayout.LayoutParams(0,-2,1));
  View divider=new View(this);divider.setBackgroundColor(Color.rgb(61,74,86));LinearLayout.LayoutParams div=new LinearLayout.LayoutParams(dp(1),dp(40));div.setMargins(dp(8),dp(3),dp(22),0);metrics.addView(divider,div);
  metrics.addView(paceColumn,new LinearLayout.LayoutParams(0,-2,1));hero.addView(metrics);gap(hero,19);
  status=label("Go outdoors for the best GPS signal",12,MUTED,false);hero.addView(status);
  gpsMeta=label("Fastest available GPS · no distance delay",10,MUTED,false);hero.addView(gpsMeta);gap(content,16);
  start=action("▶   START RUN",LIME,BG,v->begin());content.addView(start,new LinearLayout.LayoutParams(-1,dp(56)));
  runActions=row();runActions.setVisibility(View.GONE);pause=action("Ⅱ   PAUSE",LIME,BG,v->command(prefs.getBoolean("paused",false)?"RESUME":"PAUSE"));
  finish=action("■   FINISH",Color.rgb(40,49,64),WHITE,v->new AlertDialog.Builder(this)
   .setTitle("Nice work. Save your run?").setMessage("Your route, distance and pace will be saved to your history.")
   .setPositiveButton("Save run",(d,w)->command("FINISH")).setNegativeButton("Keep running",null).show());
  LinearLayout.LayoutParams control=new LinearLayout.LayoutParams(0,dp(56),1);control.setMargins(0,0,dp(10),0);runActions.addView(pause,control);
  runActions.addView(finish,new LinearLayout.LayoutParams(0,dp(56),1));content.addView(runActions);
  gap(content,22);
  LinearLayout routeHeader=row();routeHeader.setGravity(Gravity.CENTER_VERTICAL);mapLabel=label("Your route",18,WHITE,true);routeHeader.addView(mapLabel,new LinearLayout.LayoutParams(0,-2,1));
  TextView recenter=label("↗  FIT ROUTE",10,LIME,true);recenter.setPadding(dp(8),dp(10),0,dp(10));recenter.setOnClickListener(v->map.fit());routeHeader.addView(recenter);content.addView(routeHeader);gap(content,9);
  FrameLayout mapCard=new FrameLayout(this);mapCard.setBackground(shape(CARD,20));mapCard.setClipToOutline(true);
  map=new RouteMapView(this);mapCard.addView(map,new FrameLayout.LayoutParams(-1,-1));content.addView(mapCard,new LinearLayout.LayoutParams(-1,dp(218)));
  LinearLayout mapFooter=row();mapFooter.setGravity(Gravity.CENTER_VERTICAL);
  TextView attribution=label("© OpenStreetMap contributors",9,MUTED,false);attribution.setPadding(0,dp(8),0,dp(8));
  attribution.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));
  mapFooter.addView(attribution,new LinearLayout.LayoutParams(0,-2,1));
  TextView zoomOut=label("−",24,WHITE,true);zoomOut.setPadding(dp(14),0,dp(14),0);zoomOut.setContentDescription("Zoom out");zoomOut.setOnClickListener(v->map.changeZoom(-1));mapFooter.addView(zoomOut);
  TextView zoomIn=label("+",24,WHITE,true);zoomIn.setPadding(dp(14),0,0,0);zoomIn.setContentDescription("Zoom in");zoomIn.setOnClickListener(v->map.changeZoom(1));mapFooter.addView(zoomIn);content.addView(mapFooter);
  gap(content,20);
  LinearLayout historyHeader=row();historyHeader.addView(label("Recent runs",18,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
  weekly=label("0.0 km this week",11,LIME,false);historyHeader.addView(weekly);content.addView(historyHeader);gap(content,12);
  history=column();content.addView(history);gap(content,16);
  TextView foot=label("OUTDOOR ONLY  ·  RUNS SAVED ON YOUR PHONE",9,MUTED,true);foot.setGravity(Gravity.CENTER);content.addView(foot);
 }
 private void settings() {
  new AlertDialog.Builder(this).setTitle("Run settings").setItems(new String[]{"Location / GPS settings","Allow background battery use","About GPS & maps"},(d,index)-> {
   if(index==0) startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
   else if(index==1) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
   else new AlertDialog.Builder(this).setTitle("Better GPS, better runs")
    .setMessage("Grant precise location and start outdoors with a clear sky view. The app requests every GPS fix the phone provides, usually about once per second. Satellites cannot measure each footstep.\n\nWeak fixes, stationary drift and impossible jumps are filtered. Start after the GPS indicator says Live. Maps need internet for street tiles; GPS recording works offline. Map tile requests go to OpenStreetMap. Full run history stays on your phone.")
    .setPositiveButton("Got it",null).show();
  }).show();
 }
 private void begin() {
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
   requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1);return;
  }
  LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
  if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
   new AlertDialog.Builder(this).setTitle("Turn on GPS first").setMessage("Enable phone location, then start your run outdoors.")
    .setPositiveButton("Location settings",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Cancel",null).show();return;
  }
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
   requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);return;
  }
  chooseTarget();
 }
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
  super.onRequestPermissionsResult(code,permissions,results);
  if(code==1 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) begin();
  else if(code==2) chooseTarget();
  else Toast.makeText(this,"Precise location is needed to record your route",Toast.LENGTH_LONG).show();
 }
 private void command(String action) {
  Intent intent=new Intent(this,RunService.class).setAction(action);
  if("START".equals(action)) intent.putExtra("target_km",(double)prefs.getFloat("last_target_km",5));
  if("START".equals(action) || "RECOVER".equals(action)) startForegroundService(intent); else startService(intent);
  handler.postDelayed(()->refresh(),200);
 }
 private void chooseTarget() {
  if(prefs.getLong("checkpoint_date",0)>0) { command("RECOVER");return; }
  LinearLayout layout=column();layout.setPadding(dp(22),dp(10),dp(22),dp(10));
  layout.addView(label("Choose your distance: 5–10 km",14,WHITE,false));
  EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  input.setText(GoalProgress.number(prefs.getFloat("last_target_km",5)));input.setSelectAllOnFocus(true);input.setHint("Target in km");input.setContentDescription("Run target in kilometres, between 5 and 10");layout.addView(input);
  LinearLayout presets=row();
  presets.addView(action("5 km",CARD,LIME,v->input.setText("5")),new LinearLayout.LayoutParams(0,dp(48),1));
  presets.addView(action("10 km",CARD,LIME,v->input.setText("10")),new LinearLayout.LayoutParams(0,dp(48),1));layout.addView(presets);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("What's your target?").setView(layout).setPositiveButton("Start run",null).setNegativeButton("Cancel",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
   double goal;try { goal=Double.parseDouble(input.getText().toString().trim()); } catch(NumberFormatException error) { input.setError("Enter a target from 5 to 10 km");return; }
   if(!GoalProgress.validTarget(goal)) { input.setError("Enter a target from 5 to 10 km");return; }
   goal=Math.round(goal*100)/100.0;
   prefs.edit().putFloat("last_target_km",(float)goal).apply();dialog.dismiss();command("START");
  }));dialog.show();
 }
 private void refresh() {
  boolean active=prefs.getBoolean("active",false),isPaused=prefs.getBoolean("paused",false);
  if(active) {
   if(!openingRun) { openingRun=true;startActivity(new Intent(this,RunActivity.class)); }
   return;
  }
  String data=prefs.getString("history","[]");JSONObject latest=null;
  try { JSONArray runs=new JSONArray(data);if(runs.length()>0) latest=runs.getJSONObject(runs.length()-1); } catch(JSONException ignored) { }
  long elapsed=prefs.getLong("accumulated",0);
  if(active && !isPaused && !prefs.getBoolean("waiting_gps",false)) elapsed+=Math.max(0,SystemClock.elapsedRealtime()-prefs.getLong("segment",SystemClock.elapsedRealtime()));
  float meters=prefs.getFloat("meters",0);
  elapsed=latest==null?0:latest.optLong("duration",0);meters=latest==null?0:(float)latest.optDouble("meters",0);
  String amount=String.format(Locale.US,"%.2f",meters/1000.0);
  SpannableString value=new SpannableString(amount+" km");
  value.setSpan(new RelativeSizeSpan(.32f),amount.length(),value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  value.setSpan(new ForegroundColorSpan(MUTED),amount.length(),value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);distance.setText(value);
  duration.setText(clock(elapsed));pace.setText(pace(elapsed,meters)+" /km");
  session.setText(active?(isPaused?"ON A BREATHER":prefs.getBoolean("waiting_gps",false)?"ACQUIRING GPS":"●  RECORDING"):prefs.getString("history","[]").equals("[]")?"READY TO RUN":"LAST RUN");
  start.setVisibility(active?View.GONE:View.VISIBLE);runActions.setVisibility(active?View.VISIBLE:View.GONE);pause.setText(isPaused?"▶   RESUME":"Ⅱ   PAUSE");
  if(active) {
   long last=prefs.getLong("last_fix_elapsed",0),age=last==0?Long.MAX_VALUE:Math.max(0,(SystemClock.elapsedRealtime()-last)/1000);
   status.setText(isPaused?prefs.getString("status","Paused"):age>5 && last!=0?"GPS signal lost · finding a fresh fix":prefs.getString("status","Finding GPS"));
   status.setTextColor(isPaused?MUTED:age>5 || prefs.getFloat("accuracy",999)>20?Color.rgb(251,198,112):LIME);
   gpsMeta.setText(isPaused?"Tracking and run time are paused":prefs.getInt("satellites",0)+" satellites · "+(last==0?"awaiting first fix":age<1?"updated just now":"updated "+age+"s ago"));
  } else { status.setText("Go outdoors for the best GPS signal");status.setTextColor(MUTED);gpsMeta.setText("Fastest available GPS · distance shown to two decimals"); }
  if(!lastHistory.equals(data)) { lastHistory=data;renderHistory(data); }
  String points=latest==null || latest.optJSONArray("route")==null?"[]":latest.optJSONArray("route").toString();
  String marker=(active?"live:":"saved:")+points;
  if(!lastRoute.equals(marker)) {
   lastRoute=marker;try { map.setRoute(new JSONArray(points),active); } catch(JSONException ignored) { }
  }
  mapLabel.setText(active?"Live route":points.equals("[]")?"Your route":"Last route");
 }
 private void renderHistory(String data) {
  history.removeAllViews();
  try {
   JSONArray runs=new JSONArray(data);double weekMeters=0;
   Calendar week=Calendar.getInstance();week.setFirstDayOfWeek(Calendar.MONDAY);week.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);week.set(Calendar.HOUR_OF_DAY,0);week.set(Calendar.MINUTE,0);week.set(Calendar.SECOND,0);week.set(Calendar.MILLISECOND,0);
   for(int i=0;i<runs.length();i++) if(runs.getJSONObject(i).getLong("date")>=week.getTimeInMillis()) weekMeters+=runs.getJSONObject(i).getDouble("meters");
   weekly.setText(String.format(Locale.US,"%.1f km this week",weekMeters/1000));
   if(runs.length()==0) {
    LinearLayout empty=column();empty.setPadding(dp(18),dp(18),dp(18),dp(18));empty.setBackground(shape(CARD,18));
    empty.addView(label("A fresh start.",16,WHITE,true));empty.addView(label("Your first run belongs here. Lace up.",12,MUTED,false));history.addView(empty);
   }
   for(int i=runs.length()-1;i>=0;i--) {
    final JSONObject run=runs.getJSONObject(i);LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(CARD,18));
    TextView icon=label("↗",25,LIME,true);icon.setPadding(0,0,dp(14),0);card.addView(icon);
    LinearLayout info=column();info.addView(label(new SimpleDateFormat("EEE, d MMM · HH:mm",Locale.getDefault()).format(new Date(run.getLong("date"))),12,WHITE,true));
    info.addView(label(clock(run.getLong("duration"))+"  ·  "+pace(run.getLong("duration"),run.getDouble("meters"))+" /km",11,MUTED,false));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
    card.addView(label(String.format(Locale.US,"%.2f km",run.getDouble("meters")/1000),16,LIME,true));card.setOnClickListener(v->showRun(run));
    TextView more=label("⋮",24,MUTED,true);more.setGravity(Gravity.CENTER);more.setContentDescription("Run options: view or delete");
    more.setOnClickListener(v-> {
     PopupMenu menu=new PopupMenu(this,more);menu.getMenu().add(0,1,0,"View details");menu.getMenu().add(0,2,1,"Delete run");
     menu.setOnMenuItemClickListener(item-> { if(item.getItemId()==2) confirmDelete(run);else showRun(run);return true; });menu.show();
    });card.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
    history.addView(card);gap(history,8);
   }
  } catch(JSONException e) { history.addView(label("Unable to read run history",13,MUTED,false)); }
 }
 private void showRun(JSONObject run) {
  LinearLayout layout=column();layout.setPadding(dp(18),dp(12),dp(18),dp(16));layout.setBackgroundColor(BG);
  layout.addView(label(String.format(Locale.US,"%.2f km",run.optDouble("meters")/1000),32,LIME,true));
  layout.addView(label(new SimpleDateFormat("EEE, d MMM yyyy · HH:mm",Locale.getDefault()).format(new Date(run.optLong("date"))),12,MUTED,false));
  layout.addView(label(clock(run.optLong("duration"))+"  ·  "+pace(run.optLong("duration"),run.optDouble("meters"))+" /km",14,WHITE,false));gap(layout,12);
  RouteMapView saved=new RouteMapView(this);JSONArray points=run.optJSONArray("route");saved.setRoute(points==null?new JSONArray():points,false);
  saved.showCloseUp();
  layout.addView(saved,new LinearLayout.LayoutParams(-1,dp(270)));
  LinearLayout mapTools=row();
  mapTools.addView(action("−",CARD,WHITE,v->saved.changeZoom(-1)),new LinearLayout.LayoutParams(0,dp(48),1));
  mapTools.addView(action("Fit route",CARD,LIME,v->saved.fit()),new LinearLayout.LayoutParams(0,dp(48),2));
  mapTools.addView(action("+",CARD,WHITE,v->saved.changeZoom(1)),new LinearLayout.LayoutParams(0,dp(48),1));layout.addView(mapTools);
  double goal=run.optDouble("targetKm",0);if(goal>0) layout.addView(label("Target: "+GoalProgress.number(goal)+" km",12,LIME,false));
  layout.addView(label("© OpenStreetMap contributors · pinch to zoom",10,MUTED,false));
  layout.addView(action("Share route & metrics",LIME,BG,v->ShareCard.share(this,run,saved)),new LinearLayout.LayoutParams(-1,dp(48)));
  gap(layout,16);layout.addView(label("Kilometre splits",18,WHITE,true));
  JSONArray splits=run.optJSONArray("splits");
  if(splits==null || splits.length()==0) layout.addView(label(splits==null?"Splits are available for new runs from this update.":"No complete kilometre recorded yet.",12,MUTED,false));
  else {
   long fastest=Long.MAX_VALUE;int fastestIndex=-1;
   for(int i=0;i<splits.length();i++) {
    JSONObject split=splits.optJSONObject(i);if(split==null) continue;long duration=split.optLong("duration",-1);
    if(duration>=0 && duration<fastest) { fastest=duration;fastestIndex=i; }
   }
   for(int i=0;i<splits.length();i++) {
    JSONObject split=splits.optJSONObject(i);if(split==null) continue;long duration=split.optLong("duration",-1);
    String value=duration<0?"Not recorded":RunMath.pace(duration,1000)+" min/km";
    layout.addView(label(split.optInt("kilometre")+" km  ·  "+value+(i==fastestIndex?"  ·  FASTEST":""),14,i==fastestIndex?LIME:WHITE,false));
   }
  }
  ScrollView scroller=new ScrollView(this);scroller.addView(layout);
  new AlertDialog.Builder(this).setTitle("Run details").setView(scroller).setPositiveButton("Done",null)
   .setNegativeButton("Delete run",(d,w)->confirmDelete(run)).show();
 }
 private void confirmDelete(JSONObject selected) {
  new AlertDialog.Builder(this).setTitle("Delete this run?")
   .setMessage("This removes the saved run and its route from this phone. This cannot be undone.")
   .setPositiveButton("Delete",(dialog,which)-> {
    try {
     String original=prefs.getString("history","[]");JSONArray current=new JSONArray(original);
     if(!RunHistory.remove(current,selected)) { Toast.makeText(this,"This run is no longer in your history",Toast.LENGTH_SHORT).show();return; }
     String oldRoute=prefs.getString("live_route","[]");float oldMeters=prefs.getFloat("meters",0);long oldTime=prefs.getLong("accumulated",0);
     SharedPreferences.Editor edit=prefs.edit().putString("history",current.toString());
     boolean idle=!prefs.getBoolean("active",false) && prefs.getLong("checkpoint_date",0)==0;
     if(idle) {
      JSONObject latest=current.length()==0?null:current.optJSONObject(current.length()-1);
      edit.putString("live_route",latest==null || latest.optJSONArray("route")==null?"[]":latest.optJSONArray("route").toString())
       .putFloat("meters",latest==null?0:(float)latest.optDouble("meters",0)).putLong("accumulated",latest==null?0:latest.optLong("duration",0));
     }
     if(!edit.commit()) {
      SharedPreferences.Editor restore=prefs.edit().putString("history",original);
      if(idle) restore.putString("live_route",oldRoute).putFloat("meters",oldMeters).putLong("accumulated",oldTime);
      restore.apply();
      Toast.makeText(this,"Could not save the deletion. Check available storage.",Toast.LENGTH_LONG).show();return;
     }
     refresh();Toast.makeText(this,"Run deleted",Toast.LENGTH_SHORT).show();
    } catch(JSONException error) { Toast.makeText(this,"Could not read run history; nothing was deleted",Toast.LENGTH_LONG).show(); }
   }).setNegativeButton("Cancel",null).show();
 }
 static String clock(long ms) { return RunMath.clock(ms); }
 static String pace(long ms,double m) { return RunMath.pace(ms,m); }
 private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
 private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v; }
 private TextView label(String text,int size,int color,boolean bold) {
  TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);
  v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setPadding(0,dp(3),0,dp(3));return v;
 }
 private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
 private Button action(String text,int color,int foreground,View.OnClickListener click) {
  Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));b.setTextColor(foreground);b.setLetterSpacing(.055f);
  b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(45,255,255,255)),shape(color,18),null));b.setOnClickListener(click);return b;
 }
 private int dp(int size) { return Math.round(size*getResources().getDisplayMetrics().density); }
 private void gap(LinearLayout target,int size) { View v=new View(this);target.addView(v,new LinearLayout.LayoutParams(1,dp(size))); }
 @Override protected void onResume() { super.onResume();openingRun=false;handler.post(update); }
 @Override protected void onPause() { handler.removeCallbacks(update);super.onPause(); }
}
