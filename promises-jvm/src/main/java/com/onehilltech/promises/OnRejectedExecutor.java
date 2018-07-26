package com.onehilltech.promises;

import java.util.concurrent.Executor;

/**
 * Interface for rejecting a promise. The promise implementation has the option
 * of chaining another promise that will be used to get the value for resolve handlers
 * later in the chain.
 */
public class OnRejectedExecutor
{
  private final Promise.OnRejected onRejected_;

  static OnRejectedExecutor wrapOrNull (Promise.OnRejected onRejected)
  {
    return onRejected != null ? new OnRejectedExecutor (onRejected) : null;
  }

  OnRejectedExecutor (Promise.OnRejected onRejected)
  {
    this.onRejected_ = onRejected;
  }

  void execute (Executor executor, final Throwable reason, final ContinuationPromise continuation)
  {
    executor.execute (new Runnable ()
    {
      @Override
      public void run ()
      {
        execute (reason, continuation);
      }
    });
  }

  @SuppressWarnings ("unchecked")
  protected void execute (final Throwable reason, final ContinuationPromise continuation)
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
