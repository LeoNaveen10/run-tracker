package com.runtracker.app;

public class GpsFilterTest {
 private static int checks;
 public static void main(String[] args) {
  GpsFilter filter=new GpsFilter();
  test(filter.update(13,77,3,3,1000).record,"First fix anchors the route");
  close(3,filter.update(13+3/111195.0,77,3,3,2000).distance,.05,"One-second running movement counts");
  close(3,filter.update(13+6/111195.0,77,3,3,3000).distance,.05,"Every following fix updates distance");
  GpsFilter.Fix jump=filter.update(14,78,3,3,4000);
  test(jump.jump && jump.distance==0,"Kilometre GPS jump is rejected");
  close(6,filter.update(13+12/111195.0,77,3,3,5000).distance,.05,"A rejected jump does not contaminate the next fix");
  test(!filter.update(13,77,3,3,5000).record,"Duplicate timestamp is ignored");
  GpsFilter.Fix gap=filter.update(13.1,77,3,3,20000);
  test(gap.record && gap.breakSegment && gap.distance==0,"Signal loss reanchors without inventing distance");
  filter.reset();filter.update(13,77,4,0,1000);
  for(int i=1;i<=6;i++) close(0,filter.update(13+(i%2)*2/111195.0,77,4,0,1000+i*1000).distance,0,"Stationary position noise is not running");
  test(!filter.update(13,77,30,2,9000).record,"Weak accuracy is rejected");
  test(!filter.update(Double.NaN,77,3,2,10000).record,"Invalid coordinates are rejected");
  filter.reset();close(0,filter.update(14,78,3,3,11000).distance,0,"Pause/resume reset never bridges the pause");
  close(111195,GpsFilter.distance(0,0,1,0),1,"Geodesic distance is in metres");
  close(77,RunMath.longitude(RunMath.worldX(77)),.000001,"Map longitude round trip");
  close(13,RunMath.latitude(RunMath.worldY(13)),.000001,"Map latitude round trip");
  test(Double.isFinite(RunMath.worldY(90)),"Map projection handles poles");
  System.out.println(checks+" GPS and map checks passed");
 }
 private static void test(boolean condition,String name) { checks++;if(!condition) throw new AssertionError(name); }
 private static void close(double expected,double actual,double tolerance,String name) { test(Math.abs(expected-actual)<=tolerance,name+": "+actual); }
}
