package com.litongjava.telegram.bot.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import com.litongjava.db.activerecord.Db;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.utils.SendMessageUtils;
import com.litongjava.tio.utils.environment.EnvUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotSettingService {

  public void setAd(Update update) {
    Message message = update.getMessage();
    Long chatId = message.getChatId();
    if (chatId.equals(EnvUtils.getLong("bot.admin.group"))) {
      String text = message.getText();
      String substring = text.substring(7);
      log.info("ad:{}", substring);
      Db.updateBySql("update setting set set_value=? where set_key='batch_folder'", substring);

      SendMessage sendMessage = SendMessageUtils.text(chatId, "操作成功");
      sendMessage.setReplyToMessageId(message.getMessageId());
      TelegramClientCan.execute(sendMessage);
    }
  }
}
