package com.onehilltech.promises;

import java.util.concurrent.locks.Condition;

public class TimeAwaitHandler <T> extends AwaitHandler <T>
{
  private long nanos_;

  TimeAwaitHandler (long nanos)
  {
    this.nanos_ = nanos;
  }

  @Override
  protected void await (Condition condition) throws Throwable
  {
    condition.awaitNanos (this.nanos_);
  }
}
