package com.litongjava.telegram.bot.service;

import java.util.List;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.telegram.bot.model.BotChannel;
import com.litongjava.telegram.bot.model.Convoy;
import com.litongjava.telegram.can.TelegramClientCan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotChannelService {

  public void assign() {
    String updateSql = "update convoy set join_count=0";
    Db.update(updateSql);
    String sql = "select * from bot_channel";
    String convoy_channel_sql = "select * from convoy where chat_type='channel' order by num desc";

    List<Convoy> channels = Convoy.dao.find(convoy_channel_sql);
    String convoy_supergroup_sql = "select * from convoy where chat_type='supergroup' order by num desc";
    List<Convoy> supergroup = Convoy.dao.find(convoy_supergroup_sql);

    List<BotChannel> rows = BotChannel.dao.find(sql);
    for (BotChannel row : rows) {

      String chatType = row.getChatType();
      Integer count = row.getCount();
      if (count > 1) {
        String channelId = row.getChannelId();
        log.info("channelId:{}", channelId);
        if (chatType.equals("channel")) {
          for (Convoy convoy : channels) {
            if (count > convoy.getNum()) {
              if (convoy.getJoinCount() < 15) {
                update(row, channelId, convoy);
                break;
              }

            }
          }

        } else {
          for (Convoy convoy : supergroup) {
            if (count > convoy.getNum()) {
              if (convoy.getCount() < 15) {
                update(row, channelId, convoy);
                break;
              }

            }
          }
        }
      }
    }
  }

  private void update(BotChannel row, String channelId, Convoy convoy) {
    Boolean isAdmin = row.getIsAdmin();
    if (isAdmin == null) {
      boolean botAdmin = Aop.get(AdminCheckService.class).isBotAdmin(Long.valueOf(channelId));
      if (botAdmin) {
        row.setCarId(convoy.getId());
        row.setIsAdmin(true);
        row.update();
        Integer joinCount = convoy.getJoinCount();
        convoy.setJoinCount(joinCount + 1);
        convoy.update();
      } else {
        //
        Db.deleteById(BotChannel.tableName, "channel_id", row.getChannelId());
        try {
          TelegramClientCan.leaveChat(channelId);
        } catch (Exception e) {
          log.error("Failed to leave chat:{}", channelId);
        }
      }
    } else {
      if (isAdmin) {
        row.setCarId(convoy.getId());
        row.update();
        Integer joinCount = convoy.getJoinCount();
        convoy.setJoinCount(joinCount + 1);
        convoy.update();
      }
    }
  }
  
  public List<Row> getChannels(String carId) {
    String sql = "SELECT channel_id, channel_name, url, car_id, tg_id, message_id FROM bot_channel WHERE delete=0 and car_id = ?";
    List<Row> channels = Db.find(sql, carId);
    return channels;
  }
}
