package com.onehilltech.promises;

import android.os.Looper;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.onehilltech.promises.RejectedOnUIThread.rejectOnUiThread;

@RunWith(AndroidJUnit4.class)
public class RejectedOnUIThreadTest
{
  private boolean complete_;

  private final Object lock_ = new Object ();

  @Before
  public void setup ()
  {
    this.complete_ = false;
  }

  @Test
  public void testRejectedOnUiThread () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ())
             ._catch (rejectOnUiThread (reason -> {
               boolean isUiThread = Looper.getMainLooper ().getThread ().equals (Thread.currentThread ());
               Assert.assertTrue (isUiThread);

               synchronized (this.lock_)
               {
                 this.complete_ = true;
                 this.lock_.notify ();
               }

               return null;
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.complete_);
    }
  }
}
