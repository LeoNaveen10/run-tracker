package com.runtracker.app;

import org.json.*;

public class RunHistoryTest {
 private static int checks;
 public static void main(String[] args) throws Exception {
  JSONObject first=run(1,1000),second=run(2,2000),third=run(3,3000);
  JSONArray history=new JSONArray().put(first).put(second);
  check(RunHistory.remove(history,new JSONObject(first.toString())),"Can delete a previously parsed run");
  check(history.length()==1 && history.getJSONObject(0).getLong("date")==2,"Other run and its position survive");
  check(!RunHistory.remove(history,first),"Repeated deletion cannot remove a different run");
  history=new JSONArray().put(first).put(second);
  JSONObject selected=new JSONObject(first.toString());history.put(third);
  check(RunHistory.remove(history,selected) && history.length()==2 && history.getJSONObject(1).getLong("date")==3,"A run appended while confirmation is open survives");
  history=new JSONArray().put(first).put(new JSONObject(first.toString()));
  check(RunHistory.remove(history,first) && history.length()==1,"Duplicate records are removed one at a time");
  check(RunHistory.remove(history,first) && history.length()==0,"Last deletion produces an empty history");
  check(!RunHistory.remove(history,first),"Empty history is safe");
  history=new JSONArray().put(run(1,1500));
  check(!RunHistory.remove(history,first) && history.length()==1,"A changed snapshot does not delete the wrong record");
  System.out.println(checks+" run deletion checks passed");
 }
 private static JSONObject run(long date,double meters) throws Exception {
  return new JSONObject().put("date",date).put("meters",meters).put("duration",600000).put("route",new JSONArray());
 }
 private static void check(boolean pass,String name) { checks++;if(!pass) throw new AssertionError(name); }
}
