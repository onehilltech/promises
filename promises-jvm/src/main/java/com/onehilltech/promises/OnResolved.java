package com.onehilltech.promises;

public interface OnResolved <T, U>
{
  /**
   * Callback handler for a execute promise.
   *
   * @param value The execute value
   * @return Optional promise
   */
  Promise <U> onResolved (T value) throws Throwable;
}
