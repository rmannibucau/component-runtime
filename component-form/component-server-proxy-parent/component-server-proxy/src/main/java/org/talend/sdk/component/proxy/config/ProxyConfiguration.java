/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.proxy.config;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.NONE;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.Invocation;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import lombok.Getter;

@Getter
@ApplicationScoped
public class ProxyConfiguration {

    private static final String PREFIX = "talend.component.proxy.";

    @Inject
    @ConfigProperty(name = PREFIX + "server.base")
    private String targetServerBase;

    @Inject
    @ConfigProperty(name = PREFIX + "client.providers")
    private Collection<Class> clientProviders;

    @Inject
    @Getter(NONE)
    @ConfigProperty(name = PREFIX + "processing.headers")
    private String headers;

    @Getter
    private Function<Invocation.Builder, Invocation.Builder> headerAppender;

    @PostConstruct
    private void init() {
        if (headers == null || headers.isEmpty()) {
            headerAppender = identity();
        } else {
            final Properties properties = new Properties();
            try (final Reader reader = new StringReader(headers.trim())) {
                properties.load(reader);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
            final Map<String, Supplier<String>> providers =
                    properties.stringPropertyNames().stream().collect(toMap(identity(), e -> {
                        final String value = properties.getProperty(e);
                        if (value.contains("${") && value.contains("}")) {
                            return () -> value; // todo
                        }
                        return () -> value;
                    }));
            headerAppender = t -> {
                Invocation.Builder out = t;
                for (final Map.Entry<String, Supplier<String>> header : providers.entrySet()) {
                    out = out.header(header.getKey(), header.getValue().get());
                }
                return out;
            };
        }
    }
}