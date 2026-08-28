package org.whitedoggy.mapleweb2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Mapleweb2Application {

    public static void main(String[] args) {
        SpringApplication.run(Mapleweb2Application.class, args);
    }

}
