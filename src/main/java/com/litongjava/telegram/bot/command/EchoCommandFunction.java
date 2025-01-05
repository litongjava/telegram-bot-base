package com.litongjava.telegram.bot.command;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.litongjava.telegram.can.TelegramClientCan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EchoCommandFunction {

  public void index(Update update) {
    Long chatId = update.getMessage().getChatId();
    String receivedText = update.getMessage().getText();
    log.info("Received text message: {}", receivedText);
    String result = receivedText.split(" ")[1];

    // 创建回发消息对象，将收到的文本原样发送回去
    SendMessage sendMessage = new SendMessage(chatId.toString(), result);

    // 使用TelegramClient发送消息
    TelegramClientCan.execute(sendMessage);

  }

}
