package com.desc.calc.simbrief;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class SimBriefWaypoint {

    @Getter
    @Setter
    int idx;

    @Getter
    @Setter
    String name;

    @Getter
    @Setter
    String type;

    @Getter
    @Setter
    int distanceToNext;

//    public SimBriefWaypoint(String name, String type, int distanceToNext) {
//        this.name = name;
//        this.type = type;
//        this.distanceToNext = distanceToNext;
//    }
}