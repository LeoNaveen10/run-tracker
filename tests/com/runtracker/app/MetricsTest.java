package com.runtracker.app;
public class MetricsTest {
 public static void main(String[] args) {
  check("00:00:00", RunMath.clock(0));
  check("01:02:03", RunMath.clock(3723000));
  check("5:00", RunMath.pace(1500000, 5000));
  check("4:30", RunMath.pace(270000, 1000));
  check("—", RunMath.pace(1000, 0));
  check("3:24", RunMath.pace(10000, 49));
  number(12.6,RunMath.speedKmh(3.5));
  number(0,RunMath.speedKmh(0));
  number(0,RunMath.speedKmh(-1));
  number(0,RunMath.speedKmh(Double.NaN));
  check("5:00",RunMath.currentPace(12));
  check("6:00",RunMath.currentPace(10));
  check("7:30",RunMath.currentPace(8));
  check("8:34",RunMath.currentPace(7));
  check("—",RunMath.currentPace(0));
  check("—",RunMath.currentPace(-1));
  check("—",RunMath.currentPace(Double.NaN));
  check("—",RunMath.currentPace(Double.POSITIVE_INFINITY));
  System.out.println("18 run metric checks passed");
 }
 static void number(double expected,double actual) { if(Math.abs(expected-actual)>.000001) throw new AssertionError(expected+" != "+actual); }
 static void check(String expected, String actual) {
  if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
 }
}
