/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.operations;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.utils.ExceptionUtils;
import org.cloudfoundry.utils.OperationUtils;
import org.cloudfoundry.utils.PaginationUtils;
import org.cloudfoundry.utils.ResourceUtils;
import reactor.core.publisher.Mono;
import reactor.fn.Function;
import reactor.rx.Stream;

/**
 * A builder API for creating the default implementation of the {@link CloudFoundryOperations}
 */
public final class CloudFoundryOperationsBuilder {

    private CloudFoundryClient cloudFoundryClient;

    private String organization;

    private String space;

    /**
     * Builds a new instance of the default implementation of the {@link CloudFoundryOperations} using the information provided.
     *
     * @return a new instance of the default implementation of the {@link CloudFoundryOperations}
     * @throws IllegalArgumentException if {@code cloudFoundryClient} has not been set
     */
    public CloudFoundryOperations build() {
        if (this.cloudFoundryClient == null) {
            throw new IllegalArgumentException("CloudFoundryClient must be set");
        }

        Mono<String> organizationId = getOrganizationId(this.cloudFoundryClient, this.organization);
        Mono<String> spaceId = getSpaceId(this.cloudFoundryClient, organizationId, this.space);

        return new DefaultCloudFoundryOperations(this.cloudFoundryClient, organizationId, spaceId);
    }

    /**
     * Configure the {@link CloudFoundryClient} to use
     *
     * @param cloudFoundryClient the {@link CloudFoundryClient} to use
     * @return {@code this}
     */
    public CloudFoundryOperationsBuilder cloudFoundryClient(CloudFoundryClient cloudFoundryClient) {
        this.cloudFoundryClient = cloudFoundryClient;
        return this;
    }

    /**
     * Configure the organization and space to target
     *
     * @param organization the organization to target
     * @param space        the space to target
     * @return {@code this}
     */
    public CloudFoundryOperationsBuilder target(String organization, String space) {
        this.organization = organization;
        this.space = space;
        return this;
    }

    /**
     * Configure the organization to target
     *
     * @param organization the organization to target
     * @return {@code this}
     */
    public CloudFoundryOperationsBuilder target(String organization) {
        this.organization = organization;
        return this;
    }

    private static Mono<OrganizationResource> getOrganization(CloudFoundryClient cloudFoundryClient, String organization) {
        return requestOrganizations(cloudFoundryClient, organization)
            .single()
            .otherwise(ExceptionUtils.<OrganizationResource>convert("Organization %s does not exist", organization));
    }

    private static Mono<String> getOrganizationId(final CloudFoundryClient cloudFoundryClient, final String organization) {
        if (organization == null) {
            return Mono.error(new IllegalStateException("No organization targeted"));
        }

        Mono<String> organizationId = getOrganization(cloudFoundryClient, organization)
            .map(ResourceUtils.extractId())
            .as(OperationUtils.<String>promise());

        organizationId.get();
        return organizationId;
    }

    private static Mono<SpaceResource> getSpace(CloudFoundryClient cloudFoundryClient, String organizationId, String space) {
        return requestSpaces(cloudFoundryClient, organizationId, space)
            .single()
            .otherwise(ExceptionUtils.<SpaceResource>convert("Space %s does not exist", space));
    }

    private static Mono<String> getSpaceId(final CloudFoundryClient cloudFoundryClient, Mono<String> organizationId, final String space) {
        if (space == null) {
            return Mono.error(new IllegalStateException("No space targeted"));
        }

        Mono<String> spaceId = organizationId
            .then(new Function<String, Mono<SpaceResource>>() {

                @Override
                public Mono<SpaceResource> apply(String organizationId) {
                    return getSpace(cloudFoundryClient, organizationId, space);

                }

            })
            .map(ResourceUtils.extractId())
            .as(OperationUtils.<String>promise());

        spaceId.get();
        return spaceId;
    }

    private static Stream<OrganizationResource> requestOrganizations(final CloudFoundryClient cloudFoundryClient, final String organization) {
        return PaginationUtils
            .requestResources(new Function<Integer, Mono<ListOrganizationsResponse>>() {

                @Override
                public Mono<ListOrganizationsResponse> apply(Integer page) {
                    return cloudFoundryClient.organizations()
                        .list(ListOrganizationsRequest.builder()
                            .name(organization)
                            .page(page)
                            .build());
                }
            });
    }

    private static Stream<SpaceResource> requestSpaces(final CloudFoundryClient cloudFoundryClient, final String organizationId, final String space) {
        return PaginationUtils
            .requestResources(new Function<Integer, Mono<ListSpacesResponse>>() {

                @Override
                public Mono<ListSpacesResponse> apply(Integer page) {
                    return cloudFoundryClient.spaces()
                        .list(ListSpacesRequest.builder()
                            .organizationId(organizationId)
                            .name(space)
                            .page(page)
                            .build());
                }

            });
    }

}
