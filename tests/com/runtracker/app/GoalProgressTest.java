package com.runtracker.app;
import java.util.*;
public class GoalProgressTest {
 private static int checks;
 public static void main(String[] args) {
  check(GoalProgress.validTarget(5) && GoalProgress.validTarget(10),"Target endpoints allowed");
  check(!GoalProgress.validTarget(4.9) && !GoalProgress.validTarget(10.1),"Out-of-range targets rejected");
  check(!GoalProgress.validTarget(Double.NaN),"Invalid target rejected");
  check("5.25".equals(GoalProgress.number(5.25)),"Fractional goal has correct displayed precision");
  GoalProgress goal=new GoalProgress(0,false);
  check(goal.advance(999.9,5).isEmpty(),"No early kilometre announcement");
  List<String> messages=goal.advance(1000,5);
  check(messages.size()==1 && messages.get(0).equals("1 kilometre done. 4 kilometres to go."),"First milestone includes remaining target");
  check(goal.advance(1001,5).isEmpty(),"Repeated GPS samples do not repeat a milestone");
  messages=goal.advance(3000,5);
  check(messages.size()==2 && messages.get(0).equals("2 kilometres done. 3 kilometres to go."),"Each crossed kilometre announced");
  GoalProgress recovered=new GoalProgress(goal.completed(),goal.reached());
  check(recovered.advance(3001,5).isEmpty(),"Recovery does not repeat old milestones");
  messages=recovered.advance(5000,5);
  check(messages.size()==2 && recovered.reached() && messages.get(1).contains("Target reached"),"Integer target reached once");
  check(recovered.advance(5001,5).isEmpty(),"Target completion not repeated");
  messages=recovered.advance(6000,5);
  check(messages.size()==1 && messages.get(0).contains("past your target"),"Running can continue after target");
  GoalProgress fractional=new GoalProgress(5,false);
  check(fractional.advance(5499,5.5).isEmpty(),"Fractional target not early");
  check(fractional.advance(5500,5.5).size()==1 && fractional.reached(),"Fractional target completion announced");
  check(fractional.advance(5501,5.5).isEmpty(),"Fractional target not repeated");
  check(new GoalProgress(0,false).advance(Double.NaN,5).isEmpty(),"Invalid distance ignored");
  System.out.println(checks+" goal and milestone checks passed");
 }
 private static void check(boolean pass,String name) { checks++;if(!pass) throw new AssertionError(name); }
}
