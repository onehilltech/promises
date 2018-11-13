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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

/**
 * Proxy that run the OnResolved handler on the UI thread.
 *
 * If the handler is already on the UI thread, then the handler continues processing
 * the execute value on the same thread. If the handler is not on the UI thread, then
 * it will run on the UI thread.
 *
 * This design make is so the caller does not experience any context switches if the
 * root promise starts on the UI thread, and the handler needs to run on the UI thread.
 */
public final class ResolvedOnUIThread
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
  public static <T, U> OnResolvedExecutor<T, U> onUiThread (@NonNull OnResolved<T, U> onResolved)
  {
    return new Executor <> (onResolved);
  }

  /**
   * Private implementation of the executor.
   *
   * @param <T>
   * @param <U>
   */
  private static class Executor <T, U> extends OnResolvedExecutor <T, U>
  {
    /// Mock continuation promise.
    private ContinuationPromise cont_ = new ContinuationPromise ();

    /// The value of the settlement.
    private T value_;

    /**
     * Initializing constructor.
     *
     * @param onResolved        The real handler
     */
    Executor (OnResolved<T, U> onResolved)
    {
      super (onResolved);
    }

    @SuppressWarnings ("unchecked")
    @Override
    void execute (java.util.concurrent.Executor executor, Object obj, ContinuationPromise continuation)
    {
      if (this.isUiThread ())
      {
        // We are already running on the UI thread. Let's just continue with the
        // continuation promise so the original caller does not see any disruption.
        T value = (T)obj;
        this.execute (value, continuation);
      }
      else
      {
        // Schedule the rejection handler to run on the UI thread.
        this.value_ = (T)obj;
        this.cont_ = continuation;

        this.runOnUiThread ();
      }
    }

    private boolean isUiThread ()
    {
      return Looper.getMainLooper ().equals (Looper.myLooper ());
    }

    /**
     * Run the handler on the UI thread.
     */
    private void runOnUiThread ()
    {
      Message message = uiHandler_.obtainMessage (0, this);
      message.sendToTarget ();
    }

    /**
     * Implementation of the Looper that runs the handler on the UI thread.
     */
    private static final Handler uiHandler_ = new Handler (Looper.getMainLooper ()) {
      @SuppressWarnings ("unchecked")
      @Override
      public void handleMessage (Message msg)
      {
        Executor onUIThread = (Executor) msg.obj;
        onUIThread.execute (onUIThread.value_, onUIThread.cont_);
      }
    };
  }
}
