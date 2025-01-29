public class RegexpCheck {


    /**
     * Displays the right message.
     *
     * @param lineNumber the line number the message relates to.
     */
    private void logMessage(int lineNumber) {
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

        logMessageDetails(lineNumber, msg);
    }

    /**
     * Logs the message details based on the line number and message.
     *
     * @param lineNumber the line number the message relates to.
     * @param msg the message to log.
     */
    private void logMessageDetails(int lineNumber, String msg) {
        if (illegalPattern) {
            log(lineNumber, MSG_ILLEGAL_REGEXP, msg);
        }
        else {
            if (lineNumber > 0) {
                log(lineNumber, MSG_DUPLICATE_REGEXP, msg);
            }
            else {
                log(lineNumber, MSG_REQUIRED_REGEXP, msg);
            }
        }
    }
}
