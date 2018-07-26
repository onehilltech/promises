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

import android.os.Looper;
import android.support.test.annotation.UiThreadTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.onehilltech.promises.Promise.resolved;
import static com.onehilltech.promises.Promise.rejected;
import static com.onehilltech.promises.ResolvedOnUIThread.onUiThread;
import static com.onehilltech.promises.RejectedOnUIThread.onUiThread;

@RunWith(AndroidJUnit4.class)
public class ResolvedOnUIThreadTest
{
  private boolean complete_;
  private final Object lock_ = new Object ();

  @Before
  public void setup ()
  {
    this.complete_ = false;
  }

  @Test
  public void testResolved () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve (10)
             .then (onUiThread (resolved (new ResolveNoReturn<Integer> ()
             {
               @Override
               public void resolveNoReturn (Integer value)
               {
                 boolean isUiThread = Looper.getMainLooper ().getThread ().equals (Thread.currentThread ());
                 Assert.assertTrue (isUiThread);

                 synchronized (lock_)
                 {
                   complete_ = true;
                   lock_.notify ();
                 }
               }
             })))
             ._catch (onUiThread (rejected (new RejectNoReturn () {
               @Override
               public void rejectNoReturn (Throwable reason)
               {

               }
             })));

      this.lock_.wait (5000);

      Assert.assertTrue (this.complete_);
    }
  }

  @UiThreadTest
  @Test
  public void testResolvedOnUiThread ()
  {
    synchronized (this.lock_)
    {
      Promise.resolve (10)
             .then (onUiThread (resolved (new ResolveNoReturn<Integer> ()
             {
               @Override
               public void resolveNoReturn (Integer value)
               {
                 boolean isUiThread = Looper.getMainLooper ().getThread ().equals (Thread.currentThread ());
                 Assert.assertTrue (isUiThread);

                 complete_ = true;
               }
             })))
             ._catch (onUiThread (rejected (new RejectNoReturn () {
               @Override
               public void rejectNoReturn (Throwable reason)
               {

               }
             })));

      Assert.assertTrue (this.complete_);
    }
  }
}
