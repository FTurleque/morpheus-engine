package com.morpheus.application.ingestion;

import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.provider.ProviderId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A read that a publication refuses: the provider returned no content, or did not read every category a publication
 * stores. It is a state failure of the workspace, so every surface answers it as one. The message names the provider
 * and each unread category with the status it reported, and no path.
 */
public final class IncompleteProjectReadException extends IllegalStateException {
    private final transient List<UnreadCategory> unreadCategories;

    IncompleteProjectReadException(ProviderId providerId, boolean contentReturned, List<UnreadCategory> unreadCategories) {
        super(message(providerId, contentReturned, unreadCategories));
        this.unreadCategories = List.copyOf(unreadCategories);
    }

    /** The categories that were not read, in the order of {@link ReadCategory}. */
    public List<UnreadCategory> unreadCategories() {
        return unreadCategories;
    }

    private static String message(ProviderId providerId, boolean contentReturned, List<UnreadCategory> unread) {
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
