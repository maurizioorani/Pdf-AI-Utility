package com.pdf.marsk.pdfdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.config.DocumentProcessingProperties;

@SpringBootApplication
@EnableAsync // Enable asynchronous processing
@EnableCaching // Enable caching support
@EnableConfigurationProperties({RagConfigurationProperties.class, DocumentProcessingProperties.class})
public class PdfApplication {

	public static void main(String[] args) {
		SpringApplication.run(PdfApplication.class, args);
	}

}
