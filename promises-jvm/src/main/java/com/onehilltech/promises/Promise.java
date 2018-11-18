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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A promise is an object which can be returned synchronously from an asynchronous
 * function.
 *
 * @param <T>       The resolved value type.
 */
public class Promise <T>
{
  /**
   * Settlement interface used to either resolve or reject a promise.
   *
   * @param <T>
   */
  public interface Settlement <T>
  {
    /**
     * Resolve the promise
     *
     * @param value       The resolved value.
     */
    void resolve (T value);

    /**
     * Reject the promise.
     *
     * @param reason      The reason for rejection.
     */
    void reject (Throwable reason);
  }

  /**
   * Factory method that creates an OnResolved handler that does not return a Promise
   * value to be used in the chain.
   * *
   * @param resolveNoReturn     The resolve function.
   * @return                    OnResolved handler
   */
  public static <T, U> OnResolved <T, U> resolved (ResolveNoReturn <T> resolveNoReturn)
  {
    return new OnResolvedNoReturn<> (resolveNoReturn);
  }

  /**
   * Factory method that creates an OnRejected handler that does not return a Promise
   * value to be used in the chain.
   *
   * @param rejectNoReturn      RejectNoReturn instance
   * @return                    OnRejected handler
   */
  public static OnRejected rejected (RejectNoReturn rejectNoReturn)
  {
    return new OnRejectedNoReturn (rejectNoReturn);
  }

  /**
   * Helper OnRejected handler for ignoring the reason a Promise is execute.
   */
  public static final OnRejected ignoreReason = rejected (reason -> {});

  public enum Status
  {
    /// The promise is in a pending state.
    Pending,

    /// The promise has been execute.
    Resolved,

    /// The promise has been execute.
    Rejected,

    /// The promise has been cancelled.
    Cancelled
  }

  /// The execute value for the promise.
  private T value_;

  /// Current status for the promise.
  private Status status_ = Status.Pending;

  /// Future for the promise execution.
  private Future<?> future_;

  /// The execute value for the promise.
  private Throwable rejection_;

  private final ReentrantReadWriteLock stateLock_ = new ReentrantReadWriteLock ();

  T getValue ()
  {
    return this.value_;
  }

  Throwable getRejection ()
  {
    return this.rejection_;
  }

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

  private final ExecutorService executor_;

  private final String name_;

  private final ArrayList <ContinuationPromise <?>> continuations_ = new ArrayList<> ();

  /// @{ Await Methods

  /**
   * Await settlement of a promise.
   *
   * @param promise           Promise of interest.
   * @return                  The resolved value.
   * @throws Throwable        Reason for rejection.
   */
  public static <T> T await (Promise <T> promise)
      throws Throwable
  {
    return await (promise, true);
  }

  /**
   * Await settlement of a promise.
   *
   * @param promise           Promise of interest.
   * @return                  The resolved value.
   * @throws Throwable        Reason for rejection.
   */
  public static <T> T await (Promise <T> promise, boolean interruptible)
      throws Throwable
  {
    AwaitHandler <T> handler = interruptible ? new AwaitHandler<T> () : new UninterruptiblyAwaitHandler<T> ();
    promise.then (handler)._catch (handler);

    return handler.await ();
  }

  /**
   * Await settlement of a promise up to the specified deadline.
   *
   * @param promise           Promise of interest.
   * @param deadline          When the promise must be resolved by.
   * @return                  The resolved value.
   * @throws Throwable        Reason for rejection
   */
  public static <T> T await (Promise <T> promise, Date deadline)
      throws Throwable
  {
    AwaitHandler <T> handler = new DeadlineAwaitHandler <> (deadline);
    promise.then (handler)._catch (handler);

    return handler.await ();
  }

  /**
   * Await settlement of a promise for a specified amount of time.
   *
   * @param promise           Promise of interest.
   * @param nanos             Nanoseconds to wait
   * @return                  The resolved value.
   * @throws Throwable        Reason for rejection
   */
  public static <T> T await (Promise <T> promise, long nanos)
      throws Throwable
  {
    AwaitHandler <T> handler = new TimeAwaitHandler<> (nanos);
    promise.then (handler)._catch (handler);

    return handler.await ();
  }

  /**
   * Await settlement of a promise for a specified amount of time.
   *
   * @param promise           Promise of interest.
   * @param time              Time to wait
   * @param unit              Units of measure for time
   * @return                  The resolved value.
   * @throws Throwable        Reason for rejection
   */
  public static <T> T await (Promise <T> promise, long time, TimeUnit unit)
      throws Throwable
  {
    return await (promise, unit.toNanos (time));
  }

  /// @}

  /**
   * Link to a continuation promise that is waiting for its parent promise
   * to reach a settlement.
   */
  private static class PendingEntry <T, U>
  {
    /// Next promise in the chain.
    final ContinuationPromise <U> cont;

    /// Handler for when the promise is execute.
    final OnResolvedExecutor<T, U> onResolved;

    /// Handler for when the promise is execute.
    final OnRejectedExecutor<U> onRejected;

    PendingEntry (ContinuationPromise <U> cont, OnResolvedExecutor <T, U> onResolved, OnRejectedExecutor <U> onRejected)
    {
      this.cont = cont;
      this.onResolved = onResolved;
      this.onRejected = onRejected;
    }

    void resolved (Executor executor, T value)
    {
      if (this.onResolved != null)
        this.onResolved.execute (executor, value, this.cont);
      else if (this.onRejected != null)
        this.onRejected.execute (executor, value, this.cont);
    }

    void rejected (Executor executor, Throwable reason)
    {
      if (this.onRejected != null)
      {
        // We have found the first rejection handler. Let's stop here an pass the
        // rejection to the handler.
        this.onRejected.execute (executor, reason, this.cont);
      }
      else if (this.onResolved != null)
      {
        // There was not rejection handler defined a the level. Let's continue
        // down the chain until we find the first one to handle this exception.
        this.onResolved.execute (executor, reason, this.cont);
      }
    }
  }

  private final ArrayList <PendingEntry <T, ?>> pendingEntries_ = new ArrayList<> ();

  /**
   * The executor for the Promise.
   *
   * @param impl        Executor that settles the promise.
   */
  public Promise (PromiseExecutor<T> impl)
  {
    this (null, impl);
  }

  /**
   * The executor for the promise.
   *
   * @param name        Name of the promise
   * @param impl        Executor that settles the promise.
   */
  public Promise (String name, PromiseExecutor<T> impl)
  {
    this (name, impl, Status.Pending, null, null);
  }

  /**
   * Create a resolved promise.
   *
   * @param value
   */
  private Promise (T value)
  {
    this (null, null, Status.Resolved, value, null);
  }

  /**
   * Create a rejected promise.
   *
   * @param reason
   */
  private Promise (Throwable reason)
  {
    this (null, null, Status.Rejected, null, reason);
  }

  /**
   * Initializing constructor.
   *
   * @param name            Name of the promise
   * @param impl            Promise executor implementation
   * @param value           Resolved value
   * @param reason          Rejected value
   */
  private Promise (String name, PromiseExecutor<T> impl, Status status, T value, Throwable reason)
  {
    this.name_ = name;
    this.impl_ = impl;
    this.value_ = value;
    this.rejection_ = reason;
    this.status_ = status;
    this.executor_ = DEFAULT_EXECUTOR;

    // If the promise is not pending, then we need to continueWith the promise. We also
    // need to continueWith the promise in the background so normal control can continue.
    if (this.status_ == Status.Pending && this.impl_ != null)
      this.settlePromise ();
  }

  /**
   * Get the name of the promise.
   *
   * @return        Name of promise
   */
  public String getName ()
  {
    return this.name_;
  }

  /**
   * Get the status of the promise.
   *
   * @return          Status enumeration
   */
  public Status getStatus ()
  {
    try
    {
      this.stateLock_.readLock ().lock ();
      return this.status_;
    }
    finally
    {
      this.stateLock_.readLock ().unlock ();
    }
  }

  public boolean isCancelled ()
  {
    return this.getStatus () == Status.Cancelled;
  }

  public boolean isPending ()
  {
    return this.getStatus () == Status.Pending;
  }

  public boolean isResolved ()
  {
    return this.getStatus () == Status.Resolved;
  }

  public boolean isRejected ()
  {
    return this.getStatus () == Status.Rejected;
  }

  /**
   * Cancel the promise.
   *
   * @return        True if cancelled, otherwise false.
   */
  public boolean cancel ()
  {
    return this.cancel (true);
  }

  /**
   * Cancel the promise
   *
   * @param mayInterruptIfRunning         Interrupt promise is running
   * @return                              True if cancelled; otherwise false
   */
  public boolean cancel (boolean mayInterruptIfRunning)
  {
    boolean result = this.cancelThis (mayInterruptIfRunning);
    result &= this.cancelContinuations (mayInterruptIfRunning);

    return result;
  }

  private boolean cancelThis (boolean mayInterruptIfRunning)
  {
    try
    {
      this.stateLock_.writeLock ().lock ();

      if (this.status_ != Status.Pending || this.future_ == null)
        return false;

      boolean result = this.future_.cancel (mayInterruptIfRunning);

      if (result)
        this.status_ = Status.Cancelled;

      return result;
    }
    finally
    {
      this.stateLock_.writeLock ().unlock ();
    }
  }

  private boolean cancelContinuations (boolean mayInterruptIfRunning)
  {
    boolean result = true;

    synchronized (this.continuations_)
    {
      for (ContinuationPromise continuation : this.continuations_)
        result &= continuation.cancel (mayInterruptIfRunning);
    }

    return result;
  }

  /**
   * Add a chain to the promise.
   *
   * @param onResolved
   * @param <U>
   * @return
   */
  public <U> Promise <U> then (OnResolved <T, U> onResolved)
  {
    if (onResolved == null)
      throw new IllegalStateException ("The resolve handler cannot be null");

    return this.then (onResolved, null);
  }

  /**
   * Add a chain to the promise.
   *
   * @param onResolved
   * @param <U>
   * @return
   */
  public <U> Promise <U> then (OnResolvedExecutor <T, U> onResolved)
  {
    if (onResolved == null)
      throw new IllegalStateException ("The resolve handler cannot be null");

    return this.then (onResolved, null);
  }

  /**
   * Add a chain to the promise.
   *
   * @param onResolved
   * @param onRejected
   * @param <U>
   * @return
   */
  public <U> Promise <U> then (OnResolved <T, U> onResolved, OnRejected onRejected)
  {
    return this.then (OnResolvedExecutor.wrapOrNull (onResolved), OnRejectedExecutor.wrapOrNull (onRejected));
  }

  /**
   * Add an error handler to the chain.
   *
   * @param onRejected
   * @return
   */
  public <U> Promise <U> _catch (OnRejected onRejected)
  {
    if (onRejected == null)
      throw new IllegalStateException ("The rejected handler cannot be null.");

    return this.then (null, onRejected);
  }

  /**
   * Add an error handler to the chain.
   *
   * @param onRejected
   * @return
   */
  public <U> Promise <U> _catch (OnRejectedExecutor onRejected)
  {
    if (onRejected == null)
      throw new IllegalStateException ("The rejected handler cannot be null.");

    return this.then (null, onRejected);
  }

  /**
   * Settle the promise. The promised will either be execute or execute.
   *
   * @param onResolved          Handler called when execute.
   * @param onRejected          Handler called when execute.
   */
  @SuppressWarnings ("unchecked")
  public <U> Promise <U> then (OnResolvedExecutor <T, U> onResolved, OnRejectedExecutor <U> onRejected)
  {
    ContinuationPromise <U> continuation = this.createContinuationPromise ();

    synchronized (this.continuations_)
    {
      this.continuations_.add (continuation);
    }

    Status status;

    try
    {
      this.stateLock_.readLock ().lock ();
      status = this.status_;
    }
    finally
    {
      this.stateLock_.readLock ().unlock ();
    }

    switch (status)
    {
      case Pending:
        // The promise is still pending. We need to add the execute and execute
        // handlers to the waiting list along with the continuation promise returned
        // from this call. This ensure the promise from the resolve/execute handlers
        // is passed to the correct continuation promise.

        this.pendingEntries_.add (new PendingEntry< > (continuation, onResolved, onRejected));
        break;

      case Resolved:
        // The promise is already execute. If the client has provided a handler,
        // then we need to invoke it and determine how we are to proceed. Otherwise,
        // we need to continue down the chain with a new start (i.e., a null value).

        if (onResolved != null)
          onResolved.execute (this.executor_, this.value_, continuation);
        else
          continuation.continueWithNull ();

        break;

      case Rejected:
        // We are handling the rejection as this level. Either we are going to handle
        // the rejection as this level via a onRejected handler, or we are going to
        // pass the rejection to the next level.

        if (onRejected != null)
          onRejected.execute (this.executor_, this.rejection_, continuation);
        else
          continuation.continueWith (this.rejection_);

        break;

      case Cancelled:
        // This promise is cancel. We need to propagate the down the chain.
        continuation.cancel ();
        break;
    }

    return continuation;
  }

  /**
   * Create a new continuation promise.
   *
   * @return
   */
  protected <U> ContinuationPromise <U> createContinuationPromise ()
  {
    return new ContinuationPromise <> ();
  }

  /**
   * Settle the promise.
   */
  private void settlePromise ()
  {
    this.future_ = this.executor_.submit (this::settlePromiseImpl);
  }

  /**
   * The actual implementation for settling a promise.
   */
  private void settlePromiseImpl ()
  {
    try
    {
      // Execute the promise. This method must call either resolve or reject
      // before this method return. Failure to do so means the promise was not
      // completed, and in a bad state.
      this.impl_.execute (new Settlement<T> ()
      {
        @Override
        public void resolve (T value)
        {
          onResolve (value);
        }

        @Override
        public void reject (Throwable reason)
        {
          onReject (reason);
        }
      });
    }
    catch (Throwable e)
    {
      this.onReject (e);
    }
  }

  @SuppressWarnings ("unchecked")
  void onResolve (T value)
  {
    // Check that the promise is still pending. This is an implementation of the
    // double-checked locking software design pattern.
    if (this.status_ != Status.Pending)
      throw new IllegalStateException ("Promise must be pending to resolve.");

    try
    {
      // Get a write lock_ to the state since we are updating it. We do not want
      // other threads reading the state until we are done.
      this.stateLock_.writeLock ().lock ();

      // Check that the promise is still pending.
      if (this.status_ != Status.Pending)
        throw new IllegalStateException ("Promise must be pending to resolve");

      // Cache the result of the promise.
      this.status_ = Status.Resolved;
      this.value_ = value;
    }
    finally
    {
      this.stateLock_.writeLock ().unlock ();
    }

    if (!this.pendingEntries_.isEmpty ())
    {
      // Let's each of pending entry know we have execute the promise. Afterwards,
      // we need to clear the pending entry list.

      for (PendingEntry<T, ?> entry : this.pendingEntries_)
        entry.resolved (this.executor_, value);

      // Clear the list of pending entries.
      this.pendingEntries_.clear ();
    }
  }

  /**
   * Bubble the rejection.
   *
   * @param reason
   */
  @SuppressWarnings ("unchecked")
  void onReject (Throwable reason)
  {
    // Check that the promise is still pending. This is an implementation of the
    // double-checked locking software design pattern.
    if (this.status_ != Status.Pending)
      throw new IllegalStateException ("Promise must be pending to resolve");

    try
    {
      // Get a write lock_ to the state since we are updating it. We do not want
      // other threads reading the state until we are done.
      this.stateLock_.writeLock ().lock ();

      // Check that the promise is still pending.
      if (this.status_ != Status.Pending)
        throw new IllegalStateException ("Promise must be pending to resolve");

      this.rejection_ = reason;
      this.status_ = Status.Rejected;
    }
    finally
    {
      this.stateLock_.writeLock ().unlock ();
    }

    if (!this.pendingEntries_.isEmpty ())
    {
      // Let's each of pending entry know we have execute the promise. Afterwards,
      // we need to clear the pending entry list.

      for (PendingEntry<T, ?> entry : this.pendingEntries_)
        entry.rejected (this.executor_, reason);

      // Clear the list of pending entries.
      this.pendingEntries_.clear ();
    }
  }

  /**
   * Create a Promise that is already execute.
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
   * Helper method for returning a value from a OnResolved handler.
   *
   * @param value
   * @param <T>
   * @return
   */
  public static <T> Promise <T> value (T value)
  {
    return new Promise<> (value);
  }

  /**
   * Create a promise that is already execute.
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

        // The first promise in the collection that is execute causes all promises
        // to be execute.
        final OnRejected onRejected = reason -> {
          settlement.reject (reason);
          return null;
        };

        final OnResolved onResolved = new OnResolved ()
        {
          @SuppressWarnings ("unchecked")
          @Override
          public Promise onResolved (Object value)
          {
            // Add the execute value to the result set.
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

    return new Promise< > (settlement -> {
      final OnResolved <U, ?> onResolved = resolved (value -> {
        synchronized (lock)
        {
          try
          {
            settlement.resolve (value);
          }
          catch (Throwable e)
          {
            // Do nothing since we are not the first to finish
          }
        }
      });

      // The first promise in the collection that is execute causes all promises
      // to be execute.
      final OnRejected onRejected = rejected (reason -> {
        synchronized (lock)
        {
          try
          {
            settlement.reject (reason);
          }
          catch (Throwable e)
          {
            // Do nothing since we are not the first to finish
          }
        }
      });

      for (Promise <U> promise: promises)
        promise.then (onResolved, onRejected);
    });
  }
}
