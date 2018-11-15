package com.onehilltech.promises;

public class DeadlineException extends IllegalStateException
{
  public DeadlineException ()
  {
    super ();
  }

  public DeadlineException (String s)
  {
    super (s);
  }

  public DeadlineException (String message, Throwable cause)
  {
    super (message, cause);
  }

  public DeadlineException (Throwable cause)
  {
    super (cause);
  }
}
