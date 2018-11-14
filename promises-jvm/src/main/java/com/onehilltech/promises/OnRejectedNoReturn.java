package com.onehilltech.promises;

/**
 * Specialized implementation of the OnRejected handler that does not
 * have a return value.
 */
class OnRejectedNoReturn implements OnRejected
{
  private final RejectNoReturn rejectNoReturn_;

  OnRejectedNoReturn (RejectNoReturn rejectNoReturn)
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
