package com.alicia.cloudstorage.api.mail;

public interface EmailSender {

    void sendText(String to, String subject, String body);
}
