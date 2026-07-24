package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextRequest;

import java.util.List;

interface NexusContextGateway extends AutoCloseable {
    List<ProjectInfo> listProjects();

    TechnicalContextBundle buildContext(TechnicalContextRequest request);

    @Override
    void close();

    record ProjectInfo(String id, String name, String indexStatus) {
    }
}
