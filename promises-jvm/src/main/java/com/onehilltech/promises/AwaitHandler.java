package com.onehilltech.promises;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class AwaitHandler <T>
  implements OnResolved <T, Object>, OnRejected
{
  private final Lock lock_ = new ReentrantLock ();

  private final Condition isSettled_ = this.lock_.newCondition ();

  private T value_;

  private Throwable reason_;

  private boolean settled_ = false;

  @Override
  public Promise<Object> onResolved (T value)
  {
    this.settle (value, null);
    return null;
  }

  @Override
  public Promise onRejected (Throwable reason)
  {
    this.settle (null, reason);
    return null;
  }

  /**
   * The promise is settled.
   *
   * @param value           The settled value
   * @param reason          The reason for rejection
   */
  private void settle (T value, Throwable reason)
  {
    this.lock_.lock ();

    try
    {
      // Mark the handler as settled.
      this.settled_ = true;

      this.value_ = value;
      this.reason_ = reason;

      this.isSettled_.signalAll ();
    }
    finally
    {
      this.lock_.unlock ();
    }
  }

  /**
   * Wait for the promise to reach a settlement. When the promises reaches a
   * settlement, the resolved value is returned or an exception is thrown.
   *
   * @return                The resolved value
   * @throws Throwable      The reason for rejection
   */
  final T await () throws Throwable
  {
    this.lock_.lock ();

    try
    {
      // Check if the promise has been settled before locking. If the promise has
      // be settled, then we can just return and not wait.

      if (this.settled_)
      {
        if (this.reason_ != null)
          throw this.reason_;

        return this.value_;
      }

      this.await (this.isSettled_);

      if (this.reason_ != null)
        throw this.reason_;

      return this.value_;
    }
    finally
    {
      this.lock_.unlock ();
    }
  }

  protected void await (Condition condition) throws Throwable
  {
    condition.await ();
  }
}
