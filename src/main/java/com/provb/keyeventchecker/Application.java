package com.provb.keyeventchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.ApiContextInitializer;

import java.io.IOException;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) throws IOException {
        ApiContextInitializer.init();
        ApplicationContext context = SpringApplication.run(Application.class, args);
        Bot bot = context.getBean(Bot.class);
        MailChecker.run(bot);
    }

    @Scheduled(cron = "*/5 * * * * *") // Каждые 5 секунд
    public static void check() {
        for (MailChecker checker : MailChecker.getCheckers()) {
            checker.check();
        }
    }

}
