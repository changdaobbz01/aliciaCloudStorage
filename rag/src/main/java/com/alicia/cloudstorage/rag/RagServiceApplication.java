package com.alicia.cloudstorage.rag;

import com.alicia.cloudstorage.rag.assistant.RecognitionShadowJob;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class RagServiceApplication {

    public static void main(String[] args) {
        if (args.length > 0 && ("--recognition-shadow".equals(args[0])
                || "--recognition-shadow-online".equals(args[0]))) {
            if (args.length != 1) {
                throw new IllegalArgumentException("The recognition shadow command does not accept extra arguments.");
            }
            runRecognitionShadow("--recognition-shadow-online".equals(args[0]));
            return;
        }
        SpringApplication.run(RagServiceApplication.class, args);
    }

    private static void runRecognitionShadow(boolean online) {
        try (var context = new SpringApplicationBuilder(RagServiceApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("recognition-shadow")
                .run("--spring.main.web-application-type=none",
                        "--alicia.rag.deepseek.network-enabled=" + online,
                        "--alicia.rag.execution-registration.enabled=false",
                        "--alicia.rag.storage-api.base-url=",
                        "--alicia.rag.conversation.persistence-mode=MEMORY",
                        "--alicia.rag.example-retrieval.mode=LEGACY",
                        "--alicia.rag.recognition.trace.enabled=true",
                        "--alicia.rag.recognition.feedback.enabled=false",
                        "--alicia.rag.recognition.feedback.candidate-directory=")) {
            context.getBean(RecognitionShadowJob.class).run(online);
        }
    }
}
