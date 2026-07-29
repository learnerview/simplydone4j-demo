package com.demo;

import com.demo.controller.JobController;
import com.demo.handler.DemoHandlers;
import com.demo.listener.DemoEventListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({DemoHandlers.class, JobController.class, DemoEventListener.class})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
