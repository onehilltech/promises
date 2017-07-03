/*
 * Copyright (c) 2017 One Hill Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.onehilltech.promises;

import android.support.annotation.NonNull;


/**
 * Proxy that run the OnResolved handler on the UI thread.
 */
public class ResolvedOnUIThread <T, U> extends OnUIThread
    implements Promise.OnResolved <T, U>
{
  /**
   * Factory method that supports using a lambda function. It also removes the need
   * for using the new method so its usage in the Promise statements reads more fluid.
   *
   * @param onResolved        The real handler
   * @param <T>               Parameter type of the current value
   * @param <U>               Parameter type of the next value
   * @return                  Promise.OnResolved object
   */
  public static <T, U> Promise.OnResolved <T, U> resolveOnUiThread (@NonNull Promise.OnResolved <T, U> onResolved)
  {
    return new ResolvedOnUIThread<> (onResolved);
  }

  /// The real OnResolved handler.
  private final Promise.OnResolved <T, U> onResolved_;

  /// Mock continuation promise.
  private final ContinuationPromise cont_ = new ContinuationPromise ();

  /// The value of the settlement.
  private T value_;

  /**
   * Initializing constructor.
   *
   * @param onResolved        The real handler
   */
  private ResolvedOnUIThread (Promise.OnResolved <T, U> onResolved)
  {
    this.onResolved_ = onResolved;
  }

  @SuppressWarnings ("unchecked")
  @Override
  public Promise onResolved (T value)
  {
    this.value_ = value;

    this.runOnUiThread ();

    return this.cont_;
  }

  @SuppressWarnings ("unchecked")
  @Override
  protected void run ()
  {
    try
    {
      Promise<?> promise = this.onResolved_.onResolved (this.value_);
      this.cont_.continueWith (promise);
    }
    catch (Exception e)
    {
      this.cont_.continueWith (e);
    }
  }
}
