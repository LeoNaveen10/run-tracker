package com.runtracker.app;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ShareCard {
 public static void share(Activity activity,JSONObject run,RouteMapView map) {
  Bitmap card=null;
  try {
   if(map.getWidth()<=0 || map.getHeight()<=0) { Toast.makeText(activity,"Wait for the map to appear, then share",Toast.LENGTH_SHORT).show();return; }
   card=Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(card);canvas.drawColor(MainActivity.BG);
   Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
   text(canvas,paint,"RUN TRACKER",64,83,30,MainActivity.LIME);
   text(canvas,paint,new SimpleDateFormat("EEE, d MMM yyyy · HH:mm",Locale.getDefault()).format(new Date(run.optLong("date"))),64,137,27,MainActivity.MUTED);
   text(canvas,paint,String.format(Locale.US,"%.2f",run.optDouble("meters")/1000),64,267,110,MainActivity.WHITE);
   text(canvas,paint,"DISTANCE · KM",64,313,23,MainActivity.MUTED);
   text(canvas,paint,RunMath.pace(run.optLong("duration"),run.optDouble("meters")),520,235,53,MainActivity.WHITE);
   text(canvas,paint,"AVG PACE · MIN/KM",520,278,21,MainActivity.MUTED);
   text(canvas,paint,RunMath.clock(run.optLong("duration")),64,400,49,MainActivity.WHITE);
   text(canvas,paint,"RUN TIME",64,441,21,MainActivity.MUTED);
   double target=run.optDouble("targetKm",0);
   text(canvas,paint,target>0?GoalProgress.number(target)+" km":"—",520,400,49,MainActivity.LIME);
   text(canvas,paint,"TARGET",520,441,21,MainActivity.MUTED);
   paint.setColor(MainActivity.CARD);canvas.drawRect(64,493,1016,1183,paint);
   float scale=Math.min(952f/map.getWidth(),690f/map.getHeight());
   canvas.save();canvas.translate(64+(952-map.getWidth()*scale)/2,493+(690-map.getHeight()*scale)/2);
   canvas.scale(scale,scale);map.draw(canvas);canvas.restore();
   text(canvas,paint,"© OpenStreetMap contributors",64,1223,24,MainActivity.MUTED);
   text(canvas,paint,"MY ROUTE. MY PACE.",64,1291,29,MainActivity.LIME);
   File directory=new File(activity.getCacheDir(),"shares");if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare share image");
   File[] old=directory.listFiles();if(old!=null) for(File file:old) if(file.lastModified()<System.currentTimeMillis()-7L*24*3600*1000) file.delete();
   String name="run-"+UUID.randomUUID()+".png";File file=new File(directory,name);
   try(FileOutputStream out=new FileOutputStream(file)) { if(!card.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("Could not write image"); }
   Uri uri=new Uri.Builder().scheme("content").authority("com.runtracker.app.share").appendPath(name).build();
   Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
    .putExtra(Intent.EXTRA_TEXT,String.format(Locale.US,"My run: %.2f km · %s · %s min/km",run.optDouble("meters")/1000,RunMath.clock(run.optLong("duration")),RunMath.pace(run.optLong("duration"),run.optDouble("meters"))))
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   send.setClipData(ClipData.newUri(activity.getContentResolver(),"Run route and metrics",uri));
   activity.startActivity(Intent.createChooser(send,"Share your run"));
  } catch(Exception error) { Toast.makeText(activity,"Could not share this run. Check storage and available sharing apps.",Toast.LENGTH_LONG).show(); }
  finally { if(card!=null) card.recycle(); }
 }
 private static void text(Canvas canvas,Paint paint,String value,int x,int y,int size,int color) {
  paint.setTextSize(size);paint.setColor(color);canvas.drawText(value,x,y,paint);
 }
}
