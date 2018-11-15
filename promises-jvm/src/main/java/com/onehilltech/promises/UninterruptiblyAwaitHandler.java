package com.onehilltech.promises;

import java.util.concurrent.locks.Condition;

public class UninterruptiblyAwaitHandler <T> extends AwaitHandler <T>
{
  @Override
  protected void await (Condition condition)
  {
    condition.awaitUninterruptibly ();
  }
}
