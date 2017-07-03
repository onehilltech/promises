promises
==========

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
  compile com.onehilltech:promises:x.y.z
  
  # or 
    
  # for JVM projects
  compile com.onehilltech.promises:promises-jvm:x.y.z
  
  # for Android projects
  compile com.onehilltech.promises:promises-android:x.y.z
}
```

## Quick Start

The simplest promise to create is one that is already resolved or rejected using
`Promise.resolve` or `Promise.reject`, respectively.

```java
Promise.resolve (5);

Promise.reject (new IllegalStateException ("This is a rejected promise"));
```

All promises are executed (or settled) when they are first created. To process
a promise's settlement, use either `then` or `_catch`.

```java
Promise.resolve (5)
       .then (n -> {
         System.out.println ("Resolved value: " + n);
         return null;
       });

Promise.reject (new IllegalStateException ("This is a rejected promise"))
       ._catch (reason -> {
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
         System.out.println ("Resolved value: " + n);
         return Promise.resolve (10);
       })
       .then (n -> {
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
         System.out.println ("Resolved value: " + n);
       }))
       ._catch (rejected (reason -> reason.printStackTrace ()));
```

### Promise.all

### Promise.race

## Android Support

Happy Coding!
