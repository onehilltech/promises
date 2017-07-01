package com.onehilltech.promises;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.onehilltech.promises.Promise.rejected;
import static com.onehilltech.promises.Promise.resolved;
import static com.onehilltech.promises.Promise.ignoreReason;

public class PromiseTest
{
  private final Object lock_ = new Object ();
  private boolean isComplete_;
  private int counter_;

  @Before
  public void setup ()
  {
    this.isComplete_ = false;
    this.counter_ = 0;
  }

  @Test
  public void testThenResolve () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise <Integer> p = new Promise <> (settlement -> settlement.resolve (5));

      p.then (
          resolved (value -> {
            this.isComplete_ = true;
            Assert.assertEquals (5, (int)value);

            synchronized (lock_)
            {
              lock_.notify ();
            }
          }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
      Assert.assertEquals (Promise.Status.Resolved, p.getStatus ());
    }
  }

  @Test
  public void testThenReject () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise <Integer> p = new Promise <> (settlement -> settlement.reject (new IllegalStateException ()));

      p.then (resolved (value -> Promise.resolve (5)),
              rejected (reason -> {
                this.isComplete_ = true;

                synchronized (lock_)
                {
                  Assert.assertEquals (IllegalStateException.class, reason.getClass ());

                  lock_.notify ();
                }
              }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testMultipleThen () throws Exception
  {
    Promise <Integer> p1 = new Promise<> ((settlement) -> {
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

    Promise <Integer> p2 = new Promise<> (settlement -> {
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

    Promise <Integer> p3 = new Promise<> (settlement -> {
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

    Promise <Integer> p4 = new Promise<> (settlement -> {
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
      p1.then (n -> p2)
        .then (n -> p3)
        .then (n -> p4)
        .then (resolved (n -> {
          Assert.assertEquals (4, (int)n);

          this.isComplete_ = true;

          synchronized (this.lock_)
          {
            this.lock_.notify ();
          }
        }))
        ._catch (rejected (reason -> Assert.fail ()));

      this.lock_.wait ();

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
              new Promise<Integer> (settlement -> settlement.resolve (10)),
              new Promise<Integer> (settlement -> settlement.resolve (20)));

      p.then (resolved (value -> {
        Assert.assertEquals (2, value.size ());

        Assert.assertEquals (10, value.get (0));
        Assert.assertEquals (20, value.get (1));

        this.isComplete_ = true;

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
  public void testAllAsContinuation () throws Exception
  {
    synchronized (this.lock_)
    {
      // Create a LOT of promises.
      ArrayList<Promise <?>> promises = new ArrayList<> ();

      for (int i = 0; i < 7; ++ i)
      {
        Promise <Integer> p = new Promise<> ((settlement) -> {
          try
          {
            Thread.sleep (30);
            settlement.resolve (10);
          }
          catch (Exception e)
          {
            settlement.reject (e);
          }
        });

        promises.add (p);
      }

      Promise <Integer> start = new Promise<> (settlement -> {
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

      Promise <Integer> middle = new Promise<> (settlement -> {
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

      start.then (n -> middle)
           .then (n -> Promise.all (promises))
           .then (resolved (result -> {
             this.isComplete_ = true;

             Assert.assertEquals (promises.size (), result.size ());

             for (int i = 0; i < result.size (); ++ i)
               Assert.assertEquals (10, result.get (i));

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
  public void testPromiseChainTyped () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.OnResolved <String, Long> completion1 = str -> {
        Assert.assertEquals ("Hello, World", str);
        return Promise.resolve (10L);
      };

      Promise.OnResolved <Long, Void> completion2 = resolved (value -> {
        Assert.assertEquals (10L, (long)value);
        this.isComplete_ = true;

        synchronized (this.lock_)
        {
          this.lock_.notify ();
        }
      });

      Promise.resolve ("Hello, World")
             .then (completion1)
             .then (completion2);

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testPromiseChain () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve ("Hello, World")
             .then (str -> {
               Assert.assertEquals ("Hello, World", str);
               return Promise.resolve (10);
             })
             .then (resolved (n -> {
               // n is of type long, but assertEquals has ambiguous calls.
               Assert.assertEquals (10, (long)n);

               synchronized (this.lock_)
               {
                 this.isComplete_ = true;
                 this.lock_.notify ();
               }
             }));

      this.lock_.wait (5000);

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
             .then (resolved (n -> {
               Assert.assertNull (n);
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
             .then (value -> {
               Assert.fail ();
               return Promise.resolve (10);
             })
             .then (value -> {
               Assert.fail ();
               return Promise.resolve (40);
             })
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
             .then (value -> {
               Assert.fail ();
               return Promise.resolve (10);
             })
             .then (value -> {
               Assert.fail ();
               return Promise.resolve (40);
             })
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

      this.lock_.wait (1000);
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

               return Promise.resolve (10);
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

      this.lock_.wait (500);

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
  public void testResolveCatchThen () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve (50)
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
              new Promise<> ((settlement) -> {
                try
                {
                  Thread.sleep (500);
                  settlement.resolve (10);
                }
                catch (InterruptedException e)
                {
                  settlement.reject (e);
                }
              }),
              new Promise<> ((settlement) -> {
                try
                {
                  Thread.sleep (300);
                  settlement.resolve (20);
                }
                catch (InterruptedException e)
                {
                  settlement.reject (e);
                }
              }),
              new Promise<> ((settlement) -> {
                try
                {
                  Thread.sleep (600);
                  settlement.resolve (30);
                }
                catch (InterruptedException e)
                {
                  settlement.reject (e);
                }
              })
          );

      p.then (resolved (value -> {
        Assert.assertEquals (20, (int)value);

        Assert.assertFalse (this.isComplete_);
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
  public void testResolveCatchIgnoreThen () throws Exception
  {
    synchronized (this.lock_)
    {
      Promise.resolve (5)
             ._catch (ignoreReason)
             .then (resolved (reason -> {
               Assert.assertNull (reason);

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
}
