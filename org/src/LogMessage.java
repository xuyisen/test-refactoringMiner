/**
 * Displays the right message.
 *
 * @param lineNumber the line number the message relates to.
 */
private void logMessage(int lineNumber) {
    final String msg = getMessage();

    if (illegalPattern) {
        log(lineNumber, MSG_ILLEGAL_REGEXP, msg);
    }
    else {
        log(lineNumber, MSG_DUPLICATE_REGEXP, msg);
    }
}
/**
 * Provide right message.
 *
 * @return message for violation.
 */
private String getMessage() {
    String msg;

    if (message == null || message.isEmpty()) {
        msg = format.pattern();
    }
    else {
        msg = message;
    }

    if (errorCount >= errorLimit) {
        msg = ERROR_LIMIT_EXCEEDED_MESSAGE + msg;
    }

    return msg;
}