package com.onehilltech.promises;

public class OnFinallyExecutor <T, U>
{
  static <T, U> OnFinallyExecutor <T, U> wrapOrNull (OnFinally <T, U> onFinally)
  {
    return onFinally != null ? new OnFinallyExecutor<> (onFinally) : null;
  }

  private On
}
