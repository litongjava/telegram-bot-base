package com.litongjava.telegram.bot.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.telegram.telegrambots.meta.api.methods.groupadministration.LeaveChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.db.utils.MarkdownTableUtils;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.utils.InlineKeyboardButtonUtils;
import com.litongjava.telegram.utils.SendMessageUtils;
import com.litongjava.telegram.utils.TelegramBotUtils;
import com.litongjava.telegram.utils.TextFilerUtils;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.network.IpUtils;
import com.litongjava.tio.utils.notification.NotifactionWarmModel;
import com.litongjava.tio.utils.notification.NotificationTemplate;
import com.litongjava.tio.utils.telegram.Telegram;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotPushService {
  private final Random random = new Random();

  public void push() {
    log.info("==================定时任务开始==================");
    String chatId = EnvUtils.getStr("telegram.notification.chat.id");
    NotifactionWarmModel model = new NotifactionWarmModel();
    model.setAppEnv(EnvUtils.env());
    model.setAppGroupName("telegram-bots");
    model.setAppName(EnvUtils.getStr("bot.name"));
    model.setDeviceName(IpUtils.getLocalIp());
    model.setLevel("II");
    model.setTime(new Date());

    // 获取车队信息
    String sql = "SELECT id, name FROM convoy WHERE join_count > 1";
    if (EnvUtils.isDev()) {
      sql = "SELECT id, name FROM convoy WHERE join_count > 0";
    }
    List<Row> cars = Db.find(sql);
    if (cars.isEmpty()) {
      log.info("没有符合条件的车队。");
      return;
    }

    // 获取推荐资源
    String recommendedResources = Db.queryStr("SELECT set_value FROM setting WHERE set_key='recommended_resource'");
    String button1 = Db.queryStr("SELECT set_value FROM setting WHERE set_key='button1'");

    if (recommendedResources == null) {
      recommendedResources = "https://t.me/Hmdbgx";
    }

    // 构建按钮
    ReplyKeyboard buttons = buildButtons(recommendedResources, button1);

    // 获取批量文件信息
    String batchFolder = Db.queryStr("SELECT set_value FROM setting WHERE set_key='batch_folder'");

    String botUrl = TelegramBotUtils.getBotUrl();

    String botTitle = EnvUtils.getStr("bot.title");
    for (Row car : cars) {
      String carId = car.getStr("id");
      String carName = car.getStr("name");
      StringBuilder ret = new StringBuilder();
      ret.append("<a href='").append(botUrl).append("'>🚀来自" + botTitle + "【").append(carName).append("】组🚀</a>\n\n");

      // 获取频道信息
      List<Row> channels = Db.find("SELECT channel_id, channel_name, url, car_id, tg_id, message_id FROM bot_channel WHERE car_id = ?", carId);
      if (channels.isEmpty()) {
        log.info("车队 {} 没有相关频道。", carName);
        continue;
      }

      // 打乱频道顺序
      Collections.shuffle(channels, random);

      // 构建频道列表
      for (int idx = 0; idx < channels.size(); idx++) {
        Row channel = channels.get(idx);
        String channelName = channel.getStr("channel_name");
        channelName = TextFilerUtils.filterEmoji(channelName);
        channelName = TextFilerUtils.truncateWithEllipsis(channelName, 15);
        String inviteUrl = channel.getStr("url");
        // 截断频道名称到最多 20 个字符
        String truncatedName = channelName.length() > 20 ? channelName.substring(0, 20) : channelName;
        ret.append(idx + 1).append("、<a href='").append(inviteUrl).append("'>").append(truncatedName).append("</a>\n");
      }
      ret.append("\n");

      // 添加批量文件链接
      if (batchFolder != null) {
        String[] adTitleAndLinks = batchFolder.split("\r\n");

        for (String adTitleAndLink : adTitleAndLinks) {

          String[] split = adTitleAndLink.split(" ");
          if (split.length > 1) {
            String title = split[0];
            String url = split[1];
            ret.append("<a href='").append(url).append("'>").append(title).append("</a>\n");
          }
        }
      }

      // 遍历频道，删除旧消息并发送新消息
      for (Row channel : channels) {
        Long channelId = channel.getLong("channel_id");
        String channelName = channel.getStr("channel_name");
        String messageIdStr = channel.getStr("message_id");

        // 删除旧消息
        if (messageIdStr != null && !messageIdStr.isEmpty()) {
          try {
            int messageId = Integer.valueOf(messageIdStr);
            TelegramClientCan.deleteMessage(channelId, messageId);
            log.info("已删除频道 {} 的旧消息 {}", channelId, messageId);
          } catch (Exception e) {
            log.error("{}-{} 删除失败：{}", channelId, channel.getStr("channel_name"), e.getMessage());
            Db.updateBySql("UPDATE bot_channel SET message_id = 0 WHERE channel_id = ?", channelId);
          }
        }

        SendMessage html = SendMessageUtils.html(channelId.toString(), ret.toString(), buttons);
        log.info("push to {},{}", channelId, channelName);
        try {
          // 使用主 bot 发送消息
          Message message = TelegramClientCan.execute(html);
          Integer newMessageId = message.getMessageId();
          Db.updateBySql("UPDATE bot_channel SET message_id = ? WHERE channel_id = ?", newMessageId, channelId);
          log.info("{}-{} 发送成功，消息ID：{}", channelId, channelName, newMessageId);

          model.setContent("推送消息成功:" + "channelId:" + channelId + ",channelName:" + channelName + ",newMessageId:" + newMessageId);
          model.setWarningName("推送消息成功");
          String text = NotificationTemplate.format(model);
          Telegram.use().sendMessage(chatId, text);

        } catch (Exception e) {

          log.error("{}-{} 发送失败：{}", channelId, channelName, e.getCause());
          Db.updateBySql("UPDATE bot_channel SET message_id = 0,car_id=0 WHERE channel_id = ?", channelId);

          String table = MarkdownTableUtils.toItems(channel);
          model.setContent("推送消息失败:" + e.getMessage() + "\n" + table);
          model.setWarningName("推送消息失败");
          String text = NotificationTemplate.format(model);
          Telegram.use().sendMessage(chatId, text);

          // 2. 尝试让机器人退出频道或群组
          try {
            LeaveChat leaveChat = LeaveChat.builder().chatId(channelId.toString()).build();
            TelegramClientCan.execute(leaveChat);
            log.info("机器人已成功退出频道/群组 {}", channelId);
            model.setContent("机器人已成功退出频道/群组 " + channelId);
            text = NotificationTemplate.format(model);
            Telegram.use().sendMessage(chatId, text);

          } catch (Exception leaveException) {
            log.error("机器人退出频道/群组 {} 失败：{}", channelId, leaveException.getMessage());
            model.setWarningName("退出群组/频道");
            model.setContent("机器人退出频道/群组 失败" + channelId + leaveException.getMessage());
            text = NotificationTemplate.format(model);
            Telegram.use().sendMessage(chatId, text);
          }

        }
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    log.info("==================进行互推完成==================");
  }

  

  private ReplyKeyboard buildButtons(String url, String button1) {
    // 构建带有 URL 的按钮
    List<InlineKeyboardRow> rows = new ArrayList<>();
    if (button1 != null) {
      String[] split = button1.split(" ");
      rows.add(new InlineKeyboardRow(InlineKeyboardButtonUtils.url(split[0], split[1])));
    }
    InlineKeyboardButton keyboardButton = InlineKeyboardButtonUtils.url("🚒加入互推", TelegramBotUtils.getBotUrl());
    InlineKeyboardRow inlineKeyboardRow = new InlineKeyboardRow(keyboardButton);

    rows.add(inlineKeyboardRow);

    ReplyKeyboard inlineKeyboard = new InlineKeyboardMarkup(rows);
    return inlineKeyboard;
  }

}
