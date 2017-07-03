promises
==========

[![](https://jitpack.io/v/onehilltech/promises.svg)](https://jitpack.io/#onehilltech/promises)
[![Build Status](https://travis-ci.org/onehilltech/promises.svg?branch=master)](https://travis-ci.org/onehilltech/promises)
[![codecov](https://codecov.io/gh/onehilltech/promises/branch/master/graph/badge.svg)](https://codecov.io/gh/onehilltech/promises)

Promise library for JVM and Android

* Implements the [Promise/A+](https://promisesaplus.com/) specification from JavaScript
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


Happy Coding!
