package com.litongjava.telegram.bot.command;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.litongjava.telegram.can.TelegramClientCan;

public class InfoCommandFunction {

  public void index(Update update) {
    Long chatId = update.getMessage().getChatId();
    // 创建回发消息对象，将收到的文本原样发送回去
    SendMessage sendMessage = new SendMessage(chatId.toString(), "Chat Id:" + chatId);

    // 使用TelegramClient发送消息
    TelegramClientCan.execute(sendMessage);

  }
}