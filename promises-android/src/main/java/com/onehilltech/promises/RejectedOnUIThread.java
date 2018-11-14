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
 * Proxy that run the OnRejected handler on the UI thread.
 *
 * If the handler is already on the UI thread, then the handler continues processing
 * the execute value on the same thread. If the handler is not on the UI thread, then
 * it will run on the UI thread.
 *
 * This design make is so the caller does not experience any context switches if the
 * root promise starts on the UI thread, and the handler needs to run on the UI thread.
 */
public class RejectedOnUIThread
{
  /**
   * Factory method that supports using a lambda function. It also removes the need
   * for using the new method so its usage in the Promise statements reads more fluid.
   *
   * @param onRejected      The real handler
   * @return                Promise.OnRejected object
   */
  public static OnRejectedExecutor onUiThread (@NonNull OnRejected onRejected)
  {
    return new Executor (onRejected);
  }

  private static class Executor extends OnRejectedExecutor
  {
    /// Mock continuation promise.
    private ContinuationPromise cont_;

    /// The reason for the failure.
    private Throwable reason_;

    /**
     * Initializing constructor.
     *
     * @param onRejected        The real object
     */
    private Executor (OnRejected onRejected)
    {
      super (onRejected);
    }

    @Override
    void execute (java.util.concurrent.Executor executor, final Throwable reason, final ContinuationPromise continuation)
    {
      if (this.isUiThread ())
      {
        // We are already running on the UI thread. Let's just continue with the
        // continuation promise so the original caller does not see any disruption.

        this.execute (reason, continuation);
      }
      else
      {
        // Schedule the rejection handler to run on the UI thread.
        this.reason_ = reason;
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
      @Override
      public void handleMessage (Message msg)
      {
        Executor onUIThread = (Executor) msg.obj;
        onUIThread.execute (onUIThread.reason_, onUIThread.cont_);
      }
    };
  }
}
