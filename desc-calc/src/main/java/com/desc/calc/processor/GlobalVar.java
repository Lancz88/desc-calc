package com.desc.calc.processor;

public class GlobalVar {

    public static final String CSV_FILE_PATH = "waypoints-paste.csv";

    public static final int DEFAULT_CRUISE_ALTITUDE = 39000;
    public static final int DEFAULT_INITIAL_DESCENT_SPEED = 290;
    public static final int DEFAULT_TD_EXTRA_DISTANCE = 5;
    public static final int DEFAULT_PREPARATION_LEAD_DISTANCE = 40;

    public static int crzAlt = DEFAULT_CRUISE_ALTITUDE;
    public static int initialSpeed = DEFAULT_INITIAL_DESCENT_SPEED;

    public static Integer ts;
    public static Integer gs;
    public static double windAltRatio = 1;

    public static boolean addFl100Wp = false;
}
