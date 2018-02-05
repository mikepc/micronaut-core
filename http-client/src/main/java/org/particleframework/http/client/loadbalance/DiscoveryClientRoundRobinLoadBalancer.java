/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.http.client.loadbalance;

import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.exceptions.DiscoveryException;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>A {@link LoadBalancer} that uses the {@link DiscoveryClient} and a {@link ServiceInstance} ID to automatically
 * load balance between discovered clients in a non-blocking manner</p>
 *
 * <p>Note that the when {@link DiscoveryClient} caching is enabled then this load balancer may not always have the latest
 * server list from the {@link DiscoveryClient} (the default TTL is 30 seconds)</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
public class DiscoveryClientRoundRobinLoadBalancer implements LoadBalancer {

    private final String serviceID;
    private final DiscoveryClient discoveryClient;
    private final AtomicInteger index = new AtomicInteger(0);

    @Inject
    public DiscoveryClientRoundRobinLoadBalancer(@Argument String serviceID, DiscoveryClient discoveryClient) {
        this.serviceID = serviceID;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Publisher<URL> select(Object discriminator) {
        return Publishers.map(discoveryClient.getInstances(serviceID), serviceInstances -> {
            int len = serviceInstances.size();
            if(len == 0) {
                throw new DiscoveryException("No available services for ID: " + serviceID);
            }
            int i = index.getAndAccumulate(len, (cur, n) -> cur >= n - 1 ? 0 : cur + 1);
            ServiceInstance instance = serviceInstances.get(i);
            try {
                return instance.getURI().toURL();
            } catch (MalformedURLException e) {
                throw new HttpClientException("Invalid service URI: " + instance.getURI());
            }
        });
    }
}
