package com.onehilltech.promises;

/**
 * Specialized implementation of the OnResolved handler that does not have
 * a return value.
 *
 * @param <T>         Value type
 * @param <U>         Continuation value type
 */
public class OnResolvedNoReturn <T, U> implements OnResolved <T, U>
{
  private final ResolveNoReturn<T> resolveNoReturn_;

  OnResolvedNoReturn (ResolveNoReturn<T> resolveNoReturn)
  {
    this.resolveNoReturn_ = resolveNoReturn;
  }

  @Override
  @SuppressWarnings ("unchecked")
  public Promise <U> onResolved (T value) throws Throwable
  {
    this.resolveNoReturn_.resolveNoReturn (value);
    return null;
  }
}
