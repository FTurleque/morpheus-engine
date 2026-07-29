package com.morpheus.application.policy;

import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Objects;

/** Explicit governance scope; never derived from workspace, repository, provider or path. */
public sealed interface PolicyScope permits PolicyScope.Project, PolicyScope.Portfolio {
    String type();

    String identity();

    record Project(ProjectSpecificationId projectId) implements PolicyScope {
        public Project {
            Objects.requireNonNull(projectId, "projectId");
        }

        @Override
        public String type() {
            return "PROJECT";
        }

        @Override
        public String identity() {
            return projectId.toString();
        }
    }

    record Portfolio(PortfolioId portfolioId) implements PolicyScope {
        public Portfolio {
            Objects.requireNonNull(portfolioId, "portfolioId");
        }

        @Override
        public String type() {
            return "PORTFOLIO";
        }

        @Override
        public String identity() {
            return portfolioId.toString();
        }
    }
}