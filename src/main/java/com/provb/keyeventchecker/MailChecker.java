package com.provb.keyeventchecker;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class MailChecker {

    private static final List<MailChecker> checkers = new ArrayList<>();
    private static final List<Pattern> patterns = new ArrayList<>();
    private static final List<Long> users = new ArrayList<>();
    private static Bot bot;

    private final String host;
    private final String user;
    private final String password;
    private Store store;
    private Folder inbox;
    private boolean ready;
    private long lastTime;

    public MailChecker(String host, String user, String password) {
        this.host = host;
        this.user = user;
        this.password = password;
        ready = false;
        lastTime = System.currentTimeMillis();
    }

    public static void run(Bot bot) throws IOException {

        MailChecker.bot = bot;

        // Читаем конфиг
        FileInputStream fileInputStream = new FileInputStream("application.properties");
        Properties properties = new Properties();
        properties.load(fileInputStream);

        // Заполняем параметры подключения
        for (int i = 1; i <= 9; i++) {
            String host = properties.getProperty("mail.host" + i);
            String user = properties.getProperty("mail.user" + i);
            String password = properties.getProperty("mail.password" + i);
            if (host != null && user != null && password != null) {
                checkers.add(new MailChecker(host, user, password));
            }

        }

        // Заполняем получателей уведомлений
        for (int i = 1; i <= 9; i++) {
            String chatid = properties.getProperty("bot.chatid" + i);
            if (chatid != null) {
                users.add(Long.parseLong(chatid));
            }
        }

        // Заполняем паттерны
        List<String> lines = Files.readAllLines(Paths.get("patterns"));
        for (String line : lines) {
            patterns.add(Pattern.compile(line));
        }

    }

    private void connect() {

        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");

        try {
            if (store == null) {
                store = Session.getInstance(properties).getStore();
            } else {
                store.close();
            }
            store.connect(host, user, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            ready = true;
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public void check() {

        if (!ready) {
            connect();
            return;
        }

        try {
            Message[] messages = inbox.getMessages();
            Arrays.stream(messages)
                    // Берем только новые письма
                    .filter(m -> {
                        try {
                            return m.getReceivedDate().after(new Date(lastTime));
                        } catch (MessageRemovedException e) {
                            // No action
                        } catch (MessagingException e) {
                            e.printStackTrace();
                            ready = false;
                        }
                        return false;
                    })
                    .forEach(this::handlerMessage);
        } catch (MessagingException e) {
            e.printStackTrace();
            ready = false;
        }

    }

    private void handlerMessage(Message message) {
        try {
            lastTime = message.getReceivedDate().getTime();

            String from = message.getFrom()[0].toString();
            String subject = "";
            String text = "";

            if (message.isMimeType("text/plain")) {
                subject = MimeUtility.decodeText(message.getSubject()) + "\n";
                text = message.getContent().toString();
            } else if (message.isMimeType("multipart/*")) {
                subject = MimeUtility.decodeText(message.getSubject());
                Multipart content = (Multipart) message.getContent();
                BodyPart part = content.getBodyPart(0);
                text = getTextFromPart(part);
            }

            boolean isMatch = false;
            for (Pattern pattern : patterns) {
                if (pattern.matcher(text.toLowerCase()).find()) {
                    isMatch = true;
                    break;
                }
            }

            if (isMatch) {
                StringBuilder t = new StringBuilder();
                t.append("От: ").append(from.substring(from.indexOf("<"))).append("\n")
                        .append("Тема: ").append(subject).append("\n")
                        .append(text);
                for (Long chatid : users) {
                    bot.sendMessage(chatid, t.toString());
                }
            }
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
            ready = false;
        }
    }

    private String getTextFromPart(BodyPart part) throws MessagingException, IOException {

        if (part.isMimeType("text/plain")) {
            return part.getContent().toString();
        } else if (part.isMimeType("multipart/*")) {
            Multipart content = (Multipart) part.getContent();
            BodyPart thisPart = content.getBodyPart(0);
            return getTextFromPart(thisPart);
        }
        return "";
    }

    public static List<MailChecker> getCheckers() {
        return checkers;
    }

}
