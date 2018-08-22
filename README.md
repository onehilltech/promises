promises
==========


[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Promises-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/6140)
[![](https://jitpack.io/v/onehilltech/promises.svg)](https://jitpack.io/#onehilltech/promises)
[![Build Status](https://travis-ci.org/onehilltech/promises.svg?branch=master)](https://travis-ci.org/onehilltech/promises)
[![codecov](https://codecov.io/gh/onehilltech/promises/branch/master/graph/badge.svg)](https://codecov.io/gh/onehilltech/promises)

Promise library for JVM and Android

* Implements the [Promises/A+](https://promisesaplus.com/) specification from JavaScript
* Built using Java 1.7, but designed for Java 1.8
* Supports Java Lambda expressions
* Android module allows resolve/reject callbacks to run on UI thread

## Installation

### Gradle

```
buildscript {
  repositories {
    maven { url "https://jitpack.io" }
  }
}

dependencies {
  # for JVM-only projects
  compile com.onehilltech.promises:promises-jvm:x.y.z
  
  # for Android projects (includes JVM automatically)
  compile com.onehilltech.promises:promises-android:x.y.z
}
```

### Java 1.8 on JVM

Just set the source and target compiler option to 1.8:

```
targetCompatibility = '1.8'
sourceCompatibility = '1.8'
```

### Java 1.8 (Lambda expressions) on Android

Unless you are using Android Studio 3.0.0 or above, we recommend using 
[Retrolambda](https://github.com/orfjackal/retrolambda) to enable Java Lambda expressions on
Java 1.7 or earlier. Add the following dependency to your top-level `build.gradle` script:

```
buildscript {
  dependencies {
    classpath 'me.tatarka:gradle-retrolambda:x.y.z'
  }
}
```

In `build.gradle` for each submodule wanting to use Java Lambda expressions with
Promises, add the following to the top:

```
apply plugin: 'me.tatarka.retrolambda'
```

Then, set the source and target to 1.8:

```
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

Lastly, make sure you are using Android SDK build tools `26.0.0`.

## Quick Start

The simplest promise to create is one that is already resolved or rejected using
`Promise.resolve` or `Promise.reject`, respectively.

```java
// resolved promise
Promise.resolve (5);

// rejected promise
Promise.reject (new IllegalStateException ("This is a rejected promise"));
```

You can also create a promise that is settled in the background. This is good when you need to 
perform some workload in the background, and notify the caller (or client) when the workload
is resolved or rejected.

```java
Promise <Foo> p = new Promise < > (settlement -> {
  // settlement.resolve (foo);
  // settlement.reject (ex);
}); 
```

In this case, you must either invoke `settlement.resolve` with the resolved value, or
`settlement.reject` with an exception. Any uncaught exceptions will automatically reject
the promise with the uncaught exception.

All promises are executed (or settled) when they are first created. To process
a promise's settlement, use either `then` or `_catch`. It does not matter when you
call `then` or `_catch`. If the promise is not settled, then the appropriate
handler will be called after the promise is settled. If the promise is settled,
then the appropriate handler will be called as soon as possible. 

> **Important.** All handlers are executed on a separate thread.

```java
Promise.resolve (5)
       .then (n -> {
         // n == 5
         System.out.println ("Resolved value: " + n);
         return null;
       });

Promise.reject (new IllegalStateException ("This is a rejected promise"))
       ._catch (reason -> {
         // reason instanceof IllegalStateException
         reason.printStackTrace ();
         return null;
       });
```

You may notice that the handlers return `null` in the example above. This is because the
handler has the option of returning a `Promise` that to used to resolve the value for the
next handler in the chain. If the handler does not return a `Promise`, then `null` is passed
to the next handler.

```java
Promise.resolve (5)
       .then (n -> {
         // n == 5
         System.out.println ("Resolved value: " + n);
         return Promise.resolve (10);
       })
       .then (n -> {
         // n == 10
         System.out.println ("Resolved value: " + n);
         return null;
       });
```

Not all handlers will return a `Promise` object. If you are in this situation, then you can use
the `ResolveNoReturn` and `RejectNoReturn` helper classes, or `resolved` and `rejected` helper
methods.

```java
import static com.onehilltech.promises.Promise.resolved;
import static com.onehilltech.promises.Promise.rejected;

// ...

Promise.resolve (5)
       .then (resolved (n -> {
         // n == 5
         System.out.println ("Resolved value: " + n);
       }))
       ._catch (rejected (reason -> reason.printStackTrace ()));
```

### Chaining Promises

Promises can be chained to create a series of background workloads to be completed in
step order. Just use `then` to chain a series of background workloads, and `_catch` to
handle any rejection from the preceding promises.

```java
Promise.resolve (5)
       .then (resolved (n -> {
         // n == 5
         System.out.println ("Resolved value: " + n);
       }))
       .then (resolved (value -> {
         // value == null
         System.out.println ("Resolved value: " + value);
       }))
       ._catch (rejected (reason -> { }))
       .then (this::doSomethingElse)
       ._catch (Promise.ignoreReason);
```

In the example above, we must point out several things. Firstly, execution continues
after the first `_catch` if any of the preceding promises is rejected. If none of
the promises are rejected, then the first `_catch` is skipped. Secondly, we are using
Java method references (i.e., `this::doSomethingElse`), which improves the readability
of the code, and reduces verbosity. Lastly, `Promise.ignoreReason` is a special 
handler that will catch the rejection and ignore the reason. This way, you do not have
to write a bunch of empty handlers like the first `_catch`.

### Promise.all

The library implements `Promise.all`, which is resolved if **all** promises are resolved 
and rejected if **any** of the promises is rejected.

### Promise.race

The library implements `Promise.race`, which is settled when the first promise is either 
resolved or rejected.

## Android Support

### Running on the UI Thread

All promises are settled on a background thread, and the handlers are called on a background
thread. If you attempt to update the UI in the handler, then the Android framework will throw
an exception. This is because you are updating the UI on a different thread than the one that
create the UI elements (i.e., the main thread). To address the need for updating the UI in
the handler methods, the Android module provides `onUiThread` helper methods for running a 
handler on the UI thread.

```java
import static com.onehilltech.promises.Promise.resolved;
import static com.onehilltech.promises.Promise.rejected;
import static com.onehilltech.promises.RejectedOnUIThread.onUiThread;
import static com.onehilltech.promises.ResolvedOnUIThread.onUiThread;

// ...

Promise.resolve ("Hello, World!")
       .then (onUiThread (resolved (str -> {
         // Update the UI component
         this.label.setText (str);
       })))
       ._catch (onUiThread (rejected (reason -> reason.printStackTrace ())));
```


Happy Coding!
