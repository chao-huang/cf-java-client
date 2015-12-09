/*
 * Copyright 2013-2015 the original author or authors.
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
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.reactivestreams.Publisher;
import reactor.fn.Function;
import reactor.rx.Streams;

final class DefaultSpaces extends AbstractOperations implements Spaces {

    private final CloudFoundryClient cloudFoundryClient;

    private final String organizationId;

    DefaultSpaces(CloudFoundryClient cloudFoundryClient, String organizationId) {
        this.cloudFoundryClient = cloudFoundryClient;
        this.organizationId = organizationId;
    }

    @Override
    public Publisher<Space> list() {
        if (this.organizationId == null) {
            throw new IllegalStateException("No organization targeted");
        }

        return paginate(new Function<Integer, ListSpacesRequest>() {

            @Override
            public ListSpacesRequest apply(Integer page) {
                return ListSpacesRequest.builder().organizationId(DefaultSpaces.this.organizationId).page(page).build();
            }

        }, new Function<ListSpacesRequest, Publisher<ListSpacesResponse>>() {

            @Override
            public Publisher<ListSpacesResponse> apply(ListSpacesRequest request) {
                return DefaultSpaces.this.cloudFoundryClient.spaces().list(request);
            }

        }).flatMap(new Function<ListSpacesResponse, Publisher<SpaceResource>>() {

            @Override
            public Publisher<SpaceResource> apply(ListSpacesResponse r) {
                return Streams.from(r.getResources());
            }

        }).map(new Function<SpaceResource, Space>() {

            @Override
            public Space apply(SpaceResource resource) {
                return Space.builder().id(resource.getMetadata().getId()).name(resource.getEntity().getName()).build();
            }

        });
    }

}