package com.morpheus.application.reference;

import com.morpheus.domain.reference.ExternalReferenceTarget;

/** Optional adapter port for one external system such as MINOS, GitHub or Jira. */
public interface ExternalReferenceResolver {
    String system();

    ExternalReferenceResolverResult resolve(ExternalReferenceTarget target);
}
