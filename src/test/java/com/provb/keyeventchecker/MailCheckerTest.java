package com.provb.keyeventchecker;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

class MailCheckerTest {

    @Test
    public void receiveMailTest() throws IOException, MessagingException {

//        // Инициализация свойств
//        FileInputStream fileInputStream = new FileInputStream("application.properties");
//        Properties properties = new Properties();
//        properties.load(fileInputStream);
//        String host = properties.getProperty("mail.host3");
//        String user = properties.getProperty("mail.user3");
//        String password = properties.getProperty("mail.password3");
//
//        // Подключение к INBOX
//        Properties mailProperties = new Properties();
//        properties.put("mail.store.protocol", "imaps");
//
//        Store store = Session.getInstance(properties).getStore();
//        store.connect(host, user, password);
//        Folder inbox = store.getFolder("INBOX");
//        inbox.open(Folder.READ_ONLY);
//
//        int count = inbox.getMessageCount();
//        Message[] messages = inbox.getMessages(count - 5, count);
//        for (Message message : messages) {
//            System.out.print(message.getReceivedDate());
//            System.out.println("\t" + MimeUtility.decodeText(message.getSubject()));
//            printMessage(message);
//            System.out.println();
//        }
//
//        store.close();
    }

    private void printMessage(Message message) throws MessagingException, IOException {
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
        text = simpleText(text);
        System.out.println(text);
    }

    private String getTextFromPart(BodyPart part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return part.getContent().toString();
        } else if (part.isMimeType("text/html")) {
            return Jsoup.parse(part.getContent().toString()).text();
        } else if (part.isMimeType("multipart/*")) {
            Multipart content = (Multipart) part.getContent();
            BodyPart thisPart = content.getBodyPart(0);
            return getTextFromPart(thisPart);
        }
        return "";
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

}