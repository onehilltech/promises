package com.onehilltech.promises;

import java.util.concurrent.Executor;

/**
 * Interface for rejecting a promise. The promise implementation has the option
 * of chaining another promise that will be used to get the value for resolve handlers
 * later in the chain.
 */
public class OnRejectedExecutor <T>
{
  private final OnRejected onRejected_;

  static <T> OnRejectedExecutor <T> wrapOrNull (OnRejected onRejected)
  {
    return onRejected != null ? new OnRejectedExecutor <> (onRejected) : null;
  }

  OnRejectedExecutor (OnRejected onRejected)
  {
    this.onRejected_ = onRejected;
  }

  void execute (Executor executor, final Throwable reason, final ContinuationPromise <T> continuation)
  {
    executor.execute (() -> this.execute (reason, continuation));
  }

  @SuppressWarnings ("unchecked")
  protected void execute (final Throwable reason, final ContinuationPromise <T> continuation)
  {
    try
    {
      Promise promise = this.onRejected_.onRejected (reason);
      continuation.continueWith (promise);
    }
    catch (Exception e)
    {
      continuation.continueWith (e);
    }
  }
}
