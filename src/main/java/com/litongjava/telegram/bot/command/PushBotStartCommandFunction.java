package com.litongjava.telegram.bot.command;

import java.util.List;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.jfinal.kit.Kv;
import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.litongjava.telegram.can.TelegramClientCan;
import com.litongjava.telegram.utils.ChatType;
import com.litongjava.telegram.utils.SendMessageUtils;
import com.litongjava.telegram.utils.TelegramBotUtils;
import com.litongjava.tio.utils.environment.EnvUtils;

public class PushBotStartCommandFunction {

  public void index(Update update) {
    Message message = update.getMessage();
    Integer messageId = message.getMessageId();

    Chat chat = message.getChat();
    String type = chat.getType();
    if (!type.equals(ChatType.chat_private)) {
      return;
    }

    // 构建带有 URL 的按钮
    String url = TelegramBotUtils.getBotStartUrl();

    InlineKeyboardButton urlButton = InlineKeyboardButton.builder().url(url).text("⛽邀请机器人进入频道").build();
    InlineKeyboardButton callbackButton = InlineKeyboardButton.builder().callbackData("channel").text("🚒申请加入车队").build();
    InlineKeyboardRow inlineKeyboardRow1 = new InlineKeyboardRow(urlButton);
    InlineKeyboardRow inlineKeyboardRow2 = new InlineKeyboardRow(callbackButton);

    List<InlineKeyboardRow> rows = List.of(inlineKeyboardRow1, inlineKeyboardRow2);
    ReplyKeyboard inlineKeyboard = new InlineKeyboardMarkup(rows);


    Long chatId = chat.getId();
    String fromFirstName = chat.getFirstName();
    Template template = Engine.use().getTemplate("hutujiqiren_welcome.html");

    String group_name = EnvUtils.getStr("bot.group.name");
    String bot_name = EnvUtils.getStr("bot.name");
    Kv kv = Kv.by("bot_name", bot_name).set("tg_id", chatId).set("tg_name", fromFirstName).set("group_name", group_name);

    String welcomeMessage = template.renderToString(kv);

    // 创建回发消息对象，将收到的文本原样发送回去
    SendMessage sendMessage = SendMessageUtils.html(chatId.toString(), welcomeMessage);
    sendMessage.setReplyToMessageId(messageId);
    sendMessage.setReplyMarkup(inlineKeyboard);

    // 使用TelegramClient发送消息
    TelegramClientCan.execute(sendMessage);
  }

}