package com.pdf.marsk.pdfdemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles; // Added

@ActiveProfiles("test") // Added
@SpringBootTest // Removed properties
class PdfdemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
