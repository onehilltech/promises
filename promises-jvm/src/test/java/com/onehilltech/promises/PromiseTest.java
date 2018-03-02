package com.onehilltech.promises;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.onehilltech.promises.Promise.ignoreReason;
import static com.onehilltech.promises.Promise.rejected;
import static com.onehilltech.promises.Promise.resolved;

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
      Promise <Integer> p = Promise.resolve (7);

      p.then (
          resolved (new Promise.ResolveNoReturn<Integer> ()
          {
            @Override
            public void resolveNoReturn (Integer value)
            {
              isComplete_ = true;
              Assert.assertEquals (7, (int)value);

              synchronized (lock_)
              {
                lock_.notify ();
              }
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
      Promise <Integer> p = Promise.reject (new IllegalStateException ());

      p.then (new Promise.OnResolved<Integer, Integer> () {
        @Override
        public Promise<Integer> onResolved (Integer value)
        {
          return Promise.resolve (5);
        }
      }, rejected (new Promise.RejectNoReturn () {
        @Override
        public void rejectNoReturn (Throwable reason)
        {
          isComplete_ = true;

          synchronized (lock_)
          {
            Assert.assertEquals (IllegalStateException.class, reason.getClass ());

            lock_.notify ();
          }
        }
      }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }

  @Test
  public void testMultipleThen () throws Exception
  {
    final Promise <Integer> p1 = new Promise<> (new PromiseExecutor<Integer> ()
    {
      @Override
      public void execute (Promise.Settlement<Integer> settlement)
      {
        try
        {
          Thread.sleep (30);
          settlement.resolve (1);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      }
    });

    final Promise <Integer> p2 = new Promise<> (new PromiseExecutor<Integer> ()
    {
      @Override
      public void execute (Promise.Settlement<Integer> settlement)
      {
        try
        {
          Thread.sleep (30);
          settlement.resolve (2);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      }
    });

    final Promise <Integer> p3 = new Promise<> (new PromiseExecutor<Integer> ()
    {
      @Override
      public void execute (Promise.Settlement<Integer> settlement)
      {
        try
        {
          Thread.sleep (30);
          settlement.resolve (3);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      }
    });

    final Promise <Integer> p4 = new Promise<> (new PromiseExecutor<Integer> ()
    {
      @Override
      public void execute (Promise.Settlement<Integer> settlement)
      {
        try
        {
          Thread.sleep (30);
          settlement.resolve (4);
        }
        catch (Exception e)
        {
          settlement.reject (e);
        }
      }
    });

    synchronized (this.lock_)
    {
      p1.then (new Promise.OnResolved<Integer, Integer> () {
        @Override
        public Promise<Integer> onResolved (Integer value)
        {
          return p2;
        }
      }).then (new Promise.OnResolved<Integer, Integer> () {
        @Override
        public Promise<Integer> onResolved (Integer value)
        {
          return p3;
        }
      }).then (new Promise.OnResolved<Integer, Integer> () {
        @Override
        public Promise<Integer> onResolved (Integer value)
        {
          return p4;
        }
      }).then (resolved (new Promise.ResolveNoReturn<Integer> () {
        @Override
        public void resolveNoReturn (Integer value)
        {
          Assert.assertEquals (4, value.intValue ());

          isComplete_ = true;

          synchronized (lock_)
          {
            lock_.notify ();
          }
        }
      }))._catch (rejected (new Promise.RejectNoReturn () {
        @Override
        public void rejectNoReturn (Throwable reason)
        {
          Assert.fail ();
        }
      }));

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
              new Promise<> (new PromiseExecutor<Integer> () {
                @Override
                public void execute (Promise.Settlement<Integer> settlement)
                {
                  settlement.resolve (10);
                }
              }),
              new Promise<> (new PromiseExecutor<Integer> () {
                @Override
                public void execute (Promise.Settlement<Integer> settlement)
                {
                  settlement.resolve (20);
                }
              }));

      p.then (resolved (new Promise.ResolveNoReturn<List<Object>> ()
      {
        @Override
        public void resolveNoReturn (List<Object> value)
        {
          Assert.assertEquals (2, value.size ());

          Assert.assertEquals (10, value.get (0));
          Assert.assertEquals (20, value.get (1));

          isComplete_ = true;

          synchronized (lock_)
          {
            lock_.notify ();
          }
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
        Promise <Integer> p = new Promise<> (new PromiseExecutor<Integer> ()
        {
          @Override
          public void execute (Promise.Settlement<Integer> settlement)
          {
            try
            {
              Thread.sleep (30);
              settlement.resolve (10);
            }
            catch (Exception e)
            {
              settlement.reject (e);
            }
          }
        });

        promises.add (p);
      }

      final Promise <Integer> start = new Promise<> (new PromiseExecutor<Integer> ()
      {
        @Override
        public void execute (Promise.Settlement<Integer> settlement)
        {
          try
          {
            Thread.sleep (40);
            settlement.resolve (20);
          }
          catch (Exception e)
          {
            settlement.reject (e);
          }
        }
      });

      final Promise <Integer> middle = new Promise<> (new PromiseExecutor<Integer> ()
      {
        @Override
        public void execute (Promise.Settlement<Integer> settlement)
        {
          try
          {
            Thread.sleep (40);
            settlement.resolve (20);
          }
          catch (Exception e)
          {
            settlement.reject (e);
          }
        }
      });

      start.then (new Promise.OnResolved<Integer, Integer> () {
        @Override
        public Promise<Integer> onResolved (Integer value)
        {
          return middle;
        }
      }).then (new Promise.OnResolved<Integer, List <Object>> () {
        @Override
        public Promise<List <Object>> onResolved (Integer value)
        {
          return Promise.all (promises);
        }
      }).then (resolved (new Promise.ResolveNoReturn<List<Object>> ()
      {
        @Override
        public void resolveNoReturn (List<Object> result)
        {
          isComplete_ = true;

          Assert.assertEquals (promises.size (), result.size ());

          for (int i = 0; i < result.size (); ++ i)
            Assert.assertEquals (10, result.get (i));

          synchronized (lock_)
          {
            lock_.notify ();
          }
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
           ._catch (rejected (new Promise.RejectNoReturn ()
           {
             @Override
             public void rejectNoReturn (Throwable reason)
             {
               isComplete_ = true;
               Assert.assertEquals (reason.getClass (), IllegalStateException.class);

               synchronized (lock_)
               {
                 lock_.notify ();
               }
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
      Promise.OnResolved <String, Long> completion1 = new Promise.OnResolved<String, Long> ()
      {
        @Override
        public Promise<Long> onResolved (String str)
        {
          Assert.assertEquals ("Hello, World", str);
          return Promise.resolve (10L);
        }
      };

      Promise.OnResolved <Long, Void> completion2 = resolved (new Promise.ResolveNoReturn<Long> ()
      {
        @Override
        public void resolveNoReturn (Long value)
        {
          Assert.assertEquals (10L, (long)value);
          isComplete_ = true;

          synchronized (lock_)
          {
            lock_.notify ();
          }
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
             .then (new Promise.OnResolved<String, Integer> () {
               @Override
               public Promise<Integer> onResolved (String str)
               {
                 Assert.assertEquals ("Hello, World", str);
                 return Promise.resolve (10);
               }
             })
             .then (resolved (new Promise.ResolveNoReturn<Integer> ()
             {
               @Override
               public void resolveNoReturn (Integer value)
               {
                 // n is of type long, but assertEquals has ambiguous calls.
                 Assert.assertEquals (10, value.intValue ());

                 synchronized (lock_)
                 {
                   isComplete_ = true;
                   lock_.notify ();
                 }
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
             .then (resolved (new Promise.ResolveNoReturn<String> () {
               @Override
               public void resolveNoReturn (String str)
               {
                 Assert.assertEquals ("Hello, World", str);
               }
             }))
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);
                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             .then (new Promise.OnResolved<Object, Integer> () {
               @Override
               public Promise<Integer> onResolved (Object value)
               {
                 Assert.fail ();
                 return Promise.resolve (10);
               }
             })
             .then (new Promise.OnResolved<Integer, Integer> () {
               @Override
               public Promise<Integer> onResolved (Integer value)
               {
                 Assert.fail ();
                 return Promise.resolve (40);
               }
             })
             ._catch (rejected (new Promise.RejectNoReturn ()
             {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.assertEquals (IllegalStateException.class, reason.getClass ());
                 Assert.assertEquals ("GREAT", reason.getLocalizedMessage ());

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             .then (new Promise.OnResolved<Object, Integer> () {
               @Override
               public Promise<Integer> onResolved (Object value)
               {
                 Assert.fail ();
                 return Promise.resolve (10);
               }
             })
             .then (new Promise.OnResolved<Integer, Integer> () {
               @Override
               public Promise<Integer> onResolved (Integer value)
               {
                 Assert.fail ();
                 return Promise.resolve (40);
               }
             })
             ._catch (rejected (new Promise.RejectNoReturn ()
             {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.assertEquals (IllegalStateException.class, reason.getClass ());
                 Assert.assertEquals ("GREAT", reason.getLocalizedMessage ());

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
               }
             }))
             ._catch (rejected (new Promise.RejectNoReturn ()
             {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.fail ();
               }
             }));

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
             ._catch (new Promise.OnRejected () {
               @Override
               public Promise onRejected (Throwable reason)
               {
                 Assert.assertEquals (IllegalStateException.class, reason.getClass ());
                 return Promise.resolve (10);
               }
             })
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertEquals (10, value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             ._catch (rejected (new Promise.RejectNoReturn () {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.assertEquals (IllegalStateException.class, reason.getClass ());
               }
             }))
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             ._catch (rejected (new Promise.RejectNoReturn () {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.fail ();
               }
             }))
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             ._catch (rejected (new Promise.RejectNoReturn () {
               @Override
               public void rejectNoReturn (Throwable reason)
               {
                 Assert.assertEquals (IllegalStateException.class, reason.getClass ());
               }
             }))
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
             .then (resolved (new Promise.ResolveNoReturn<Integer> ()
             {
               @Override
               public void resolveNoReturn (Integer value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
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
              new Promise<> (new PromiseExecutor<Integer> ()
              {
                @Override
                public void execute (Promise.Settlement<Integer> settlement)
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
              new Promise<> (new PromiseExecutor<Integer> ()
              {
                @Override
                public void execute (Promise.Settlement<Integer> settlement)
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
              new Promise<> (new PromiseExecutor<Integer> ()
              {
                @Override
                public void execute (Promise.Settlement<Integer> settlement)
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

      p.then (resolved (new Promise.ResolveNoReturn<Integer> () {
        @Override
        public void resolveNoReturn (Integer value)
        {
          Assert.assertEquals (20, (int)value);

          Assert.assertFalse (isComplete_);
          isComplete_ = true;

          synchronized (lock_)
          {
            lock_.notify ();
          }
        }
      }))
      ._catch (rejected (new Promise.RejectNoReturn () {
        @Override
        public void rejectNoReturn (Throwable reason)
        {
          Assert.fail ();
        }
      }));

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
             .then (resolved (new Promise.ResolveNoReturn<Object> () {
               @Override
               public void resolveNoReturn (Object value)
               {
                 Assert.assertNull (value);

                 isComplete_ = true;

                 synchronized (lock_)
                 {
                   lock_.notify ();
                 }
               }
             }));

      this.lock_.wait (5000);

      Assert.assertTrue (this.isComplete_);
    }
  }
}
