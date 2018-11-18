package com.onehilltech.promises;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.onehilltech.promises.Promise.ignoreReason;
import static com.onehilltech.promises.Promise.rejected;
import static com.onehilltech.promises.Promise.resolved;
import static com.onehilltech.promises.Promise.value;
import static com.onehilltech.promises.Promise.await;

public class PromiseTest
{
  private final Object lock_ = new Object ();
  private boolean isComplete_;

  @Before
  public void setup ()
  {
    this.isComplete_ = false;
  }

  @Test
  public void testTypeChanges ()
  {
    int start = 5;

    Promise.resolve (start)
           .then (value -> {
             Assert.assertEquals (Integer.class, value.getClass ());
             return value (value);
           })
           .then (value -> {
             Assert.assertEquals (Integer.class, value.getClass ());
             return value ("Hello, World!");
           })
           .then (resolved (str -> Assert.assertEquals (String.class, str.getClass ())));
  }

  @Test
  public void testThenResolve () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise <Integer> p = Promise.resolve (7);

      p.then (resolved (value -> {
        this.isComplete_ = true;
        Assert.assertEquals (7, (int)value);
        Assert.assertEquals (Integer.class, value.getClass ());

        synchronized (lock_)
        {
          this.lock_.notify ();
        }
      }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
      Assert.assertTrue (p.isResolved ());
    }
  }

  @Test
  public void testThenReject () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise <Integer> p = Promise.reject (new IllegalStateException ());

      p._catch (rejected (reason -> {
        this.isComplete_ = true;

        synchronized (lock_)
        {
          Assert.assertEquals (IllegalStateException.class, reason.getClass ());
          this.lock_.notify ();
        }
      }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
      Assert.assertTrue (p.isRejected ());
    }
  }

  @Test
  public void testMultipleThen () throws Exception
  {
    final Promise <Integer> p1 = new Promise<> (settlement -> {
      try
      {
        Thread.sleep (30);
        settlement.resolve (1);
      }
      catch (Exception e)
      {
        settlement.reject (e);
      }
    });

    final Promise <Integer> p2 = new Promise<> (settlement -> {
      try
      {
        Thread.sleep (30);
        settlement.resolve (2);
      }
      catch (Exception e)
      {
        settlement.reject (e);
      }
    });

    final Promise <Integer> p3 = new Promise<> (settlement -> {
      try
      {
        Thread.sleep (30);
        settlement.resolve (3);
      }
      catch (Exception e)
      {
        settlement.reject (e);
      }
    });

    final Promise <Integer> p4 = new Promise<> (settlement -> {
      try
      {
        Thread.sleep (30);
        settlement.resolve (4);
      }
      catch (Exception e)
      {
        settlement.reject (e);
      }
    });

    synchronized (this.lock_)
    {
      p1.then (value -> {
        Assert.assertEquals (1, value.intValue ());
        return p2;
      }).then (value -> {
        Assert.assertEquals (2, value.intValue ());
        return p3;
      }).then (value -> {
        Assert.assertEquals (3, value.intValue ());
        return p4;
      }).then (resolved (value -> {
        Assert.assertEquals (4, value.intValue ());

        this.isComplete_ = true;

        synchronized (lock_)
        {
          this.lock_.notify ();
        }
      }))
        ._catch (rejected (reason -> Assert.fail ()));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testAll () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise<List<Object>> p =
          Promise.all (
              new Promise<> (settlement -> settlement.resolve (10)),
              new Promise<> (settlement -> settlement.resolve (20))
          );

      p.then (resolved (values -> {
        Assert.assertEquals (2, values.size ());

        Assert.assertEquals (10, values.get (0));
        Assert.assertEquals (20, values.get (1));

        this.isComplete_ = true;

        synchronized (this.lock_)
        {
          this.lock_.notify ();
        }
      }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testAllAsContinuation () throws Exception
  {
    synchronized (this.lock_)
    {
      // Create a LOT of promises.
      final ArrayList<Promise <?>> promises = new ArrayList<> ();

      for (int i = 0; i < 7; ++ i)
      {
        final int value = 10 * i;

        Promise <Integer> p = new Promise<> (settlement -> {
          try
          {
            Thread.sleep (30);
            settlement.resolve (value);
          }
          catch (Exception e)
          {
            settlement.reject (e);
          }
        });

        promises.add (p);
      }

      final Promise <Integer> start = new Promise<> (settlement -> {
        try
        {
          Thread.sleep (40);
          settlement.resolve (20);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      });

      final Promise <Integer> middle = new Promise<> (settlement -> {
        try
        {
          Thread.sleep (40);
          settlement.resolve (20);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      });

      start.then (value -> middle)
           .then (value -> Promise.all (promises))
           .then (resolved (results -> {
             this.isComplete_ = true;

             Assert.assertEquals (promises.size (), results.size ());

             for (int i = 0; i < results.size (); ++ i)
               Assert.assertEquals (10 * i, results.get (i));

             synchronized (lock_)
             {
               lock_.notify ();
             }
           }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testStaticResolve ()
  {
    Promise <Integer> p = Promise.resolve (7);
    Assert.assertEquals (Promise.Status.Resolved, p.getStatus ());
  }

  @Test
  public void testStaticReject ()
  {
    Promise <?> p = Promise.reject (new IllegalStateException ());
    Assert.assertEquals (Promise.Status.Rejected, p.getStatus ());
  }

  @Test
  public void testRejectOnly () throws Exception
  {
    Promise.reject (new IllegalStateException ())
           ._catch (rejected (reason -> {
             this.isComplete_ = true;
             Assert.assertEquals (reason.getClass (), IllegalStateException.class);

             synchronized (this.lock_)
             {
               this.lock_.notify ();
             }
           }));

    synchronized (this.lock_)
    {
      this.lock_.wait (5000);
    }

    Assert.assertTrue (this.isComplete_);
  }

  @Test
  public void testPromiseChain () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve ("Hello, World")
             .then (str -> {
               Assert.assertEquals ("Hello, World", str);
               return value (10);
             })
             .then (value -> {
               // n is of type long, but assertEquals has ambiguous calls.
               Assert.assertEquals (10, value.intValue ());

               synchronized (this.lock_)
               {
                 this.isComplete_ = true;
                 this.lock_.notify ();
               }

               return null;
             });

      this.lock_.wait ();

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testPromiseChainNoContinuation () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve ("Hello, World")
             .then (resolved (str -> Assert.assertEquals ("Hello, World", str)))
             .then (resolved (value -> {
               Assert.assertNull (value);
               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testBubbleRejection () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ("GREAT"))
             .then (resolved (value -> Assert.fail ()))
             .then (resolved (value -> Assert.fail ()))
             ._catch (rejected (reason -> {
               Assert.assertEquals (IllegalStateException.class, reason.getClass ());
               Assert.assertEquals ("GREAT", reason.getLocalizedMessage ());

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testStopAtFirstCatch () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ("GREAT"))
             .then (resolved (value -> Assert.fail ()))
             .then (resolved (value -> Assert.fail ()))
             ._catch (rejected (reason -> {
               Assert.assertEquals (IllegalStateException.class, reason.getClass ());
               Assert.assertEquals ("GREAT", reason.getLocalizedMessage ());

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }))
             ._catch (rejected (reason -> Assert.fail ()));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }


  @Test
  public void testThenAfterCatchWithContinuationValue () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ())
             ._catch (reason -> {
               Assert.assertEquals (IllegalStateException.class, reason.getClass ());
               return value (10);
             })
             .then (resolved (value -> {
               Assert.assertEquals (10, value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testThenAfterRejectShouldNotWork () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ())
             .then (resolved (value -> {
               Assert.assertNull (value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (100);

      Assert.assertFalse (this.isComplete_);
    }
  }

  @Test
  public void testRejectCatchThen () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ())
             ._catch (rejected (reason -> Assert.assertEquals (IllegalStateException.class, reason.getClass ())))
             .then (resolved (value -> {
               Assert.assertNull (value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testResolveSkipCatchThen () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve (50)
             .then (n -> value (30))
             ._catch (rejected (reason -> Assert.fail ()))
             .then (resolved (value -> {
               Assert.assertNull (value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testThenAfterCatch () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.reject (new IllegalStateException ())
             ._catch (rejected (reason -> Assert.assertEquals (IllegalStateException.class, reason.getClass ())))
             .then (resolved (value -> {
               Assert.assertNull (value);

               isComplete_ = true;

               synchronized (lock_)
               {
                 lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testRaceEmpty () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.race (new ArrayList<Promise<Integer>> ())
             .then (resolved (value -> {
               Assert.assertNull (value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testRace () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise<Integer> p =
          Promise.race (
              new Promise<> (settlement -> {
                {
                  try
                  {
                    Thread.sleep (500);
                    settlement.resolve (10);
                  }
                  catch (InterruptedException e)
                  {
                    settlement.reject (e);
                  }
                }
              }),
              new Promise<> (settlement -> {
                {
                  try
                  {
                    Thread.sleep (300);
                    settlement.resolve (20);
                  }
                  catch (InterruptedException e)
                  {
                    settlement.reject (e);
                  }
                }
              }),
              new Promise<> (settlement -> {
                {
                  try
                  {
                    Thread.sleep (600);
                    settlement.resolve (30);
                  }
                  catch (InterruptedException e)
                  {
                    settlement.reject (e);
                  }
                }
              })
          );

      p.then (resolved (value -> {
        Assert.assertEquals (20, (int) value);

        Assert.assertFalse (isComplete_);
        isComplete_ = true;

        synchronized (lock_)
        {
          lock_.notify ();
        }
      }))
       ._catch (rejected (reason -> Assert.fail ()));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testResolveCatchIgnoreThen () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve (5)
             ._catch (ignoreReason)
             .then (resolved (value -> {
               Assert.assertNull (value);

               this.isComplete_ = true;

               synchronized (this.lock_)
               {
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testCancel () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise<Integer> p1 = new Promise<> (settlement -> {
        try
        {
          Thread.sleep (300);
        }
        catch (InterruptedException e)
        {
          this.isComplete_ = true;
        }
      });

      Thread.sleep (100);
      p1.cancel (true);
      p1.then (resolved (value -> this.isComplete_ = false));

      this.lock_.wait (100);

      Assert.assertTrue (p1.isCancelled ());
      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testAwaitResolved ()
      throws Throwable
  {
    int result = await (this.makeSimplePromise (5, 2000));
    Assert.assertEquals (result, 5);
  }

  @Test
  public void testAwaitRejected ()
  {
    try
    {
      await (this.makeSimplePromise (new IllegalStateException ("REJECTED"), 2000));
      Assert.fail ("The promise should have been rejected");
    }
    catch (Throwable e)
    {
      Assert.assertEquals ("REJECTED", e.getMessage ());
    }
  }

  private <T> Promise <T> makeSimplePromise (final T value, final long sleep)
  {
    return new Promise<> (settlement -> {
      Thread.sleep (sleep);
      settlement.resolve (value);
    });
  }

  private Promise <Integer> makeSimplePromise (final Throwable reason, final long sleep)
  {
    return new Promise<> (settlement -> {
      Thread.sleep (sleep);
      settlement.reject (reason);
    });
  }
}
