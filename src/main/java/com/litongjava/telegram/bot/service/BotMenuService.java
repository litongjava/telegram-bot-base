package com.litongjava.telegram.bot.service;

import java.util.ArrayList;
import java.util.List;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.jfinal.kit.Kv;
import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.litongjava.db.SqlPara;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.model.page.Page;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.fetcher.TelegramChatInfoFetcher;
import com.litongjava.telegram.utils.AnswerCallbackUtils;
import com.litongjava.telegram.utils.EditMessageUtils;
import com.litongjava.telegram.utils.TelegramBotUtils;
import com.litongjava.telegram.vo.TelegramChatInfo;
import com.litongjava.template.SqlTemplates;
import com.litongjava.tio.utils.environment.EnvUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotMenuService {

  public void channel(Update update, String data) {
    String chatType = "supergroup";
    String[] split = data.split("_");
    int pageNumber = 1;
    if (split.length > 1) {
      chatType = split[0];
      pageNumber = Integer.parseInt(split[2]);
    } else {
      chatType = split[0];
    }

    String sql = SqlTemplates.get("convoy.page");
    SqlPara sqlPara = SqlPara.by(sql).addPara(chatType);

    Page<Row> page = Db.paginate(pageNumber, 20, sqlPara);
    int totalRow = page.getTotalRow();
    List<Row> results = page.getList();
    int totalPages = (int) Math.ceil((double) totalRow / 20);
    int currentPage = pageNumber;

    String messageText = Engine.use().getTemplate("hutujiqiren_menu_top.html").renderToString((Kv.by("chat_type", chatType)));

    sendResults(update, results, currentPage, totalPages, messageText, chatType);
  }

  public String carList(Row carRow, Long carId, int joinCount) {
    String sql;
    double num = carRow.getDouble("num");
    String number = String.format("%.2f", num / 1000);
    String dau = String.format("%.2f", num / 10000);

    Kv kv = carRow.toKv();
    kv.set("number", number);
    kv.set("dau", dau);

    String text = Engine.use().getTemplate("hutujiqiren_menu_item.html").renderToString(kv);
    text += "\r\n";

    if (joinCount > 0) {
      sql = "select channel_id,channel_name,count,url from bot_channel where car_id=?";
      log.info("id:{}", carId);
      List<Row> rows = Db.find(sql, carId);

      for (int i = 0; i < rows.size(); i++) {
        Row row = rows.get(i);
        String channel_name = row.getStr("channel_name");
        String url = row.getStr("url");
        int channelCount = row.getInt("count");
        String format = "%d. <a href='%s'>%s</a>-%s";
        String string = String.format(format, i + 1, url, channel_name, channelCount);
        text += (string + "\r\n");
      }
    }
    return text;
  }

  private void sendResults(Update update, List<Row> results, int currentPage, int totalPages, String messageText, String chatType) {
    CallbackQuery callbackQuery = update.getCallbackQuery();
    Long chatId = callbackQuery.getMessage().getChatId();
    Integer messageId = callbackQuery.getMessage().getMessageId();

    List<InlineKeyboardButton> buttons = new ArrayList<>();

    // 构建 "频道车队" 和 "群组车队" 按钮
    if ("channel".equals(chatType)) {
      buttons.add(InlineKeyboardButton.builder().text("✔️频道车队").callbackData("channel").build());
      buttons.add(InlineKeyboardButton.builder().text("群组车队").callbackData("supergroup").build());
    } else {
      buttons.add(InlineKeyboardButton.builder().text("频道车队").callbackData("channel").build());
      buttons.add(InlineKeyboardButton.builder().text("✔️群组车队").callbackData("supergroup").build());
    }

    for (Row result : results) {
      long id = result.getLong("id");
      String name = result.getStr("name");
      int joinCount = result.getInt("join_count");
      int count = result.getInt("count");
      int num = result.getInt("num");

      if (joinCount == count) {
        name = "🈵" + name;
      } else {
        name = "🉑" + name;
      }

      String numStr = num >= 1000 ? (num / 1000) + "k" : String.valueOf(num);
      String buttonText = String.format("%s组(%s)-[%d/%d]", name, numStr, joinCount, count);
      String callbackData = "car_" + id;
      buttons.add(InlineKeyboardButton.builder().text(buttonText).callbackData(callbackData).build());
    }

    // 分页按钮
    if (currentPage > 1) {
      String string = "page_" + (currentPage - 1);
      if ("channel".equals(chatType)) {
        string = "channel_" + string;
      } else {
        string = "supergroup_" + string;
      }
      buttons.add(InlineKeyboardButton.builder().text("◀️上一页").callbackData(string).build());
    }
    if (currentPage < totalPages) {
      String string = "page_" + (currentPage + 1);
      if ("channel".equals(chatType)) {
        string = "channel_" + string;
      } else {
        string = "supergroup_" + string;
      }
      buttons.add(InlineKeyboardButton.builder().text("下一页▶️").callbackData(string).build());
    }

    // "🏠首页" 按钮
    buttons.add(InlineKeyboardButton.builder().text("🏠首页").callbackData("home").build());

    InlineKeyboardMarkup inlineKeyboard = buildMenu(buttons, 2);

    EditMessageText editMessage = EditMessageUtils.html(chatId, messageId, messageText, inlineKeyboard);

    TelegramClientCan.execute(editMessage);
  }

  private InlineKeyboardMarkup buildMenu(List<InlineKeyboardButton> buttons, int nCols) {
    List<InlineKeyboardRow> rows = new ArrayList<>();
    for (int i = 0; i < buttons.size(); i += nCols) {
      int end = Math.min(i + nCols, buttons.size());
      List<InlineKeyboardButton> subList = buttons.subList(i, end);
      InlineKeyboardRow inlineKeyboardRow = new InlineKeyboardRow(subList);
      rows.add(inlineKeyboardRow);
    }
    return new InlineKeyboardMarkup(rows);
  }

  public void home(Update update, String data) {
    CallbackQuery callbackQuery = update.getCallbackQuery();
    Chat chat = callbackQuery.getMessage().getChat();
    Long chatId = chat.getId();
    Integer messageId = callbackQuery.getMessage().getMessageId();

    long fromUserId = 0;
    String fromFirstName = null;
    User user = callbackQuery.getFrom();
    if (user != null) {
      fromUserId = user.getId();
      fromFirstName = user.getFirstName();
    }

    Template template = Engine.use().getTemplate("hutujiqiren_welcome.html");
    String bot_name = EnvUtils.getStr("bot.name");
    Kv kv = Kv.by("bot_name", bot_name).set("tg_id", fromUserId).set("tg_name", fromFirstName);
    String welcomeMessage = template.renderToString(kv);

    // 构建按钮
    String url = TelegramBotUtils.getBotStartUrl();
    InlineKeyboardButton urlButton = InlineKeyboardButton.builder().url(url).text("⛽邀请机器人进入频道").build();
    InlineKeyboardButton callbackButton = InlineKeyboardButton.builder().callbackData("channel").text("🚒申请加入车队").build();

    InlineKeyboardRow inlineKeyboardRow1 = new InlineKeyboardRow(urlButton);
    InlineKeyboardRow inlineKeyboardRow2 = new InlineKeyboardRow(callbackButton);

    List<InlineKeyboardRow> rows = List.of(inlineKeyboardRow1, inlineKeyboardRow2);
    InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(rows);

    TelegramClientCan.execute(EditMessageUtils.html(chatId, messageId, welcomeMessage, inlineKeyboard));
  }

  public void car(Update update, String data) {
    CallbackQuery callbackQuery = update.getCallbackQuery();
    Long chatId = callbackQuery.getMessage().getChatId();
    Integer messageId = callbackQuery.getMessage().getMessageId();

    String[] split = data.split("_");
    Long id = Long.parseLong(split[1]);
    String sql = "select name,join_count,count,chat_type,num from convoy where id=?";
    Row carRow = Db.findFirst(sql, id);
    int joinCount = carRow.getInt("join_count");
    String text = carList(carRow, id, joinCount);

    int count = carRow.getInt("count");
    String chatType = carRow.getStr("chat_type");
    List<InlineKeyboardButton> buttons = new ArrayList<>();
    if (joinCount == count) {
      InlineKeyboardButton back = InlineKeyboardButton.builder().text("车队已满|返回").callbackData(chatType).build();
      buttons.add(back);
    } else {
      String callbackString = "join " + chatType + " " + id;
      InlineKeyboardButton apply = InlineKeyboardButton.builder().text("申请加入").callbackData(callbackString).build();
      InlineKeyboardButton back = InlineKeyboardButton.builder().text("返回").callbackData(chatType).build();
      buttons.add(apply);
      buttons.add(back);
    }

    InlineKeyboardMarkup inlineKeyboard = buildMenu(buttons, 2);

    EditMessageText editMessage = EditMessageText.builder().chatId(chatId.toString()).messageId(messageId).text(text).parseMode("HTML").disableWebPagePreview(true).replyMarkup(inlineKeyboard).build();

    TelegramClientCan.execute(editMessage);
  }

  public void join(Update update, String data) {
    CallbackQuery callbackQuery = update.getCallbackQuery();
    Long chatId = callbackQuery.getMessage().getChatId();
    Integer messageId = callbackQuery.getMessage().getMessageId();
    long userId = callbackQuery.getFrom().getId();

    String[] split = data.split(" ");
    String chatType = split[1];
    Long id = Long.parseLong(split[2]);

    Long userCount = Db.queryLong("select count(1) from user_role where tg_id=? and role='black'", userId);
    if (userCount > 0) {
      String message = "⚠️由于您之前的违法行为，已被封禁无法加入";
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), message));
      return;
    }

    String sql = SqlTemplates.get("bot_channel.find_user_channel");
    List<Row> rows = Db.find(sql, userId, chatType);
    String chatTypeString = "频道";
    if (!"channel".equals(chatType)) {
      chatTypeString = "群组";
    }

    if (rows.size() == 0) {
      String message = "⚠️您的" + chatTypeString + "未邀请机器人作为管理员，请先邀请机器人 或者 已经在申请中";
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), message));

      return;
    }

    List<InlineKeyboardRow> buttonRows = new ArrayList<>();

    for (Row row : rows) {
      Long channel_id = row.getLong("channel_id");
      String channel_name = row.getStr("channel_name");
      Integer count = row.getInt("count");
      String text = channel_name + " " + (count / 1000) + "K";
      String callbackString = "apply " + chatType + " " + id + " " + channel_id;
      InlineKeyboardButton apply = InlineKeyboardButton.builder().text(text).callbackData(callbackString).build();

      InlineKeyboardRow rowButtons = new InlineKeyboardRow(apply);
      buttonRows.add(rowButtons);
    }

    InlineKeyboardButton back = InlineKeyboardButton.builder().text("返回").callbackData(chatType).build();
    buttonRows.add(new InlineKeyboardRow(back));

    InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(buttonRows);

    sql = "select name,join_count,count,chat_type,num from convoy where id=?";
    Row carRow = Db.findFirst(sql, id);
    int joinCount = carRow.getInt("join_count");
    String text = carList(carRow, id, joinCount);

    EditMessageText editMessage = EditMessageText.builder().chatId(chatId.toString()).messageId(messageId)
        //
        .text(text).parseMode("HTML").replyMarkup(inlineKeyboard).build();

    TelegramClientCan.execute(editMessage);
  }

  public void applyJoin(Update update, String data) {
    CallbackQuery callbackQuery = update.getCallbackQuery();
    long userIdLong = callbackQuery.getFrom().getId();
    String username = callbackQuery.getFrom().getUserName();

    String[] split = data.split(" ");
    if (split.length < 4) {
      AnswerCallbackUtils.alert(callbackQuery.getId(), "⚠️数据格式错误，请重试。");
      return;
    }

    String chatType = split[1];
    String carId = split[2];
    String chatId = split[3];

    String convoySql = "SELECT name, join_count, count, num FROM convoy WHERE id = ?";
    Row convoy = Db.findFirst(convoySql, carId);
    if (convoy == null) {
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "⚠️未找到相关车队信息。"));
      return;
    }

    String name = convoy.getStr("name");
    int joinCount = convoy.getInt("join_count");
    int count = convoy.getInt("count");
    int num = convoy.getInt("num");

    String blacklistSql = "SELECT COUNT(1) FROM user_role WHERE tg_id = ? AND role = 'black'";
    Long blacklistCount = Db.queryLong(blacklistSql, userIdLong);
    if (blacklistCount != null && blacklistCount > 0) {
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "⚠️由于您之前的违法行为，已被封禁无法加入"));
      return;
    }

    if (joinCount >= count) {
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "❌" + name + "车队已满"));
      return;
    }

    String channelSql = "SELECT channel_id, channel_name, url, status FROM bot_channel WHERE channel_id = ?";
    Row channelRow = Db.findFirst(channelSql, chatId);
    if (channelRow == null) {
      AnswerCallbackUtils.alert(callbackQuery.getId(), "⚠️未找到相关频道信息。");
      return;
    }

    Long channelId = channelRow.getLong("channel_id");
    String channelName = channelRow.getStr("channel_name");
    String url = channelRow.getStr("url");
    int status = channelRow.getInt("status");

    TelegramChatInfo chatInfo = TelegramChatInfoFetcher.getChatUrl(url);
    int chatCount = chatInfo.getChatCount();

    if (EnvUtils.isDev()) {
      if (!channelId.equals(-1002446428074L)) {
        if (chatCount < num) {
          TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "❌不满足人数要求,申请失败"));
          log.info("{},{} 不满足人数要求,申请失败", channelId, channelName);
        }
        return;
      }
    } else {
      if (chatCount < num) {
        TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "❌不满足人数要求,申请失败"));
        log.info("{},{} 不满足人数要求,申请失败", channelId, channelName);
        return;
      }
    }

    if (status == 1) {
      TelegramClientCan.execute(AnswerCallbackUtils.alert(callbackQuery.getId(), "⚠️请勿重复申请"));
      return;
    }

    String updateSqlStr = "UPDATE bot_channel SET count = ?, status = 1 WHERE channel_id = ?";
    Db.updateBySql(updateSqlStr, chatCount, chatId);

    List<InlineKeyboardRow> buttonRows = new ArrayList<>();
    InlineKeyboardRow approveButtons = new InlineKeyboardRow();
    approveButtons.add(InlineKeyboardButton.builder().text("✅审批通过").callbackData("审批通过 " + userIdLong + " " + chatType + " " + carId + " " + chatId).build());
    approveButtons.add(InlineKeyboardButton.builder().text("❌审批不通过").callbackData("审批不通过 " + userIdLong + " " + chatType + " " + carId + " " + chatId).build());

    buttonRows.add(approveButtons);

    InlineKeyboardRow blacklistButton = new InlineKeyboardRow();
    blacklistButton.add(InlineKeyboardButton.builder().text("⛔加入申请黑名单").callbackData("审批黑名单 " + userIdLong + " " + chatType + " " + carId + " " + chatId).build());
    buttonRows.add(blacklistButton);

    InlineKeyboardMarkup replyMarkup = new InlineKeyboardMarkup(buttonRows);

    String approvalMessage = "<b>申请加入车队:</b>\n"
        //
        + "车队类型：" + chatType + "-" + name + "\n" + "人数要求：" + (num / 1000) + "k+ \n" + "日浏览量：" + ((double) num / 1000) + "k+ \n"
        //
        + "申请名称：" + channelName + "\n" + "申请链接：" + url + "\n" + "申请人数：" + (chatCount / 1000) + "k\n"
        //
        + "申请人ID：" + userIdLong + "\n" + "申请人用户名：@" + (username != null ? username : "无");

    Long adminGroupId = EnvUtils.getLong("bot.admin.group");

    SendMessage sendToAdmin = SendMessage.builder().chatId(adminGroupId.toString()).text(approvalMessage).parseMode("HTML").replyMarkup(replyMarkup).build();

    TelegramClientCan.execute(sendToAdmin);

    String text = "✅已申请加入" + name + "，请等待审核，审核后会收到通知";
    SendMessage sendToUser = SendMessage.builder().chatId(userIdLong + "").text(text).build();
    TelegramClientCan.execute(sendToUser);

    AnswerCallbackQuery answer = AnswerCallbackUtils.alert(callbackQuery.getId(), "申请已提交,请等待审批");
    TelegramClientCan.execute(answer);
  }

}
