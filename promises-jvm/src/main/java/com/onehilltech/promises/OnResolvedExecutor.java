package com.onehilltech.promises;

import java.util.concurrent.Executor;

/**
 * Interface for resolving a promise. The promise implementation has the option
 * of chaining another promise that will be used to get the value for resolve handlers
 * later in the chain.
 *
 * We use a wrapper here so we can support lambda expressions for OnResolved, which
 * is an interface.
 *
 * @param <T>
 */
public class OnResolvedExecutor <T, U>
{
  private OnResolved <T, U> onResolved_;

  static <T, U> OnResolvedExecutor <T, U> wrapOrNull (OnResolved <T, U> onResolved)
  {
    return onResolved != null ? new OnResolvedExecutor<> (onResolved) : null;
  }

  OnResolvedExecutor (OnResolved <T, U> onResolved)
  {
    this.onResolved_ = onResolved;
  }

  @SuppressWarnings ("unchecked")
  void execute (Executor executor, Object objValue, final ContinuationPromise continuation)
  {
    final T value = (T)objValue;
    executor.execute (new Runnable ()
    {
      @Override
      public void run ()
      {
        execute (value, continuation);
      }
    });
  }

  void execute (Executor executor, final Throwable reason, final ContinuationPromise continuation)
  {
    executor.execute (new Runnable ()
    {
      @Override
      public void run ()
      {
        continuation.continueWith (reason);
      }
    });
  }

  @SuppressWarnings ("unchecked")
  void execute (T value, ContinuationPromise continuation)
  {
    try
    {
      Promise promise = this.onResolved_.onResolved (value);
      continuation.continueWith (promise);
    }
    catch (Throwable e)
    {
      continuation.continueWith (e);
    }
  }
}
