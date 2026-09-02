package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusM13McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void discoversBothM13ToolsOverRealStdioWithoutNexusInstalled() throws Exception {
        Path database = tempDirectory.resolve("m13-mcp.db");

        try (McpStdioSession session = McpStdioSession.start(database)) {
            session.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m13-test\",\"version\":\"1\"}}}");
            session.readLine(Duration.ofSeconds(10));
            session.send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");
            session.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String tools = session.readLine(Duration.ofSeconds(10));
            assertTrue(tools.contains("get_augmented_requirement_context"), tools);
            assertTrue(tools.contains("get_augmented_change_context"), tools);
            assertTrue(tools.contains("list_external_references"), tools);

            String projectId = ProjectSpecificationId.generate().toString();
            String requirementId = RequirementId.generate().toString();
            session.send("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_augmented_requirement_context\",\"arguments\":{\"projectId\":\""
                    + projectId + "\",\"requirementId\":\"" + requirementId
                    + "\",\"nexusProject\":\"morpheus-engine\"}}}");
            String missingProject = session.readLine(Duration.ofSeconds(10));
            assertTrue(missingProject.contains("isError") || missingProject.contains("error"), missingProject);
            assertTrue(missingProject.contains("project not found"), missingProject);
        }
    }
}
