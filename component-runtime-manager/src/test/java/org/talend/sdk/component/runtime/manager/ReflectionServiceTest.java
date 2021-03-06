/**
 *  Copyright (C) 2006-2017 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.talend.sdk.component.runtime.manager;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;
import org.talend.sdk.component.runtime.manager.reflect.ParameterModelService;
import org.talend.sdk.component.runtime.manager.reflect.ReflectionService;
import org.talend.sdk.component.runtime.manager.test.MethodsHolder;

public class ReflectionServiceTest {

    private final ReflectionService reflectionService = new ReflectionService(new ParameterModelService());

    @Test
    public void primitive() throws NoSuchMethodException {
        { // from string
            final Object[] params = reflectionService
                    .parameterFactory(MethodsHolder.class.getMethod("primitives", String.class, String.class, int.class),
                            emptyMap())
                    .apply(new HashMap<String, String>() {

                        {
                            put("url", "http://foo");
                            put("arg1", "default");
                            put("port", "1");
                        }
                    });
            assertEquals("http://foo", params[0]);
            assertEquals("default", params[1]);
            assertEquals(1, params[2]);
        }
        { // partial
            final Object[] params = reflectionService
                    .parameterFactory(MethodsHolder.class.getMethod("primitives", String.class, String.class, int.class),
                            emptyMap())
                    .apply(new HashMap<String, String>() {

                        {
                            put("port", "1");
                        }
                    });
            assertNull(params[0]);
            assertNull(params[1]);
            assertEquals(1, params[2]);
        }
        { // exact type
            final Object[] params = reflectionService
                    .parameterFactory(MethodsHolder.class.getMethod("primitives", String.class, String.class, int.class),
                            emptyMap())
                    .apply(new HashMap<String, String>() {

                        {
                            put("port", "1");
                        }
                    });
            assertEquals(1, params[2]);
        }
    }

    @Test
    public void collection() throws NoSuchMethodException {
        final Object[] params = reflectionService
                .parameterFactory(MethodsHolder.class.getMethod("collections", List.class, List.class, Map.class), emptyMap())
                .apply(new HashMap<String, String>() {

                    {
                        put("urls[0]", "http://foo");
                        put("urls[1]", "https://bar");
                        put("ports[0]", "1234");
                        put("ports[1]", "5678");
                        put("mapping.key[0]", "key1");
                        put("mapping.value[0]", "value1");
                        put("mapping.key[1]", "key2");
                        put("mapping.value[1]", "value2");
                    }
                });
        assertEquals(asList("http://foo", "https://bar"), params[0]);
        assertEquals(asList(1234, 5678), params[1]);
        assertEquals(new HashMap<String, String>() {

            {
                put("key1", "value1");
                put("key2", "value2");
            }
        }, params[2]);
    }

    @Test
    public void array() throws NoSuchMethodException {
        final Object[] params = reflectionService
                .parameterFactory(MethodsHolder.class.getMethod("array", MethodsHolder.Array.class), emptyMap())
                .apply(new HashMap<String, String>() {

                    {
                        put("arg0.urls[0]", "http://foo");
                        put("arg0.urls[1]", "https://bar");
                    }
                });
        assertEquals(1, params.length);
        assertThat(params[0], instanceOf(MethodsHolder.Array.class));
        assertArrayEquals(new String[] { "http://foo", "https://bar" }, MethodsHolder.Array.class.cast(params[0]).getUrls());
    }

    @Test
    public void object() throws NoSuchMethodException {
        final Object[] params = reflectionService
                .parameterFactory(MethodsHolder.class.getMethod("object", MethodsHolder.Config.class, MethodsHolder.Config.class),
                        emptyMap())
                .apply(new HashMap<String, String>() {

                    {
                        put("arg0.urls[0]", "http://foo");
                        put("arg0.urls[1]", "https://bar");
                        put("prefixed.urls[0]", "http://foo2");
                        put("prefixed.urls[1]", "https://bar2");

                        put("arg0.mapping.key[0]", "key1");
                        put("arg0.mapping.value[0]", "val1");
                        put("arg0.mapping.key[1]", "key2");
                        put("arg0.mapping.value[1]", "val2");
                    }
                });
        Stream.of(params).forEach(p -> assertThat(p, instanceOf(MethodsHolder.Config.class)));
        final MethodsHolder.Config[] configs = Stream.of(params).map(MethodsHolder.Config.class::cast)
                .toArray(MethodsHolder.Config[]::new);
        assertEquals(asList("http://foo", "https://bar"), configs[0].getUrls());
        assertEquals(asList("http://foo2", "https://bar2"), configs[1].getUrls());
        assertEquals(new HashMap<String, String>() {

            {
                put("key1", "val1");
                put("key2", "val2");
            }
        }, configs[0].getMapping());
        assertNull(configs[1].getMapping());
    }

    @Test
    public void nestedObject() throws NoSuchMethodException {
        final Object[] params = reflectionService
                .parameterFactory(MethodsHolder.class.getMethod("nested", MethodsHolder.ConfigOfConfig.class), emptyMap())
                .apply(new HashMap<String, String>() {

                    {
                        put("arg0.direct.urls[0]", "http://foo");
                        put("arg0.direct.urls[1]", "https://bar");
                        put("arg0.multiple[0].urls[0]", "http://foo1");
                        put("arg0.multiple[0].urls[1]", "https://bar1");
                        put("arg0.multiple[1].urls[0]", "http://foo2");
                        put("arg0.multiple[1].urls[1]", "https://bar2");
                        put("arg0.keyed.key[0]", "k1");
                        put("arg0.keyed.value[0].urls[0]", "v1");
                        put("arg0.keyed.value[0].urls[1]", "v2");
                        put("arg0.keyed.key[1]", "k2");
                        put("arg0.keyed.value[1].urls[0]", "v3");
                        put("arg0.keyed.value[1].urls[1]", "v4");
                        put("arg0.passthrough", "ok");
                    }
                });
        assertThat(params[0], instanceOf(MethodsHolder.ConfigOfConfig.class));
        final MethodsHolder.ConfigOfConfig value = MethodsHolder.ConfigOfConfig.class.cast(params[0]);
        assertEquals("ok", value.getPassthrough());
        assertNotNull(value.getDirect());
        assertEquals(asList("http://foo", "https://bar"), value.getDirect().getUrls());
        assertNotNull(value.getMultiple());
        assertEquals(2, value.getMultiple().size());
        assertEquals(asList("http://foo1", "https://bar1"), value.getMultiple().get(0).getUrls());
        assertEquals(asList("http://foo2", "https://bar2"), value.getMultiple().get(1).getUrls());
        assertEquals(2, value.getKeyed().size());
        assertEquals(new HashSet<>(asList("k1", "k2")), value.getKeyed().keySet());
        assertEquals(asList("v1", "v2"), value.getKeyed().get("k1").getUrls());
        assertEquals(asList("v3", "v4"), value.getKeyed().get("k2").getUrls());
    }
}
