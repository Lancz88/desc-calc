package com.desc.calc;

import com.desc.calc.processor.GlobalVar;
import com.desc.calc.processor.service.CSVService;
import com.desc.calc.processor.service.DescentService;
import com.desc.calc.simbrief.SimBriefService;
import com.desc.calc.timer.TimerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import static com.desc.calc.processor.GlobalVar.CSV_FILE_PATH;

@SpringBootApplication
public class SpringApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringApp.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "app.runner", havingValue = "proc")
    CommandLineRunner runCalc(CSVService csvService,
                              DescentService descentService) {

        return args -> {

            try {
                GlobalVar.crzAlt = Integer.parseInt(args[1]);
                if (args.length > 2) {
                    GlobalVar.initialSpeed = Integer.parseInt(args[2]);
                }
                if (args.length > 3) {
                    GlobalVar.ts = Integer.parseInt(args[3]);
                }
                if (args.length > 4) {
                    GlobalVar.gs = Integer.parseInt(args[4]);
                }
                if (args.length > 5) {
                    GlobalVar.windAltRatio = Double.parseDouble(args[5]);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed parsing input arguments, " + e);
            }

            System.out.println(descentService.process(csvService.read(CSV_FILE_PATH)).itinerary());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.runner", havingValue = "simbrief")
    CommandLineRunner runSimBrief(SimBriefService simBriefService) {
        return args -> {
            simBriefService.presentSimBriefData(Integer.parseInt(args[1]));
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.runner", havingValue = "timer")
    CommandLineRunner runTimer(TimerService timerService) {
        return args -> {
            timerService.calculateTimeToReach(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.runner", havingValue = "tod-timer")
    CommandLineRunner runTodTimer(TimerService timerService) {
        return args -> {
            timerService.calculateTimeToTod(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        };
    }
}
