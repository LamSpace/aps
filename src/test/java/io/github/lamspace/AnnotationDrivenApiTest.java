/*
 * Copyright 2026 Lam Tong
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

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationDrivenApiTest {

    @Test
    void aroundAnnotationHasRuntimeRetentionAndDefaults() throws Exception {
        assertTrue(Around.class.isAnnotationPresent(Retention.class));
        assertEquals(RetentionPolicy.RUNTIME,
                Around.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.METHOD,
                Around.class.getAnnotation(Target.class).value()[0]);

        assertEquals("", Around.class.getMethod("value").getDefaultValue());
        assertArrayEquals(new String[0],
                (String[]) Around.class.getMethod("glob").getDefaultValue());
        assertArrayEquals(new String[0],
                (String[]) Around.class.getMethod("regex").getDefaultValue());
        assertArrayEquals(new Class[0],
                (Class[]) Around.class.getMethod("annotatedWith").getDefaultValue());
    }

    @Test
    void interceptAnnotationHasRuntimeRetentionAndTypeTarget() {
        assertEquals(RetentionPolicy.RUNTIME,
                Intercept.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.TYPE,
                Intercept.class.getAnnotation(Target.class).value()[0]);
    }
}
