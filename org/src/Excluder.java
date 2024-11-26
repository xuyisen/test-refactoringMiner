
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
        assertFieldExclusion("hiddenField", true, true);
    }

    @Test
    public void testSkipExplicitlySkippedFields() throws Exception {
        assertFieldExclusion("explicitlyHiddenField", true, true);
    }

    @Test
    public void testNeverSkipExposedAnnotatedFields() throws Exception {
        assertFieldExclusion("exposedField", false, false);
    }

    @Test
    public void testNeverSkipExplicitlyExposedAnnotatedFields() throws Exception {
        assertFieldExclusion("explicitlyExposedField", false, false);
    }

    @Test
    public void testDifferentSerializeAndDeserializeField() throws Exception {
        Field f = createFieldAttributes("explicitlyDifferentModeField");
        assertThat(excluder.excludeField(f, true)).isFalse();
        assertThat(excluder.excludeField(f, false)).isTrue();
    }

    private void assertFieldExclusion(String fieldName, boolean expectedWhenSerialized, boolean expectedWhenNotSerialized) throws Exception {
        Field f = createFieldAttributes(fieldName);
        assertThat(excluder.excludeField(f, true)).isEqualTo(expectedWhenSerialized);
        assertThat(excluder.excludeField(f, false)).isEqualTo(expectedWhenNotSerialized);
    }

    private static Field createFieldAttributes(String fieldName) throws Exception {
        return MockObject.class.getField(fieldName);
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
