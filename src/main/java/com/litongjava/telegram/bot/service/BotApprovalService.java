package com.litongjava.telegram.bot.service;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.utils.AnswerCallbackUtils;
import com.litongjava.telegram.utils.EditMessageUtils;
import com.litongjava.telegram.utils.SendMessageUtils;
import com.litongjava.tio.utils.environment.EnvUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotApprovalService {

  // 审批通过 6276672963 channel 1 2446428074
  // 审批不通过 6276672963 channel 1 2446428074
  // 审批黑名单 6276672963 channel 1 2446428074
  public void approvalCall(Update event, String data) {
    Long callbackChatId = event.getCallbackQuery().getMessage().getChat().getId();

    Integer messageId = event.getCallbackQuery().getMessage().getMessageId();
    String callbackQuereyId = event.getCallbackQuery().getId();
    // 解析回调数据
    String[] split = data.split(" ");
    if (split.length < 5) {
      // 数据格式不正确
      AnswerCallbackQuery alert = AnswerCallbackUtils.alert(callbackQuereyId, "⚠️数据格式错误，请重试。");
      TelegramClientCan.execute(alert);
    }

    String approval = split[0];
    String tgId = split[1];
    //String chatType = split[2];
    String carId = split[3];
    String chatId = split[4];

    // 查询车队信息
    String convoySql = "SELECT join_count, count, name FROM convoy WHERE id = ?";
    Row convoy = Db.findFirst(convoySql, carId);
    if (convoy == null) {
      AnswerCallbackQuery alert = AnswerCallbackUtils.alert(chatId, "⚠️未找到相关车队信息。");
      TelegramClientCan.execute(alert);
    }

    int joinCount = convoy.getInt("join_count");
    int count = convoy.getInt("count");
    String name = convoy.getStr("name");

    // 查询频道信息
    String channelSql = "SELECT channel_id, channel_name, url, message_id FROM bot_channel WHERE channel_id = ?";
    Row channelRow = Db.findFirst(channelSql, chatId);
    if (channelRow == null) {
      TelegramClientCan.execute(AnswerCallbackUtils.alert(chatId, "⚠️未找到相关频道信息。"));
    }

    Long channelId = channelRow.getLong("channel_id");
    String channelName = channelRow.getStr("channel_name");
    String url = channelRow.getStr("url");
    //Integer messageId = channelRow.getInt("message_id");

    long tgIdLong = Long.parseLong(tgId);
    log.info("user_id:{}", tgIdLong);
    if ("审批通过".equals(approval)) {
      if (joinCount >= count) {
        // 车队已满，更新状态并通知用户
        String ret = "❌车队" + name + "已满,加入失败\nID：" + channelId + "\n名称：" + channelName + "\n链接：" + url;
        Db.updateBySql("UPDATE bot_channel SET status = 0 WHERE channel_id = ?", chatId);
        SendMessage html = SendMessageUtils.html(tgId, ret);
        TelegramClientCan.execute(html);

        EditMessageText editMessage = EditMessageUtils.html(callbackChatId, messageId, ret);
        TelegramClientCan.execute(editMessage);
      } else {

        Db.updateBySql("UPDATE bot_channel SET car_id = ? WHERE channel_id = ?", carId, chatId);
        Db.updateBySql("UPDATE convoy SET join_count = join_count + 1 WHERE id = ?", carId);
        // 加入成功，更新车队和频道信息
        String ret = "✅加入" + name + "成功\nID：" + channelId + "\n名称：" + channelName + "\n链接：" + url;
        log.info("tgId:{}", tgId);

        TelegramClientCan.execute(SendMessageUtils.html(tgId, ret));
        TelegramClientCan.execute(EditMessageUtils.html(callbackChatId, messageId, ret));
      }
    } else if ("审批不通过".equals(approval)) {
      // 审批不通过，更新状态并通知用户
      String ret = "❌审核不通过\n"
          //
          + "如有疑问请联系 " + EnvUtils.getStr("bot.group.name") + " \n"
          //
          + "申请人：" + tgId + "\n申请车队：" + name + "\n申请名称：" + channelName + "\n链接：" + url;

      Db.updateBySql("UPDATE bot_channel SET status = 0 WHERE channel_id = ?", chatId);

      // 发送提示消息
      TelegramClientCan.execute(SendMessageUtils.html(tgId, ret));
      TelegramClientCan.execute(EditMessageUtils.html(callbackChatId, messageId, ret));

    } else if ("审批黑名单".equals(approval)) {
      // 添加到黑名单，更新状态并通知用户
      Db.updateBySql("INSERT INTO user_role (tg_id, role) VALUES (?, 'black')", tgId);
      Db.updateBySql("UPDATE bot_channel SET status = 0 WHERE channel_id = ?", chatId);
      String ret = "❌已封禁" + tgId + "，剥夺申请权限";
      // TelegramBotClient.sendMessageToUser(client, Long.parseLong(tgId), ret);
      TelegramClientCan.execute(SendMessageUtils.html(tgId, ret));
      TelegramClientCan.execute(EditMessageUtils.html(callbackChatId, messageId, ret));

    } else {
      // 未识别的审批类型
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuereyId, "⚠️未识别的审批类型。"));
    }

  }

  /**
  ❌审核不通过
  如有疑问请联系 @hutuijqr 
  申请人：6276672963
  申请车队：A1
  申请名称：dev_test_channel
  链接：https://t.me/+lOMey_mjtPw2ZDAx
   */
  public void rejectReason(Update event, String content) {
    String text = event.getMessage().getReplyToMessage().getText();
    log.info("content:{}", content);
    String[] split = text.split("\n");
    String tgId = split[2].split("：")[1];
    String lastLine = "不通过原因:" + content;
    TelegramClientCan.execute(SendMessageUtils.html(tgId, text + "\n" + lastLine));
  }
}