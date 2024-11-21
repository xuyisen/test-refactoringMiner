/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.gson;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.annotations.Expose;
import com.google.gson.internal.Excluder;
import java.lang.reflect.Field;
import org.junit.Test;

/**
 * Unit tests for GsonBuilder.REQUIRE_EXPOSE_DESERIALIZE.
 *
 * @author Joel Leitch
 */
public class ExposeAnnotationExclusionStrategyTest {
    private Excluder excluder = Excluder.DEFAULT.excludeFieldsWithoutExposeAnnotation();

    @Test
    public void testNeverSkipClasses() {
        assertThat(excluder.excludeClass(MockObject.class, true)).isFalse();
        assertThat(excluder.excludeClass(MockObject.class, false)).isFalse();
    }

    @Test
    public void testSkipNonAnnotatedFields() throws Exception {
        Field f = createFieldAttributes("hiddenField");
        assertFieldExclusion(f, true, true);
        assertFieldExclusion(f, false, true);
    }

    @Test
    public void testSkipExplicitlySkippedFields() throws Exception {
        Field f = createFieldAttributes("explicitlyHiddenField");
        assertFieldExclusion(f, true, true);
        assertFieldExclusion(f, false, true);
    }

    @Test
    public void testNeverSkipExposedAnnotatedFields() throws Exception {
        Field f = createFieldAttributes("exposedField");
        assertFieldExclusion(f, true, false);
        assertFieldExclusion(f, false, false);
    }

    @Test
    public void testNeverSkipExplicitlyExposedAnnotatedFields() throws Exception {
        Field f = createFieldAttributes("explicitlyExposedField");
        assertFieldExclusion(f, true, false);
        assertFieldExclusion(f, false, false);
    }

    @Test
    public void testDifferentSerializeAndDeserializeField() throws Exception {
        Field f = createFieldAttributes("explicitlyDifferentModeField");
        assertFieldExclusion(f, true, false);
        assertFieldExclusion(f, false, true);
    }

    private static Field createFieldAttributes(String fieldName) throws Exception {
        return MockObject.class.getField(fieldName);
    }

    private void assertFieldExclusion(Field field, boolean serialize, boolean expected) throws Exception {
        boolean actual = excluder.excludeField(field, serialize);
        assertThat(actual).isEqualTo(expected);
    }

    @SuppressWarnings("unused")
    private static class MockObject {
        @Expose public final int exposedField = 0;

        @Expose(serialize = true, deserialize = true)
        public final int explicitlyExposedField = 0;

        @Expose(serialize = false, deserialize = false)
        public final int explicitlyHiddenField = 0;

        @Expose(serialize = true, deserialize = false)
        public final int explicitlyDifferentModeField = 0;

        public final int hiddenField = 0;
    }
}