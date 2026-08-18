package com.alicia.cloudstorage.identity.mail;

public interface EmailSender {

    void sendText(String to, String subject, String body);
}
