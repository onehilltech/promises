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
 * Proxy that run the OnRejected handler on the UI thread.
 */
public class RejectedOnUIThread extends OnUIThread
    implements Promise.OnRejected
{
  /**
   * Factory method that supports using a lambda function. It also removes the need
   * for using the new method so its usage in the Promise statements reads more fluid.
   *
   * @param onRejected      The real handler
   * @return                Promise.OnRejected object
   */
  public static Promise.OnRejected onUiThread (@NonNull Promise.OnRejected onRejected)
  {
    return new RejectedOnUIThread (onRejected);
  }

  /// The real OnRejected handler.
  private final Promise.OnRejected onRejected_;

  /// Mock continuation promise.
  private final ContinuationPromise cont_ = new ContinuationPromise ();

  /// The reason for the failure.
  private Throwable reason_;

  /**
   * Initializing constructor.
   *
   * @param onRejected        The real object
   */
  private RejectedOnUIThread (Promise.OnRejected onRejected)
  {
    this.onRejected_ = onRejected;
  }

  @Override
  public Promise onRejected (Throwable reason)
  {
    this.reason_ = reason;

    this.runOnUiThread ();

    return this.cont_;
  }

  @SuppressWarnings ("unchecked")
  @Override
  protected void run ()
  {
    try
    {
      Promise promise = this.onRejected_.onRejected (this.reason_);
      this.cont_.continueWith (promise);
    }
    catch (Exception e)
    {
      this.cont_.continueWith (e);
    }
  }
}
