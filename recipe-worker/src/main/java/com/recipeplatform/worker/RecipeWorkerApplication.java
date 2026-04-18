package com.recipeplatform.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RecipeWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecipeWorkerApplication.class, args);
    }
}
