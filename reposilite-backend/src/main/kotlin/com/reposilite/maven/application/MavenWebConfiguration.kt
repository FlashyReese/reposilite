/*
 * Copyright (c) 2021 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.maven.application

import com.reposilite.Reposilite
import com.reposilite.config.Configuration.RepositoryConfiguration
import com.reposilite.journalist.Journalist
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.MetadataService
import com.reposilite.maven.ProxyService
import com.reposilite.maven.RepositoryFactory
import com.reposilite.maven.RepositorySecurityProvider
import com.reposilite.maven.RepositoryService
import com.reposilite.maven.infrastructure.MavenApiEndpoints
import com.reposilite.maven.infrastructure.MavenEndpoints
import com.reposilite.shared.RemoteClient
import com.reposilite.web.ReposiliteRoutes
import com.reposilite.web.WebConfiguration
import java.nio.file.Path

internal object MavenWebConfiguration : WebConfiguration {

    fun createFacade(journalist: Journalist, workingDirectory: Path, remoteClient: RemoteClient, repositories: Map<String, RepositoryConfiguration>): MavenFacade {
        val repositoryFactory = RepositoryFactory(journalist, workingDirectory)
        val securityProvider = RepositorySecurityProvider()

        val repositoryService = repositories
            .mapValues { (repositoryName, repositoryConfiguration) -> repositoryFactory.createRepository(repositoryName, repositoryConfiguration) }
            .let { RepositoryService(journalist, it, securityProvider) }

        return MavenFacade(
            journalist,
            securityProvider,
            repositoryService,
            ProxyService(remoteClient),
            MetadataService(repositoryService)
        )
    }

    override fun routing(reposilite: Reposilite): Set<ReposiliteRoutes> = setOf(
        MavenEndpoints(reposilite.mavenFacade, reposilite.frontendFacade),
        MavenApiEndpoints(reposilite.mavenFacade)
    )

}