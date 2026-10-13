package com.runtracker.app;
import java.util.*;

/** Adds derived split times without modifying recorded distance, elapsed time or pace. */
public final class SplitTracker {
 public static final class Split {
  public final int kilometre;
  public final long duration,elapsed;
  Split(int kilometre,long duration,long elapsed) { this.kilometre=kilometre;this.duration=duration;this.elapsed=elapsed; }
 }
 private int completed;
 private long boundaryElapsed,previousElapsed;
 private double previousMeters;
 public SplitTracker(int completed,long boundaryElapsed,double meters,long elapsed) {
  this.completed=completed;this.boundaryElapsed=boundaryElapsed;previousMeters=meters;previousElapsed=elapsed;
 }
 public List<Split> advance(double meters,long elapsed) {
  List<Split> added=new ArrayList<>();
  if(!Double.isFinite(meters) || meters<previousMeters || elapsed<previousElapsed) return added;
  double delta=meters-previousMeters;
  while(delta>0 && (completed+1)*1000.0<=meters) {
   double fraction=Math.max(0,Math.min(1,((completed+1)*1000.0-previousMeters)/delta));
   long crossing=previousElapsed+Math.round(fraction*(elapsed-previousElapsed));
   long duration=boundaryElapsed<0?-1:Math.max(0,crossing-boundaryElapsed);
   completed++;added.add(new Split(completed,duration,crossing));boundaryElapsed=crossing;
  }
  previousMeters=meters;previousElapsed=elapsed;return added;
 }
}
