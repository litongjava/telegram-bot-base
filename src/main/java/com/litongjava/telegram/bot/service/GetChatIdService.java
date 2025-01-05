package com.litongjava.telegram.bot.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;

import com.litongjava.telegram.can.TelegramClientCan;

public class GetChatIdService {

  public void index(Update update) {
    Chat chat = null;
    if (update.hasMessage()) {
      chat = update.getMessage().getChat();
    } else if (update.hasChannelPost()) {
      chat = update.getChannelPost().getChat();
    }

    Long chatId = chat.getId();

    // 创建回发消息对象，将收到的文本原样发送回去
    SendMessage sendMessage = new SendMessage(chatId.toString(), chatId.toString());
    // 使用TelegramClient发送消息
    TelegramClientCan.execute(sendMessage);
  }
}
