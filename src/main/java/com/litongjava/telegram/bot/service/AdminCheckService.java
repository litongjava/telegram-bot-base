package com.litongjava.telegram.bot.service;

import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;

import com.litongjava.telegram.can.TelegramClientCan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdminCheckService {

  /**
   * 检查机器人是否为指定频道的管理员
   * 
   * @param channelId 频道的 ID 或用户名（例如：@channelusername）
   * @return 如果机器人是管理员或频道所有者，返回 true；否则返回 false
   */
  public boolean isBotAdmin(Long chatId) {
    // 执行请求
    ChatMember chatMember = null;
    try {
      chatMember = TelegramClientCan.getChatMember(chatId, TelegramClientCan.botId);
    } catch (Exception e) {
      log.error(e.getMessage());
      return false;
    }

    if (chatMember != null) {
      String status = chatMember.getStatus();
      log.info("Bot status in channel {}: {}", chatId, status);

      // 检查是否为管理员或频道所有者
      return ChatMemberAdministrator.STATUS.equals(status);
    }

    return false;
  }
}
