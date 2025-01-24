package com.litongjava.telegram.bot.task;

import java.util.ArrayList;
import java.util.List;

import org.quartz.JobExecutionContext;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.tio.utils.quartz.AbstractJobWithLog;

public class ConvoyUpdateTask extends AbstractJobWithLog {

  @Override
  public void run(JobExecutionContext context) throws Exception {
    String sql = "SELECT car_id,count(car_id) FROM `bot_channel` where deleted=0 and car_id!=0 GROUP BY car_id order by car_id;";
    List<Row> rows = Db.find(sql);
    List<Row> updateData = new ArrayList<>();
    for (Row row : rows) {
      Integer car_id = row.getInt("car_id");
      Integer count = row.getInt("count(car_id)");
      updateData.add(Row.by("id", car_id).set("join_count", count));
    }
    Db.batchUpdate("convoy", updateData, 2000);
  }

}
