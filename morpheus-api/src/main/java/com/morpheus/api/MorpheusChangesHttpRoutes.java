package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project change reads, context and controlled mutations. */
final class MorpheusChangesHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusAugmentedContextApiService augmentedContextService;
    private final MorpheusJarvisOrchestrationApiService jarvisOrchestrationService;
    private final MorpheusControlledLifecycleApiService controlledLifecycleService;
    private final MorpheusHttpRequestDecoder requestDecoder;

    MorpheusChangesHttpRoutes(
            MorpheusApiService service,
            MorpheusAugmentedContextApiService augmentedContextService,
            MorpheusJarvisOrchestrationApiService jarvisOrchestrationService,
            MorpheusControlledLifecycleApiService controlledLifecycleService,
            MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.augmentedContextService = Objects.requireNonNull(augmentedContextService, "augmentedContextService");
        this.jarvisOrchestrationService = Objects.requireNonNull(jarvisOrchestrationService, "jarvisOrchestrationService");
        this.controlledLifecycleService = Objects.requireNonNull(controlledLifecycleService, "controlledLifecycleService");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    MorpheusHttpRouteResponse route(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        if (segments.size() == 5 && segments.get(4).equals("augmented-context")) {
            MorpheusHttpRouteGuards.requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(augmentedContextService.change(
                    projectId,
                    segments.get(3),
                    requestDecoder.readRequiredJson(exchange, AugmentedContextRequest.class)));
        }
        if (segments.size() == 5 && segments.get(4).equals("transition-check")) {
            MorpheusHttpRouteGuards.requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(jarvisOrchestrationService.transition(
                    projectId,
                    segments.get(3),
                    requestDecoder.readRequiredJson(exchange, TransitionCheckRequest.class)));
        }
        if (segments.size() == 5 && segments.get(4).equals("lifecycle-transitions")) {
            MorpheusHttpRouteGuards.requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(controlledLifecycleService.apply(
                    projectId,
                    segments.get(3),
                    requestDecoder.readRequiredJson(exchange, LifecycleMutationRequest.class)));
        }
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            return ok(service.listChanges(projectId, page(query)));
        }
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.change(projectId, segments.get(3)));
        }
        if (segments.size() != 5) {
            throw ApiFailure.notFound("unknown changes route");
        }

        String changeId = segments.get(3);
        String child = segments.get(4);
        return switch (child) {
            case "constraints" -> ok(service.constraints(projectId, changeId, page(query)));
            case "acceptance-criteria" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.acceptanceCriteria(projectId, changeId));
            }
            case "design-decisions" -> ok(service.designDecisions(projectId, changeId, page(query)));
            case "implementation-tasks" -> ok(service.implementationTasks(projectId, changeId, page(query)));
            case "context" -> {
                query.rejectUnknown(Set.of("depth"));
                int depth = query.intValue(
                        "depth", MorpheusApiService.DEFAULT_DEPTH, 1, MorpheusApiService.MAX_DEPTH);
                yield ok(service.changeContext(projectId, changeId, depth));
            }
            case "status" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.changeStatus(projectId, changeId));
            }
            case "blocking-conditions" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.blockingConditions(projectId, changeId));
            }
            case "orchestration" -> {
                query.rejectUnknown(Set.of("lifecycleState", "abandonmentReason"));
                yield ok(jarvisOrchestrationService.state(
                        projectId,
                        changeId,
                        query.string("lifecycleState").map(String::trim).filter(value -> !value.isEmpty()),
                        query.string("abandonmentReason").map(String::trim).filter(value -> !value.isEmpty())));
            }
            default -> throw ApiFailure.notFound("unknown change subresource: " + child);
        };
    }

    private PageRequest page(MorpheusHttpQuery query) {
        query.rejectUnknown(Set.of("offset", "limit"));
        return new PageRequest(
                query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
