package com.onehilltech.promises;

/**
 * Helper interface for resolving a promise, but does not return a promise to be used in
 * chain
 *
 * @param <T>
 */
public interface ResolveNoReturn <T>
{
  void resolveNoReturn (T value) throws Throwable;
}
