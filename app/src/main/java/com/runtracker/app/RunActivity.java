package com.runtracker.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** Dedicated recording page: distance, current pace, elapsed time and the route. */
public class RunActivity extends Activity {
 private TextView distance,currentPace,time,status;
 private Button pause;
 private RouteMapView map;
 private SharedPreferences prefs;
 private String lastRoute="";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable() {
  @Override public void run() { refresh();if(!isFinishing()) handler.postDelayed(this,500); }
 };
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);prefs=getSharedPreferences("run",0);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  LinearLayout root=column();root.setBackgroundColor(MainActivity.BG);
  root.setPadding(dp(20),dp(12),dp(20),dp(16));setContentView(root);
  if(Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)-> {
   Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
   root.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(16)+bars.bottom);return insets;
  });
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=label("YOUR RUN",13,MainActivity.WHITE);title.setLetterSpacing(.12f);
  header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button fit=button("◎",MainActivity.CARD,MainActivity.LIME,v->map.fit());fit.setContentDescription("Fit the entire route on the map");
  header.addView(fit,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
  status=label("Finding GPS…",12,MainActivity.MUTED);status.setPadding(0,dp(8),0,dp(8));root.addView(status);
  LinearLayout stats=column();stats.setGravity(Gravity.CENTER);stats.setPadding(dp(18),dp(14),dp(18),dp(18));stats.setBackground(shape(MainActivity.CARD,22));
  distance=label("0.000",58,MainActivity.LIME);distance.setGravity(Gravity.CENTER);distance.setFontFeatureSettings("tnum");stats.addView(distance,new LinearLayout.LayoutParams(-1,-2));
  TextView km=label("DISTANCE · KM",10,MainActivity.MUTED);km.setGravity(Gravity.CENTER);km.setLetterSpacing(.13f);stats.addView(km);
  LinearLayout lower=new LinearLayout(this);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(16);stats.addView(lower,rowParams);
  LinearLayout paceCell=column();paceCell.setGravity(Gravity.CENTER);currentPace=metric("—",30);paceCell.addView(currentPace,new LinearLayout.LayoutParams(-1,dp(42)));paceCell.addView(label("CURRENT PACE · MIN/KM",10,MainActivity.MUTED));
  LinearLayout timeCell=column();timeCell.setGravity(Gravity.CENTER);time=metric("00:00:00",26);timeCell.addView(time,new LinearLayout.LayoutParams(-1,dp(42)));timeCell.addView(label("RUN TIME",10,MainActivity.MUTED));
  lower.addView(paceCell,new LinearLayout.LayoutParams(0,-2,1));
  View divider=new View(this);divider.setBackgroundColor(Color.rgb(53,65,80));LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(dp(1),dp(48));dividerParams.setMargins(dp(10),0,dp(10),0);lower.addView(divider,dividerParams);
  lower.addView(timeCell,new LinearLayout.LayoutParams(0,-2,1));root.addView(stats);
  FrameLayout mapFrame=new FrameLayout(this);mapFrame.setBackground(shape(MainActivity.CARD,22));mapFrame.setClipToOutline(true);
  map=new RouteMapView(this);mapFrame.addView(map,new FrameLayout.LayoutParams(-1,-1));
  TextView credit=label("© OpenStreetMap contributors",9,MainActivity.WHITE);credit.setPadding(dp(9),dp(5),dp(9),dp(5));credit.setBackgroundColor(Color.argb(210,13,18,26));
  credit.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));
  FrameLayout.LayoutParams creditParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);mapFrame.addView(credit,creditParams);
  LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(-1,0,1);mapParams.setMargins(0,dp(14),0,dp(14));root.addView(mapFrame,mapParams);
  LinearLayout controls=new LinearLayout(this);
  pause=button("Ⅱ  PAUSE",MainActivity.LIME,MainActivity.BG,v->command(prefs.getBoolean("paused",false)?"RESUME":"PAUSE"));
  Button finish=button("■  FINISH",MainActivity.CARD,MainActivity.WHITE,v->new AlertDialog.Builder(this)
   .setTitle("Save this run?").setMessage("Your distance, time and route will appear in Recent runs.")
   .setPositiveButton("Save run",(d,w)->command("FINISH")).setNegativeButton("Keep running",null).show());
  LinearLayout.LayoutParams pauseParams=new LinearLayout.LayoutParams(0,dp(56),1);pauseParams.rightMargin=dp(10);controls.addView(pause,pauseParams);
  controls.addView(finish,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(controls);
 }
 private void refresh() {
  if(!prefs.getBoolean("active",false)) { finish();return; }
  boolean paused=prefs.getBoolean("paused",false),waiting=prefs.getBoolean("waiting_gps",false);
  long elapsed=prefs.getLong("accumulated",0);
  if(!paused && !waiting) elapsed+=Math.max(0,SystemClock.elapsedRealtime()-prefs.getLong("segment",SystemClock.elapsedRealtime()));
  distance.setText(String.format(Locale.US,"%.3f",prefs.getFloat("meters",0)/1000.0));time.setText(RunMath.clock(elapsed));
  long speedFix=prefs.getLong("speed_fix_elapsed",0),age=SystemClock.elapsedRealtime()-speedFix;
  currentPace.setText(paused || waiting || speedFix==0 || age>5000 || age<0?"—":RunMath.currentPace(prefs.getFloat("speed_kmh",0)));
  pause.setText(paused?"▶  RESUME":"Ⅱ  PAUSE");
  long fix=prefs.getLong("last_fix_elapsed",0);
  boolean stale=fix!=0 && SystemClock.elapsedRealtime()-fix>5000;
  String tracking=prefs.getString("status","Finding GPS…");
  status.setText(tracking.startsWith("Could not save")?tracking:paused?"PAUSED · resume when you're ready":waiting?"Acquiring GPS · time starts after a fix":stale?"GPS signal lost · finding a fresh fix":prefs.getFloat("accuracy",999)>20?"Weak GPS signal · waiting for a clearer fix":tracking.startsWith("GPS live")?"●  GPS LIVE":tracking);
  status.setTextColor(paused?MainActivity.MUTED:waiting || stale || prefs.getFloat("accuracy",999)>20?Color.rgb(251,198,112):MainActivity.LIME);
  String route=prefs.getString("live_route","[]");
  if(!lastRoute.equals(route)) { lastRoute=route;try { map.setRoute(new JSONArray(route),true); } catch(JSONException ignored) { } }
 }
 private void command(String action) {
  startService(new Intent(this,RunService.class).setAction(action));handler.postDelayed(()->refresh(),200);
 }
 @Override public void onBackPressed() {
  new AlertDialog.Builder(this).setTitle("Keep recording in the background?")
   .setMessage("Your run will continue. Tap the recording notification to return.")
   .setPositiveButton("Keep recording",(d,w)->moveTaskToBack(true)).setNegativeButton("Stay here",null).show();
 }
 private LinearLayout column() { LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view; }
 private TextView label(String value,int size,int color) {
  TextView view=new TextView(this);view.setText(value);view.setTextColor(color);view.setTextSize(size);view.setIncludeFontPadding(false);
  view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view;
 }
 private TextView metric(String value,int size) {
  TextView view=label(value,size,MainActivity.WHITE);view.setGravity(Gravity.CENTER);view.setMaxLines(1);view.setFontFeatureSettings("tnum");
  view.setAutoSizeTextTypeUniformWithConfiguration(12,size,1,TypedValue.COMPLEX_UNIT_SP);return view;
 }
 private GradientDrawable shape(int color,int radius) { GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape; }
 private Button button(String value,int background,int foreground,View.OnClickListener click) {
  Button view=new Button(this);view.setText(value);view.setTextSize(13);view.setTextColor(foreground);view.setAllCaps(false);
  view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(45,255,255,255)),shape(background,18),null));view.setOnClickListener(click);return view;
 }
 private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
 @Override protected void onResume() { super.onResume();handler.post(tick); }
 @Override protected void onPause() { handler.removeCallbacks(tick);super.onPause(); }
}
