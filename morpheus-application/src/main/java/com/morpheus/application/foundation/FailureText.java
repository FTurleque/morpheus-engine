package com.morpheus.application.foundation;

/**
 * The text of a failure: its message, or the simple name of its type when the message is absent or blank, so that no
 * caller receives a {@code null} or an empty reason. Every module forms the text of a failure here, whatever the
 * declared type of the failure; the form filtered for a caller outside the machine is
 * {@code ServerLocationDisclosure.relayableText}, which starts from this text. Nothing is filtered here.
 */
public final class FailureText {

    private FailureText() {
    }

    public static String of(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
