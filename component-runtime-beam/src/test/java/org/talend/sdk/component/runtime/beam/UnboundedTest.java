/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.runtime.beam;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.junit.Rule;
import org.junit.Test;
import org.talend.sdk.component.api.input.Assessor;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.input.Split;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.runtime.input.Mapper;
import org.talend.sdk.component.runtime.manager.ComponentManager;

import lombok.RequiredArgsConstructor;

public class UnboundedTest implements Serializable {
    @Rule
    public transient final TestPipeline pipeline = TestPipeline.create();

    @Test
    public void infinite() {
        final ComponentManager manager = ComponentManager.instance();
        manager.addPlugin("target/test-classes");
        final Mapper mapper = manager.findMapper("beam-test", "inf", 1, emptyMap())
                .orElseThrow(IllegalStateException::new);
        PAssert.that(pipeline.apply(TalendIO.read(mapper)))
            .satisfies((SerializableFunction<Iterable<Record>, Void>) input -> {
                final List<Record> records = StreamSupport.stream(Spliterators.spliteratorUnknownSize(input.iterator(), Spliterator.IMMUTABLE), false)
                        .collect(toList());
                assertEquals(0, records.size());
                return null;
            });
    }

    @RequiredArgsConstructor
    @PartitionMapper(family = "beam-test", name = "inf", infinite = true)
    public static class MyCompo implements Serializable {
        private final Configuration configuration;

        @Assessor
        public long estimate() {
            return 1;
        }

        @Split
        public List<MyCompo> split() {
            return singletonList(this);
        }

        @Emitter
        public MyReader create() {
            return new MyReader(configuration);
        }

        @RequiredArgsConstructor
        public static class MyReader implements  Serializable {
            private final Configuration configuration;

            @Producer
            public Record next() {
                return null;
            }
        }

        public static class Configuration implements Serializable {

        }
    }
}
