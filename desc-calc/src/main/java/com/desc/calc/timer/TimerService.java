package com.desc.calc.timer;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class TimerService {

    private final int DIST_PREPARE_OFFSET = 40;

    public void calculateTimeToReach(int distanceToDo, int groundSpeed) {
        System.out.println("\n");
        System.out.println("--------------- TIME UNTIL TARGET ---------------");
        System.out.println(processTimeToReach(distanceToDo, groundSpeed));
    }

    public void calculateTimeToTod(int distanceToWp, int groundSpeed, int distanceWpOffset) {

        int distanceTOD = distanceToWp - distanceWpOffset;
        int distanceBeforePrepare = distanceTOD - DIST_PREPARE_OFFSET;

        System.out.println("\n");
        System.out.println("--------------- TIME UNTIL TOD ---------------");
        System.out.println("START PREPARE: " + processTimeToReach(distanceBeforePrepare, groundSpeed));
        System.out.println("TOP OF DESCENT: " + processTimeToReach(distanceTOD, groundSpeed));
    }

    private String processTimeToReach(int distanceToDo, int groundSpeed) {

        float rawTime = ((float) distanceToDo) / groundSpeed;

        int totalMinutes = Math.round(rawTime * 60);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        // Current local time
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        // Add duration
        LocalDateTime arrivalTime = now
                .plusHours(hours)
                .plusMinutes(minutes);

        return String.format("%02d", hours) + ":" + String.format("%02d", minutes) + " - " +
                arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}