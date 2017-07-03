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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @class Promise
 *
 * A promise is an object which can be returned synchronously from an asynchronous
 * function.
 *
 * @param <T>
 */
public class Promise <T>
{
  /**
   * @interface Settlement
   *
   * Settlement interface used to either resolve or reject a promise.
   *
   * @param <T>
   */
  public interface Settlement <T>
  {
    void resolve (T value);

    void reject (Throwable reason);
  }

  /**
   * Interface for resolving a promise. The promise implementation has the option
   * of chaining another promise that will be used to get the value for resolve handlers
   * later in the chain.
   *
   * @param <T>
   */
  public interface OnResolved <T, U>
  {
    Promise <U> onResolved (T value);
  }

  /**
   * Interface for rejecting a promise. The promise implementation has the option
   * of chaining another promise that will be used to get the value for resolve handlers
   * later in the chain.
   */
  public interface OnRejected
  {
    Promise onRejected (Throwable reason);
  }

  /**
   * Helper interface for resolving a promise, but does not return a promise to be used in
   * chain
   *
   * @param <T>
   */
  public interface ResolveNoReturn <T>
  {
    void resolveNoReturn (T value);
  }

  public static <T, U> OnResolved <T, U> resolved (ResolveNoReturn <T> resolveNoReturn)
  {
    return new OnResolvedNoReturn<> (resolveNoReturn);
  }

  private static class OnResolvedNoReturn <T, U> implements OnResolved <T, U>
  {
    private final ResolveNoReturn <T> resolveNoReturn_;

    private OnResolvedNoReturn (ResolveNoReturn <T> resolveNoReturn)
    {
      this.resolveNoReturn_ = resolveNoReturn;
    }

    @Override
    public Promise onResolved (T value)
    {
      this.resolveNoReturn_.resolveNoReturn (value);
      return null;
    }
  }

  /**
   * Factory method that creates an OnRejected handler that does not return a Promise
   * value to be used in the chain.
   *
   * @param rejectNoReturn        RejectNoReturn instance
   * @return
   */
  public static OnRejected rejected (RejectNoReturn rejectNoReturn)
  {
    return new OnRejectedNoReturn (rejectNoReturn);
  }

  /**
   * Helper OnRejected handler for ignoring the reason a Promise is rejected.
   */
  public static final OnRejected ignoreReason = rejected (new RejectNoReturn ()
  {
    @Override
    public void rejectNoReturn (Throwable reason)
    {

    }
  });

  public interface RejectNoReturn
  {
    void rejectNoReturn (Throwable reason);
  }

  private static class OnRejectedNoReturn implements OnRejected
  {
    private final RejectNoReturn rejectNoReturn_;

    private OnRejectedNoReturn (RejectNoReturn rejectNoReturn)
    {
      this.rejectNoReturn_ = rejectNoReturn;
    }

    @Override
    public Promise onRejected (Throwable reason)
    {
      this.rejectNoReturn_.rejectNoReturn (reason);
      return null;
    }
  }

  public enum Status
  {
    Pending,
    Resolved,
    Rejected
  }

  /// The resolved value for the promise.
  private T value_;

  private Status status_;

  /// The rejected value for the promise.
  protected Throwable rejection_;

  private final ReentrantReadWriteLock stateLock_ = new ReentrantReadWriteLock ();

  private static class PromiseThreadFactory implements ThreadFactory
  {
    private AtomicInteger counter_ = new AtomicInteger (0);

    @Override
    public Thread newThread (Runnable runnable)
    {
      String threadName = "PromiseThread-" + this.counter_.getAndIncrement ();
      return new Thread (runnable, threadName);
    }
  }

  private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool (new PromiseThreadFactory ());

  private final PromiseExecutor<T> impl_;

  private final Executor executor_;

  private static class PendingEntry <T>
  {
    final ContinuationPromise <?> cont;
    final OnResolved <T, ?> onResolved;
    final OnRejected onRejected;

    PendingEntry (ContinuationPromise <?> cont, OnResolved <T, ?> onResolved, OnRejected onRejected)
    {
      this.cont = cont;
      this.onResolved = onResolved;
      this.onRejected = onRejected;
    }
  }

  private final ArrayList <PendingEntry <T>> pendingEntries_ = new ArrayList<> ();

  /**
   * The executor for the Promise.
   *
   * @param impl
   */
  public Promise (PromiseExecutor<T> impl)
  {
    this (impl, Status.Pending, null, null);
  }

  /**
   * Create a Promise that is resolved.
   *
   * @param resolve
   */
  private Promise (T resolve)
  {
    this (null, Status.Resolved, resolve, null);
  }

  /**
   * Create a Promise that is rejected.
   *
   * @param reason
   */
  private Promise (Throwable reason)
  {
    this (null, Status.Rejected, null, reason);
  }

  /**
   * Initializing constructor.
   *
   * @param impl
   * @param resolve
   * @param reason
   */
  private Promise (PromiseExecutor<T> impl, Status status, T resolve, Throwable reason)
  {
    this.impl_ = impl;
    this.value_ = resolve;
    this.rejection_ = reason;
    this.status_ = status;
    this.executor_ = DEFAULT_EXECUTOR;

    // If the promise is not pending, then we need to continueWith the promise. We also
    // need to continueWith the promise in the background so normal control can continue.
    if (this.status_ == Status.Pending && this.impl_ != null)
      this.settlePromise ();
  }

  public Status getStatus ()
  {
    return this.status_;
  }

  /**
   * Settle the promise.
   *
   * @param onResolved
   */
  public <U> Promise <U> then (OnResolved <T, U> onResolved)
  {
    return this.then (onResolved, null);
  }

  /**
   * Settle the promise. The promised will either be resolved or rejected.
   *
   * @param onResolved
   * @param onRejected
   */
  @SuppressWarnings ("unchecked")
  public <U> Promise <U> then (final OnResolved <T, U> onResolved, final OnRejected onRejected)
  {
    final ContinuationPromise continuation = new ContinuationPromise<> ();

    try
    {
      // Get the read lock so that t
      this.stateLock_.readLock ().lock ();

      if (this.status_ == Status.Resolved)
      {
        // The promise is already resolved. If the client has provided a handler,
        // then we need to invoke it and determine how we are to proceed. Otherwise,
        // we need to continue down the chain with a new start (i.e., a null value).

        this.executor_.execute (new Runnable ()
        {
          @Override
          public void run ()
          {
            if (onResolved != null)
            {
              try
              {
                Promise promise = onResolved.onResolved (value_);
                continuation.continueWith (promise);
              }
              catch (Exception e)
              {
                continuation.continueWith (e);
              }
            }
            else
            {
              continuation.continueWithNull ();
            }
          }
        });
      }
      else if (this.status_ == Status.Rejected)
      {
        // We are handling the rejection as this level. Either we are going to handle
        // the rejection as this level via a onRejected handler, or we are going to
        // pass the rejection to the next level.
        this.executor_.execute (new Runnable ()
        {
          @Override
          public void run ()
          {
            if (onRejected != null)
            {
              try
              {
                Promise promise = onRejected.onRejected (rejection_);
                continuation.continueWith (promise);
              }
              catch (Exception e)
              {
                continuation.continueWith (e);
              }
            }
            else
            {
              continuation.continueWith (rejection_);
            }
          }
        });
      }
      else
      {
        // The promise is still pending. We need to add the resolved and rejected
        // handlers to the waiting list along with the continuation promise returned
        // from this call. This ensure the promise from the resolve/rejected handlers
        // is passed to the correct continuation promise.
        this.pendingEntries_.add (new PendingEntry<> (continuation, onResolved, onRejected));
      }

      return continuation;
    }
    finally
    {
      this.stateLock_.readLock ().unlock ();
    }
  }

  @SuppressWarnings ("unchecked")
  private void settlePromise ()
  {
    this.executor_.execute (new Runnable ()
    {
      @Override
      public void run ()
      {
        // Execute the promise. This method must call either resolve or reject
        // before this method return. Failure to do so means the promise was not
        // completed, and in a bad state.
        try
        {
          impl_.execute (new Settlement<T> ()
          {
            @Override
            public void resolve (T value)
            {
              // Check that the promise is still pending.
              if (status_ != Status.Pending)
                throw new IllegalStateException ("Promise must be pending to resolve");

              onResolve (value);
            }

            @Override
            public void reject (Throwable reason)
            {
              // Check that the promise is still pending.
              if (status_ != Status.Pending)
                throw new IllegalStateException ("Promise must be pending to reject");

              onReject (reason);
            }
          });
        }
        catch (Exception e)
        {
          onReject (e);
        }
      }
    });
  }

  @SuppressWarnings ("unchecked")
  protected void onResolve (T value)
  {
    try
    {
      // Get a write lock to the state since we are updating it. We do not want
      // other threads reading the state until we are done.
      this.stateLock_.writeLock ().lock ();

      // Cache the result of the promise.
      this.status_ = Status.Resolved;
      this.value_ = value;
    }
    finally
    {
      this.stateLock_.writeLock ().unlock ();
    }

    if (this.pendingEntries_.isEmpty ())
      return;

    for (final PendingEntry<T> entry : this.pendingEntries_)
    {
      this.executor_.execute (new Runnable ()
      {
        @Override
        public void run ()
        {
          try
          {
            if (entry.onResolved != null)
            {
              Promise promise = entry.onResolved.onResolved (value_);
              entry.cont.continueWith (promise);
            }
            else
            {
              entry.cont.continueWithNull ();
            }
          }
          catch (Exception e)
          {
            entry.cont.continueWith (e);
          }
        }
      });
    }

    // Clear the list of pending entries.
    this.pendingEntries_.clear ();
  }

  /**
   * Bubble the rejection.
   *
   * @param reason
   */
  @SuppressWarnings ("unchecked")
  protected void onReject (Throwable reason)
  {
    try
    {
      this.stateLock_.writeLock ().lock ();

      this.rejection_ = reason;
      this.status_ = Status.Rejected;
    }
    finally
    {
      this.stateLock_.writeLock ().unlock ();
    }

    if (this.pendingEntries_.isEmpty ())
      return;

    for (final PendingEntry <T> entry: this.pendingEntries_)
    {
      this.executor_.execute (new Runnable ()
      {
        @Override
        public void run ()
        {
          try
          {
            if (entry.onRejected != null)
            {
              Promise promise = entry.onRejected.onRejected (rejection_);
              entry.cont.continueWith (promise);
            }
            else
            {
              entry.cont.continueWith (rejection_);
            }
          }
          catch (Exception e)
          {
            entry.cont.continueWith (e);
          }
        }
      });
    }

    // Clear the list of pending entries.
    this.pendingEntries_.clear ();
  }

  public <U> Promise <U> _catch (OnRejected onRejected)
  {
    if (onRejected == null)
      throw new IllegalStateException ("onRejected cannot be null");

    return this.then (null, onRejected);
  }

  /**
   * Create a Promise that is already resolved.
   *
   * @param value
   * @param <T>
   * @return
   */
  public static <T> Promise <T> resolve (T value)
  {
    return new Promise<> (value);
  }

  /**
   * Create a promise that is already rejected.
   *
   * @param reason
   * @return
   */
  public static <T> Promise <T> reject (Throwable reason)
  {
    return new Promise<> (reason);
  }

  /**
   * Settle a collection of promises.
   *
   * @param promises
   * @return
   */
  public static Promise <List <Object>> all (Promise <?>... promises)
  {
    return all (Arrays.asList (promises));
  }

  /**
   * Settle a collection of promises.
   *
   * @param promises
   * @return
   */
  public static Promise <List <Object>> all (final List <Promise <?>> promises)
  {
    if (promises.isEmpty ())
      return Promise.resolve (Collections.emptyList ());

    return new Promise<> (new PromiseExecutor<List<Object>> ()
    {
      @Override
      public void execute (final Settlement<List<Object>> settlement)
      {
        final ArrayList <Object> results = new ArrayList<> (promises.size ());
        final Iterator<Promise<?>> iterator = promises.iterator ();

        // The first promise in the collection that is rejected causes all promises
        // to be rejected.
        final OnRejected onRejected = new OnRejected ()
        {
          @Override
          public Promise onRejected (Throwable reason)
          {
            settlement.reject (reason);
            return null;
          }
        };

        final OnResolved onResolved = new OnResolved ()
        {
          @Override
          public Promise onResolved (Object value)
          {
            // Add the resolved value to the result set.
            results.add (value);

            if (iterator.hasNext ())
            {
              // We have more promises to resolve. So, let's move to the next one and
              // attempt to resolve it.
              Promise<?> promise = iterator.next ();
              promise.then (this, onRejected);
            }
            else
            {
              // We have fulfilled all the promises. We can return control to the
              // client so it can continue.
              settlement.resolve (results);
            }

            return null;
          }
        };

        // Start resolving the promises.
        Promise<?> promise = iterator.next ();
        promise.then (onResolved, onRejected);
      }
    });
  }

  /**
   * This method returns a promise that resolves or rejects as soon as one of the
   * promises in the iterable resolves or rejects, with the value or reason from
   * that promise.
   *
   * @param promises
   * @param <U>
   * @return
   */
  public static <U> Promise <U> race (Promise <U>... promises)
  {
    return race (Arrays.asList (promises));
  }

  /**
   * This method returns a promise that resolves or rejects as soon as one of the
   * promises in the iterable resolves or rejects, with the value or reason from
   * that promise.
   *
   * @param promises
   * @param <U>
   * @return
   */
  public static <U> Promise <U> race (final List <Promise <U>> promises)
  {
    if (promises.isEmpty ())
      return Promise.resolve (null);

    final Object lock = new Object ();

    return new Promise<U> (new PromiseExecutor<U> ()
    {
      @Override
      public void execute (final Settlement<U> settlement)
      {
        final OnResolved <U, ?> onResolved = resolved (new ResolveNoReturn<U> ()
        {
          @Override
          public void resolveNoReturn (U value)
          {
            synchronized (lock)
            {
              try
              {
                settlement.resolve (value);
              }
              catch (Exception e)
              {
                // Do nothing since we are not the first to finish
              }
            }
          }
        });

        // The first promise in the collection that is rejected causes all promises
        // to be rejected.
        final OnRejected onRejected = rejected (new RejectNoReturn ()
        {
          @Override
          public void rejectNoReturn (Throwable reason)
          {
            synchronized (lock)
            {
              try
              {
                settlement.reject (reason);
              }
              catch (Exception e)
              {
                // Do nothing since we are not the first to finish
              }
            }
          }
        });

        for (Promise <U> promise: promises)
          promise.then (onResolved, onRejected);
      }
    });
  }
}
