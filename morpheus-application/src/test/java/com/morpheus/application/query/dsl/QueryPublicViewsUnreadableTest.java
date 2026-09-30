package com.morpheus.application.query.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewEntry;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A listing names a row it could not read; it neither drops it nor fails on it. */
class QueryPublicViewsUnreadableTest {
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void anUnreadableEntryIsProjectedWithItsIdentityAndReasonAndNoQuery() {
        SavedViewId id = SavedViewId.generate();
        SavedViewDefinition readable = new SavedViewDefinition(
                SavedViewId.generate(), "readable",
                QueryDefinition.all(new ProjectQueryScope(ProjectSpecificationId.generate()),
                        QueryEntityType.CHANGE, QueryPage.first(10)),
                1L, SavedViewStatus.ACTIVE, NOW, NOW);

        List<Object> views = QueryPublicViews.savedViews(List.of(
                new SavedViewEntry.Readable(readable),
                new SavedViewEntry.Unreadable(id, "broken", 3L, SavedViewStatus.ACTIVE, NOW, NOW, "predicate values count is outside supported bounds: 65")));

        assertEquals(2, views.size());
        assertTrue(views.get(0) instanceof QueryPublicViews.SavedViewView, "a readable view keeps its record and wire shape");
        QueryPublicViews.UnreadableSavedViewView degraded =
                (QueryPublicViews.UnreadableSavedViewView) views.get(1);
        assertEquals(id.toString(), degraded.id());
        assertEquals(3L, degraded.revision());
        assertTrue(degraded.unreadableReason().contains("outside supported bounds"));
    }
}
