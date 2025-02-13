package ru.cbgr.adapter.xwiki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XwikiApplication {

	public static void main(String[] args) {
		SpringApplication.run(XwikiApplication.class, args);
	}

}
