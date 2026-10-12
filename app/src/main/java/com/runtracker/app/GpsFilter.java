package com.runtracker.app;

/** Rejects impossible jumps, suppresses stationary drift, never bridges a GPS gap. */
public final class GpsFilter {
 private double latitude,longitude,accuracy;
 private long time;
 private boolean anchored;
 public static final class Fix {
  public double distance,speed;
  public boolean record, jump, breakSegment;
 }
 public void reset() { anchored=false; }
 public Fix update(double lat,double lon,double a,double speed,long ms) {
  Fix f=new Fix();
  if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(a)||Math.abs(lat)>90||Math.abs(lon)>180||a<=0||a>20) return f;
  if(Double.isFinite(speed) && (speed<0 || speed>12)) { f.jump=true;return f; }
  if(!anchored) { anchor(lat,lon,a,ms); f.record=true; return f; }
  if(ms<=time) return f;
  double seconds=(ms-time)/1000.0;
  if(seconds>10) { anchor(lat,lon,a,ms); f.record=true; f.breakSegment=true; return f; }
  double delta=distance(latitude,longitude,lat,lon);
  if(delta/seconds>12 || (Double.isFinite(speed)&&speed>12)) { f.jump=true; return f; }
  // Doppler-derived speed is more useful than position jitter when standing still.
  if(Double.isFinite(speed)&&speed<.35) { anchor(lat,lon,a,ms); return f; }
  double threshold=Math.max(.5,Math.min(1.2,Math.min(accuracy,a)*.15));
  if(delta<threshold) return f;
  f.distance=delta; f.speed=delta/seconds; f.record=true; anchor(lat,lon,a,ms); return f;
 }
 private void anchor(double lat,double lon,double a,long ms) { latitude=lat;longitude=lon;accuracy=a;time=ms;anchored=true; }
 public static double distance(double lat1,double lon1,double lat2,double lon2) {
  double dlat=Math.toRadians(lat2-lat1),dlon=Math.toRadians(lon2-lon1);
  double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dlon/2)*Math.sin(dlon/2);
  return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
 }
}
