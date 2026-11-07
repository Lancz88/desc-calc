package com.desc.calc.processor;

import com.desc.calc.processor.obj.Waypoint;

import java.util.ArrayList;
import java.util.List;

public class CalcUtil {

    public static int calcDistanceNeeded(int altFrom, int altTo, double angle) {
        return (int) ((altFrom - altTo) / (angle * 100));
    }

    public static int calcAltAtDistance(int distance, int altTo, double angle) {
        return (int) (distance * (angle * 100) + altTo);
    }

    public static double calcAngle(int altDiff, int distance) {
        if (distance == 0) {
            return 0;
        }
        return (double) altDiff / distance / 100;
    }

    public static Waypoint getPrevWp(List<Waypoint> waypoints, Waypoint wp) {
        int wpIndex = waypoints.indexOf(wp);
        return wpIndex - 1 <= 0 ? null : waypoints.get(wpIndex - 1);
    }

    public static List<Waypoint> subListFromTDToWP(List<Waypoint> waypoints, Waypoint wpUntil) {
        List<Waypoint> subList = new ArrayList<>();
        for (int i = 0; i <= waypoints.size() - 1; i++) {
            Waypoint currentWp = waypoints.get(i);
            subList.add(currentWp);
            if (currentWp.getName().equals(wpUntil.getName())) {
                break;
            }
        }
        return subList;
    }

    public static List<Waypoint> subListFromWPToEnd(List<Waypoint> waypoints, Waypoint wpUntil) {
        List<Waypoint> subList = new ArrayList<>();
        for (int i = waypoints.size() - 1; i >= 0; i--) {
            Waypoint currentWp = waypoints.get(i);
            subList.add(currentWp);
            if (currentWp.getName().equals(wpUntil.getName())) {
                break;
            }
        }
        return subList;
    }

    public static boolean angleEquals(double angle1, double angle2) {
        return roundAngle(angle1) == roundAngle(angle2);
    }

    public static double roundAngle(double value) {
        double scale = Math.pow(10, 1);
        return Math.round(value * scale) / scale;
    }
}
