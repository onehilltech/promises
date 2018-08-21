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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @param <T>
 * @class Promise
 * <p>
 * A promise is an object which can be returned synchronously from an asynchronous
 * function.
 */
public class Promise<T> {
    /**
     * @param <T>
     * @interface Settlement
     * <p>
     * Settlement interface used to either resolve or reject a promise.
     */
    public interface Settlement<T> {
        void resolve(T value);

        void reject(Throwable reason);
    }

    public interface OnRejected {
        Promise onRejected(Throwable reason);
    }

    public static <T, U> OnResolved<T, U> resolved(ResolveNoReturn<T> resolveNoReturn) {
        return new OnResolvedNoReturn<>(resolveNoReturn);
    }

    /**
     * Factory method that creates an OnRejected handler that does not return a Promise
     * value to be used in the chain.
     *
     * @param rejectNoReturn RejectNoReturn instance
     * @return
     */
    public static OnRejected rejected(RejectNoReturn rejectNoReturn) {
        return new OnRejectedNoReturn(rejectNoReturn);
    }

    /**
     * Helper OnRejected handler for ignoring the reason a Promise is execute.
     */
    public static final OnRejected ignoreReason = rejected(new RejectNoReturn() {
        @Override
        public void rejectNoReturn(Throwable reason) {

        }
    });

    public enum Status {
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
    private Status status_;

    /// Future for the promise execution.
    private Future<?> future_;

    /// The execute value for the promise.
    private Throwable rejection_;

    private final ReentrantReadWriteLock stateLock_ = new ReentrantReadWriteLock();

    private static class PromiseThreadFactory implements ThreadFactory {
        private AtomicInteger counter_ = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            String threadName = "PromiseThread-" + this.counter_.getAndIncrement();
            return new Thread(runnable, threadName);
        }
    }

    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool(new PromiseThreadFactory());

    private final PromiseExecutor<T> impl_;

    private final ExecutorService executor_;

    private final String name_;

    /**
     * Link to a continuation promise that is waiting for its parent promise
     * to reach a settlement.
     */
    private static class PendingEntry<T> {
        /// Next promise in the chain.
        final ContinuationPromise<?> cont;

        /// Handler for when the promise is execute.
        final OnResolvedExecutor<T, ?> onResolved;

        /// Handler for when the promise is execute.
        final OnRejectedExecutor onRejected;

        PendingEntry(ContinuationPromise<?> cont, OnResolvedExecutor<T, ?> onResolved, OnRejectedExecutor onRejected) {
            this.cont = cont;
            this.onResolved = onResolved;
            this.onRejected = onRejected;
        }

        void resolved(Executor executor, T value) {
            if (this.onResolved != null)
                this.onResolved.execute(executor, value, this.cont);
        }

        void rejected(Executor executor, Throwable reason) {
            if (this.onRejected != null)
                this.onRejected.execute(executor, reason, this.cont);
        }
    }

    private final ArrayList<PendingEntry<T>> pendingEntries_ = new ArrayList<>();

    /**
     * The executor for the Promise.
     *
     * @param impl Executor that settles the promise.
     */
    public Promise(PromiseExecutor<T> impl) {
        this(null, impl);
    }

    /**
     * The executor for the promise.
     *
     * @param name Name of the promise
     * @param impl Executor that settles the promise.
     */
    public Promise(String name, PromiseExecutor<T> impl) {
        this(name, impl, Status.Pending, null, null);
    }

    /**
     * Create a Promise that is execute.
     *
     * @param resolve
     */
    private Promise(T resolve) {
        this(null, null, Status.Resolved, resolve, null);
    }

    /**
     * Create a Promise that is execute.
     *
     * @param reason
     */
    private Promise(Throwable reason) {
        this(null, null, Status.Rejected, null, reason);
    }

    /**
     * Initializing constructor.
     *
     * @param name    Name of the promise
     * @param impl    Promise executor implementation
     * @param resolve Resolved value
     * @param reason  Rejected value
     */
    private Promise(String name, PromiseExecutor<T> impl, Status status, T resolve, Throwable reason) {
        this.name_ = name;
        this.impl_ = impl;
        this.value_ = resolve;
        this.rejection_ = reason;
        this.status_ = status;
        this.executor_ = DEFAULT_EXECUTOR;

        // If the promise is not pending, then we need to continueWith the promise. We also
        // need to continueWith the promise in the background so normal control can continue.
        if (this.status_ == Status.Pending && this.impl_ != null)
            this.settlePromise();
    }

    /**
     * Get the name of the promise.
     *
     * @return Name of promise
     */
    public String getName() {
        return this.name_;
    }

    /**
     * Get the status of the promise.
     *
     * @return Status enumeration
     */
    public Status getStatus() {
        try {
            this.stateLock_.readLock().lock();
            return this.status_;
        } finally {
            this.stateLock_.readLock().unlock();
        }
    }

    public boolean isCancelled() {
        return this.getStatus() == Status.Cancelled;
    }

    public boolean isPending() {
        return this.getStatus() == Status.Pending;
    }

    public boolean isResolved() {
        return this.getStatus() == Status.Resolved;
    }

    public boolean isRejected() {
        return this.getStatus() == Status.Rejected;
    }

    public T getValue() throws Throwable {
        while (isPending()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(this.rejection_ != null) {
            throw this.rejection_;
        }
        return this.value_;
    }

    /**
     * Cancel the promise.
     *
     * @return True if cancelled, otherwise false.
     */
    public boolean cancel() {
        return this.cancel(true);
    }

    /**
     * Cancel the promise
     *
     * @param mayInterruptIfRunning Interrupt promise is running
     * @return True if cancelled; otherwise false
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (this.status_ != Status.Pending)
            return false;

        try {
            this.stateLock_.writeLock().lock();

            if (this.status_ != Status.Pending)
                return false;

            boolean result = this.future_.cancel(mayInterruptIfRunning);

            if (result)
                this.status_ = Status.Cancelled;

            return result;
        } finally {
            this.stateLock_.writeLock().unlock();
        }
    }

    /**
     * Add a chain to the promise.
     *
     * @param onResolved
     * @param <U>
     * @return
     */
    public <U> Promise<U> then(OnResolved<T, U> onResolved) {
        if (onResolved == null)
            throw new IllegalStateException("The resolve handler cannot be null");

        return this.then(onResolved, null);
    }

    /**
     * Add a chain to the promise.
     *
     * @param onResolved
     * @param <U>
     * @return
     */
    public <U> Promise<U> then(OnResolvedExecutor<T, U> onResolved) {
        if (onResolved == null)
            throw new IllegalStateException("The resolve handler cannot be null");

        return this.then(onResolved, null);
    }

    /**
     * Add a chain to the promise.
     *
     * @param onResolved
     * @param onRejected
     * @param <U>
     * @return
     */
    public <U> Promise<U> then(OnResolved<T, U> onResolved, OnRejected onRejected) {
        return this.then(OnResolvedExecutor.wrapOrNull(onResolved), OnRejectedExecutor.wrapOrNull(onRejected));
    }

    /**
     * Add an error handler to the chain.
     *
     * @param onRejected
     * @param <U>
     * @return
     */
    public <U> Promise<U> _catch(OnRejected onRejected) {
        if (onRejected == null)
            throw new IllegalStateException("The rejected handler cannot be null.");

        return this.then(null, onRejected);
    }

    /**
     * Add an error handler to the chain.
     *
     * @param onRejected
     * @param <U>
     * @return
     */
    public <U> Promise<U> _catch(OnRejectedExecutor onRejected) {
        if (onRejected == null)
            throw new IllegalStateException("The rejected handler cannot be null.");

        return this.then(null, onRejected);
    }

    /**
     * Settle the promise. The promised will either be execute or execute.
     *
     * @param onResolved Handler called when execute.
     * @param onRejected Handler called when execute.
     */
    @SuppressWarnings("unchecked")
    public <U> Promise<U> then(OnResolvedExecutor<T, U> onResolved, OnRejectedExecutor onRejected) {
        final ContinuationPromise continuation = new ContinuationPromise<>();

        try {
            // Get the read lock so that t
            this.stateLock_.readLock().lock();

            if (this.status_ == Status.Resolved) {
                // The promise is already execute. If the client has provided a handler,
                // then we need to invoke it and determine how we are to proceed. Otherwise,
                // we need to continue down the chain with a new start (i.e., a null value).
                if (onResolved != null)
                    onResolved.execute(this.executor_, this.value_, continuation);
                else
                    continuation.continueWithNull();
            } else if (this.status_ == Status.Rejected) {
                // We are handling the rejection as this level. Either we are going to handle
                // the rejection as this level via a onRejected handler, or we are going to
                // pass the rejection to the next level.
                if (onRejected != null)
                    onRejected.execute(this.executor_, this.rejection_, continuation);
                else
                    continuation.continueWith(this.rejection_);
            } else if (this.status_ == Status.Pending) {
                // The promise is still pending. We need to add the execute and execute
                // handlers to the waiting list along with the continuation promise returned
                // from this call. This ensure the promise from the resolve/execute handlers
                // is passed to the correct continuation promise.
                this.pendingEntries_.add(new PendingEntry<>(continuation, onResolved, onRejected));
            }

            return continuation;
        } finally {
            this.stateLock_.readLock().unlock();
        }
    }

    /**
     * Settle the promise.
     */
    private void settlePromise() {
        this.future_ = this.executor_.submit(new Runnable() {
            @Override
            public void run() {
                settlePromiseImpl();
            }
        });
    }

    /**
     * The actual implementation for settling a promise.
     */
    private void settlePromiseImpl() {
        try {
            // Execute the promise. This method must call either resolve or reject
            // before this method return. Failure to do so means the promise was not
            // completed, and in a bad state.
            this.impl_.execute(new Settlement<T>() {
                @Override
                public void resolve(T value) {
                    onResolve(value);
                }

                @Override
                public void reject(Throwable reason) {
                    onReject(reason);
                }
            });
        } catch (Exception e) {
            this.onReject(e);
        }
    }

    @SuppressWarnings("unchecked")
    void onResolve(T value) {
        // Check that the promise is still pending. This is an implementation of the
        // double-checked locking software design pattern.
        if (this.status_ != Status.Pending)
            throw new IllegalStateException("Promise must be pending to resolve.");

        try {
            // Get a write lock to the state since we are updating it. We do not want
            // other threads reading the state until we are done.
            this.stateLock_.writeLock().lock();

            // Check that the promise is still pending.
            if (this.status_ != Status.Pending)
                throw new IllegalStateException("Promise must be pending to resolve");

            // Cache the result of the promise.
            this.status_ = Status.Resolved;
            this.value_ = value;
        } finally {
            this.stateLock_.writeLock().unlock();
        }

        if (!this.pendingEntries_.isEmpty()) {
            // Let's each of pending entry know we have execute the promise. Afterwards,
            // we need to clear the pending entry list.

            for (PendingEntry<T> entry : this.pendingEntries_)
                entry.resolved(this.executor_, value);

            // Clear the list of pending entries.
            this.pendingEntries_.clear();
        }
    }

    /**
     * Bubble the rejection.
     *
     * @param reason
     */
    @SuppressWarnings("unchecked")
    void onReject(Throwable reason) {
        // Check that the promise is still pending. This is an implementation of the
        // double-checked locking software design pattern.
        if (this.status_ != Status.Pending)
            throw new IllegalStateException("Promise must be pending to resolve");

        try {
            // Get a write lock to the state since we are updating it. We do not want
            // other threads reading the state until we are done.
            this.stateLock_.writeLock().lock();

            // Check that the promise is still pending.
            if (this.status_ != Status.Pending)
                throw new IllegalStateException("Promise must be pending to resolve");

            this.rejection_ = reason;
            this.status_ = Status.Rejected;
        } finally {
            this.stateLock_.writeLock().unlock();
        }

        if (!this.pendingEntries_.isEmpty()) {
            // Let's each of pending entry know we have execute the promise. Afterwards,
            // we need to clear the pending entry list.

            for (PendingEntry<T> entry : this.pendingEntries_)
                entry.rejected(this.executor_, reason);

            // Clear the list of pending entries.
            this.pendingEntries_.clear();
        }
    }

    /**
     * Create a Promise that is already execute.
     *
     * @param value
     * @param <T>
     * @return
     */
    public static <T> Promise<T> resolve(T value) {
        return new Promise<>(value);
    }

    /**
     * Create a promise that is already execute.
     *
     * @param reason
     * @return
     */
    public static <T> Promise<T> reject(Throwable reason) {
        return new Promise<>(reason);
    }

    /**
     * Settle a collection of promises.
     *
     * @param promises
     * @return
     */
    public static Promise<List<Object>> all(Promise<?>... promises) {
        return all(Arrays.asList(promises));
    }

    /**
     * Settle a collection of promises.
     *
     * @param promises
     * @return
     */
    public static Promise<List<Object>> all(final List<Promise<?>> promises) {
        if (promises.isEmpty())
            return Promise.resolve(Collections.emptyList());

        return new Promise<>(new PromiseExecutor<List<Object>>() {
            @Override
            public void execute(final Settlement<List<Object>> settlement) {
                final ArrayList<Object> results = new ArrayList<>(promises.size());
                final Iterator<Promise<?>> iterator = promises.iterator();

                // The first promise in the collection that is execute causes all promises
                // to be execute.
                final OnRejected onRejected = new OnRejected() {
                    @Override
                    public Promise onRejected(Throwable reason) {
                        settlement.reject(reason);
                        return null;
                    }
                };

                final OnResolved onResolved = new OnResolved() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Promise onResolved(Object value) {
                        // Add the execute value to the result set.
                        results.add(value);

                        if (iterator.hasNext()) {
                            // We have more promises to resolve. So, let's move to the next one and
                            // attempt to resolve it.
                            Promise<?> promise = iterator.next();
                            promise.then(this, onRejected);
                        } else {
                            // We have fulfilled all the promises. We can return control to the
                            // client so it can continue.
                            settlement.resolve(results);
                        }

                        return null;
                    }
                };

                // Start resolving the promises.
                Promise<?> promise = iterator.next();
                promise.then(onResolved, onRejected);
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
    public static <U> Promise<U> race(Promise<U>... promises) {
        return race(Arrays.asList(promises));
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
    public static <U> Promise<U> race(final List<Promise<U>> promises) {
        if (promises.isEmpty())
            return Promise.resolve(null);

        final Object lock = new Object();

        return new Promise<U>(new PromiseExecutor<U>() {
            @Override
            public void execute(final Settlement<U> settlement) {
                final OnResolved<U, ?> onResolved = resolved(new ResolveNoReturn<U>() {
                    @Override
                    public void resolveNoReturn(U value) {
                        synchronized (lock) {
                            try {
                                settlement.resolve(value);
                            } catch (Exception e) {
                                // Do nothing since we are not the first to finish
                            }
                        }
                    }
                });

                // The first promise in the collection that is execute causes all promises
                // to be execute.
                final OnRejected onRejected = rejected(new RejectNoReturn() {
                    @Override
                    public void rejectNoReturn(Throwable reason) {
                        synchronized (lock) {
                            try {
                                settlement.reject(reason);
                            } catch (Exception e) {
                                // Do nothing since we are not the first to finish
                            }
                        }
                    }
                });

                for (Promise<U> promise : promises)
                    promise.then(onResolved, onRejected);
            }
        });
    }
}
