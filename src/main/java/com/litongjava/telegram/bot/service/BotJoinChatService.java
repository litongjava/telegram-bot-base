package com.litongjava.telegram.bot.service;

import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ChatInviteLink;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;

import com.litongjava.db.OkResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.fetcher.TelegramPeerInfoFetcher;
import com.litongjava.telegram.utils.SendMessageUtils;
import com.litongjava.telegram.utils.TelegramBotUtils;
import com.litongjava.telegram.vo.TelegramPeerInfo;
import com.litongjava.tio.utils.json.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotJoinChatService {
  public void index(Update update) {
    String json = JacksonUtils.toJson(update);
    log.info("json:{}", json);
    ChatMemberUpdated myChatMember = update.getMyChatMember();
    ChatMember oldChatMember = myChatMember.getOldChatMember();
    ChatMember newChatMember = myChatMember.getNewChatMember();
    if (oldChatMember == null && newChatMember == null) {
      return;
    }

    // 判断bot本身的id
    long botId = getBotIdFromMember(newChatMember, oldChatMember);
    if (botId != TelegramBotUtils.getSelfId()) {
      return;
    }

    Chat channelChat = myChatMember.getChat();
    User fromUser = myChatMember.getFrom();

    long channelIdLong = channelChat.getId();
    String type = channelChat.getType();
    String channelTitle = channelChat.getTitle();
    String username = channelChat.getUserName();

    String chatUrl = (username != null && !username.isEmpty()) ? "https://t.me/" + username : "";

    // 判断是机器人自己被添加为管理员还是被删除
    // String oldStatus = oldChatMember.getStatus();
    String newStatus = newChatMember.getStatus();

    // 如果 newStatus = administrator
    if ("left".equals(newStatus) || "kicked".equals(newStatus)) {
      handleLeftEvent(channelIdLong, channelTitle, chatUrl);
    }

    // 如果 newStatus = administrator
    if ("administrator".equals(newStatus) || "member".equals(newStatus)) {
      handleJoinEvent(channelIdLong, channelTitle, chatUrl, fromUser.getId(), type);
    }

  }

  /**
   * 处理加入事件(机器人被添加为管理员)
   */
  private void handleJoinEvent(Long channelIdLong, String channelTitle, String chatUrl, long userIdLong, String gruopType) {
    // 如果没有username，即没有公开链接，无法互推
    if (chatUrl == null || chatUrl.isEmpty()) {
      sendMessageToUser(userIdLong, channelTitle + " 该群组/频道没有公开链接，无法参与互推！");
      return;
    }

    // 创建邀请链接
    ChatInviteLink inviteLink = null;
    // 创建邀请链接请求
    CreateChatInviteLink createChatInviteLink = new CreateChatInviteLink(channelIdLong.toString());

    try {
      // 这里可根据需要设置邀请链接属性，比如setName,setExpireDate,setMemberLimit等
      inviteLink = TelegramClientCan.execute(createChatInviteLink);

    } catch (Exception e) {
      // 创建邀请链接失败
      String errorMessage = "⚠️<a href='" + chatUrl + "'>" + channelTitle + "</a> 创建邀请链接失败, 请赋予机器人添加成员的权限";
      sendHtmlMessageToUser(userIdLong, errorMessage);
      return;
    }

    if (inviteLink == null || inviteLink.getInviteLink() == null) {
      String messageText = "⚠️ 创建邀请链接失败：返回的inviteLink为空";
      sendMessageToUser(userIdLong, messageText);
      return;
    }

    // 向频道发送成功加入消息
    try {
      TelegramClientCan.execute(new SendMessage(String.valueOf(channelIdLong), "🤖互推机器人已成功加入"));
    } catch (Exception e) {
      // 向频道发消息失败
      String errorMessageText = "<a href='" + chatUrl + "'>" + channelTitle + "</a> ⚠️ 向频道发送消息失败，请检查机器人权限。";
      sendHtmlMessageToUser(userIdLong, errorMessageText);
      return;
    }

    // 获取频道成员数量（通过外部工具类TelegramUrlFetcher）
    OkResult<TelegramPeerInfo> okResult = TelegramPeerInfoFetcher.getChatUrl(chatUrl);
    if(!okResult.isOk()) {
      return;
    }
    TelegramPeerInfo telegramPeerInfo = okResult.getV();
    int count = (telegramPeerInfo != null) ? telegramPeerInfo.getCount() : 0;

    // 数据库操作 保存或者更新bot_channel表
    String sql = "SELECT channel_id FROM bot_channel WHERE channel_id = ?";
    try {
      Row row = Db.findFirst(sql, channelIdLong);

      Row saveRow = Row.by("channel_id", channelIdLong).set("access_hash", 0)
          //
          .set("tg_id", userIdLong).set("channel_name", channelTitle).set("role", "administrator")
          //
          .set("chat_type", gruopType).set("count", count).set("url", inviteLink.getInviteLink());

      if (row == null) {
        Db.save("bot_channel", saveRow);
      } else {
        Db.update("bot_channel", "channel_id", saveRow);
      }
    } catch (Exception e) {
      // 保存数据失败
      sendMessageToUser(userIdLong, "保存数据失败");
      return;
    }

    // 向用户发送成功信息
    String userMessageText = "🤖互推机器人已成功加入 <a href='" + chatUrl + "'>" + channelTitle + "</a>";
    sendHtmlMessageToUser(userIdLong, userMessageText);
  }

  /**
   * 处理离开事件(机器人被移除管理员权限或踢出)
   */
  private void handleLeftEvent(long channelIdLong, String channelTitle, String chatUrl) {
    // 查询bot_channel表
    Row botChannelRow = Db.findFirst("SELECT tg_id, car_id FROM bot_channel WHERE channel_id = ?", channelIdLong);
    if (botChannelRow != null) {
      // 删除bot_channel记录
      Db.delete("DELETE FROM bot_channel WHERE channel_id = ?", channelIdLong);

      // 更新convoy表
      String carId = botChannelRow.getStr("car_id");
      if (carId != null && !carId.isEmpty()) {
        Db.updateBySql("UPDATE convoy SET join_count = join_count - 1 WHERE id = ?", carId);
      }

      Long tgId = botChannelRow.getLong("tg_id");
      if (tgId != null) {
        String messageText = "已退出互推，如继续互推需要重新邀请机器人为管理员" + "<a href='" + chatUrl + "'>" + channelTitle + "</a>";
        sendHtmlMessageToUser(tgId, messageText);
      }
    }
  }

  /**
   * 获取bot本身的id，用于区分是否是bot
   * 在old/new_chat_member里是同一个用户，但这里直接返回新成员的user id即可
   */
  private long getBotIdFromMember(ChatMember newChatMember, ChatMember oldChatMember) {
    if (newChatMember != null && newChatMember.getUser() != null) {
      return newChatMember.getUser().getId();
    }
    if (oldChatMember != null && oldChatMember.getUser() != null) {
      return oldChatMember.getUser().getId();
    }
    return 0L;
  }

  /**
   * 向用户发送纯文本消息
   */
  private void sendMessageToUser(long userId, String text) {
    if (userId == 0) {
      return;
    }
    TelegramClientCan.execute(SendMessageUtils.html(String.valueOf(userId), text));
  }

  /**
   * 以HTML格式向用户发送消息
   */
  private void sendHtmlMessageToUser(long userId, String htmlText) {
    if (userId == 0) {
      return;
    }
    TelegramClientCan.execute(SendMessageUtils.html(userId, htmlText));
  }
}
