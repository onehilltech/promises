package com.onehilltech.promises;

import java.util.Date;
import java.util.concurrent.locks.Condition;

public class DeadlineAwaitHandler <T> extends AwaitHandler <T>
{
  private final Date deadline_;

  DeadlineAwaitHandler (Date deadline)
  {
    this.deadline_ = deadline;
  }

  @Override
  protected void await (Condition condition) throws Throwable
  {
    if (!condition.awaitUntil (this.deadline_))
      throw new DeadlineException ();
  }
}
