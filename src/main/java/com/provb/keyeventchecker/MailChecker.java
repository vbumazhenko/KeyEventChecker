package com.provb.keyeventchecker;

import org.jsoup.Jsoup;

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
    private static final int maxLengthText = 4000;

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
//        Calendar calendar = Calendar.getInstance();
//        calendar.set(Calendar.YEAR, Calendar.MONTH, Calendar.DATE);
//        lastTime = calendar.getTimeInMillis();
    }

    public static void run(Bot bot) throws IOException {

        MailChecker.bot = bot;

        // Читаем конфиг
        String prefix = System.getProperty("homedir") != null ? System.getProperty("homedir") : "";
        FileInputStream fileInputStream = new FileInputStream(prefix + "application.properties");
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
        List<String> lines = Files.readAllLines(Paths.get(prefix + "patterns"));
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
            int count = inbox.getMessageCount();
            Message[] messages = inbox.getMessages(count - 5, count);  // последние 5 сообщений
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
            String subject = MimeUtility.decodeText(message.getSubject()) + "\n";
            String text = "";

            if (message.isMimeType("text/plain")) {
                text = message.getContent().toString();
            } else if (message.isMimeType("text/html")) {
                text = Jsoup.parse(message.getContent().toString()).wholeText();
            } else if (message.isMimeType("multipart/*")) {
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
                t.append("От кого: ").append(from.substring(from.indexOf("<"))).append("\n")
                        .append("Кому: ").append(getUser()).append("\n")
                        .append("Тема: ").append(subject).append("\n")
                        .append(simpleText(text));
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
        } else if (part.isMimeType("text/html")) {
            return Jsoup.parse(part.getContent().toString()).wholeText();
        } else if (part.isMimeType("multipart/*")) {
            Multipart content = (Multipart) part.getContent();
            BodyPart thisPart = content.getBodyPart(0);
            return getTextFromPart(thisPart);
        }
        return "";
    }

    public String getUser() {
        return user;
    }

    public String simpleText(String text) {
        text = text.trim();
        for (;;) {
            int length = text.length();
            text = text
                    .replace("\t", " ")
                    .replace("  ", " ")
                    .replace("\r\n\r\n", "\r\n")
                    .replace("\r\n ", "\r\n");
            if (length == text.length()) {
                break;
            }
        }
        return text.substring(0, Math.min(text.length(), 4000));
    }

    public static List<MailChecker> getCheckers() {
        return checkers;
    }
}
