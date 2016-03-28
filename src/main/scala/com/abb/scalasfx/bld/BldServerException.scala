package com.abb.bolt

/**
 * Taken from http://stackoverflow.com/a/33312286/6015856
 */

object BldServerException {
  def apply: BldServerException = 
    BldServerException()
  
  def apply(message: String): BldServerException =
    BldServerException(optionMessage = Some(message))
  
  def apply(cause: Throwable): BldServerException =
    BldServerException(optionCause = Some(cause))
  
  def apply(message: String, cause: Throwable): BldServerException =
    BldServerException(optionMessage = Some(message), optionCause = Some(cause))
  
  def apply(
    optionMessage: Option[String] = None,
    optionCause: Option[Throwable] = None,
    isEnableSuppression: Boolean = false,
    isWritableStackTrace: Boolean = false
  ): BldServerException =
    new BldServerException(
      optionMessage,
      optionCause,
      isEnableSuppression,
      isWritableStackTrace
    )
}

class BldServerException (
  val optionMessage: Option[String],
  val optionCause: Option[Throwable],
  val isEnableSuppression: Boolean,
  val isWritableStackTrace: Boolean
) extends RuntimeException (
  optionMessage match {
    case Some(string) => string
    case None => null
  },
  optionCause match {
    case Some(throwable) => throwable
    case None => null
  },
  isEnableSuppression,
  isWritableStackTrace
)
