package com.alicia.cloudstorage.identity.mail;

class DisabledEmailSender implements EmailSender {

    @Override
    public void sendText(String to, String subject, String body) {
        throw new EmailDeliveryException("邮箱服务暂未配置，请稍后再试。");
    }
}
