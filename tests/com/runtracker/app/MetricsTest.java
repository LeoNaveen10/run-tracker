package com.runtracker.app;
public class MetricsTest {
 public static void main(String[] args) {
  check("00:00:00", RunMath.clock(0));
  check("01:02:03", RunMath.clock(3723000));
  check("5:00", RunMath.pace(1500000, 5000));
  check("4:30", RunMath.pace(270000, 1000));
  check("—", RunMath.pace(1000, 0));
  check("3:24", RunMath.pace(10000, 49));
  System.out.println("6 run metric checks passed");
 }
 static void check(String expected, String actual) {
  if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
 }
}
