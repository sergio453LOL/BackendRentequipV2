package com.rentequip.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	/** The marketplace works in Peruvian time, whatever the server's clock says. */
	public static final String MARKET_TIME_ZONE = "America/Lima";

	public static void main(String[] args) {
		useMarketTimeZone();
		SpringApplication.run(BackendApplication.class, args);
	}

	/**
	 * Every LocalDate.now() and LocalDateTime.now() of the code reads the JVM's default zone. EC2 (Amazon
	 * Linux) runs in UTC, where "today" turns into tomorrow at 19:00 in Lima: a booking starting today would
	 * then be refused as in the past. Fixing the default here makes the deployment independent of the host.
	 */
	static void useMarketTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone(MARKET_TIME_ZONE));
	}

}
