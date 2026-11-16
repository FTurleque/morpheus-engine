package com.morpheus.application.ingestion;

import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A read that a publication refuses: the provider returned no content, or did not read every category a publication
 * stores. It is a state failure of the workspace, so every surface answers it as one. The message names the provider,
 * each unread category with the status it reported, and the first errors the provider reported, which name the file
 * at fault relative to the workspace.
 *
 * <p>The message relays the text of those errors only. Their code, details and source are in
 * {@link #providerErrors()}, for a surface that relays a diagnostic as the provider produced it; the message itself
 * reaches callers outside the machine, who receive a detail only through an allowlisted projection.</p>
 */
public final class IncompleteProjectReadException extends IllegalStateException {
    /** The most errors of the provider the message relays. */
    static final int MAX_RELAYED_ERRORS = 5;

    private final transient List<UnreadCategory> unreadCategories;
    private final transient List<Diagnostic> providerErrors;

    IncompleteProjectReadException(
            ProviderId providerId,
            boolean contentReturned,
            List<UnreadCategory> unreadCategories,
            List<Diagnostic> providerErrors) {
        super(message(providerId, contentReturned, unreadCategories,
                providerErrors.stream().map(Diagnostic::message).distinct().toList()));
        this.unreadCategories = List.copyOf(unreadCategories);
        this.providerErrors = List.copyOf(providerErrors);
    }

    /** The categories that were not read, in the order of {@link ReadCategory}. */
    public List<UnreadCategory> unreadCategories() {
        return unreadCategories;
    }

    /** Every distinct error diagnostic the provider reported, in the order it reported them. */
    public List<Diagnostic> providerErrors() {
        return providerErrors;
    }

    private static String message(
            ProviderId providerId, boolean contentReturned, List<UnreadCategory> unread, List<String> errors) {
        Objects.requireNonNull(providerId, "providerId");
        StringBuilder message = new StringBuilder("project read is incomplete, nothing was published: provider ")
                .append(providerId.value());
        if (!contentReturned) {
            message.append(" returned no content");
        }
        if (!unread.isEmpty()) {
            message.append(contentReturned ? " did not read " : "; unread categories ")
                    .append(unread.stream()
                            .map(item -> item.category().name() + "=" + item.reported().map(Enum::name).orElse("UNREPORTED"))
                            .collect(Collectors.joining(", ")));
        }
        if (!errors.isEmpty()) {
            message.append("; errors: ").append(String.join("; ", errors.subList(0, Math.min(errors.size(),
                    MAX_RELAYED_ERRORS))));
            if (errors.size() > MAX_RELAYED_ERRORS) {
                message.append("; and ").append(errors.size() - MAX_RELAYED_ERRORS).append(" more");
            }
        }
        return message.toString();
    }

    /** One category a publication stores that the provider did not read, and the status it reported, if any. */
    public record UnreadCategory(ReadCategory category, Optional<ReadCategoryStatus> reported) {
        public UnreadCategory {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(reported, "reported");
        }
    }
}
