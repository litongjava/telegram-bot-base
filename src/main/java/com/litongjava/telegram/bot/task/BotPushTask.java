package com.litongjava.telegram.bot.task;


import org.quartz.JobExecutionContext;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.telegram.bot.service.BotPushService;
import com.litongjava.tio.utils.quartz.AbstractJobWithLog;

public class BotPushTask extends AbstractJobWithLog {
  @Override
  public void run(JobExecutionContext context) throws Exception {
    BotPushService botPushService = Aop.get(BotPushService.class);
    botPushService.push();
  }
}