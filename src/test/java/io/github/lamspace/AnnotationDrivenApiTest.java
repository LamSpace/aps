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
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    public static class Greeter {
        public String getGreeting() { return "hello"; }
        public void setGreeting(String g) { }
        public String format(String prefix) { return prefix + ":ok"; }
    }

    @Intercept
    public static class GetterInterceptor {
        final AtomicReference<String> lastMethod = new AtomicReference<>();

        @Around("get*")
        public Object measure(Object proxy, Method method, Object[] args)
                throws Throwable {
            lastMethod.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void singleGlobRoutesMatchedMethodsAndPassthroughsOthers() {
        GetterInterceptor interceptor = new GetterInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());
        assertEquals("getGreeting", interceptor.lastMethod.get());

        proxy.setGreeting("x");
        assertEquals("p:ok", proxy.format("p"));
        assertEquals("getGreeting", interceptor.lastMethod.get());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tx {}

    public static class Service {
        @Tx public String save(String x) { return "saved:" + x; }
        @Tx public int load() { return 1; }
        public String ping() { return "pong"; }
    }

    @Intercept
    public static class TxInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(annotatedWith = Tx.class)
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void annotatedWithMatchesOnlyAnnotatedMethods() {
        TxInterceptor interceptor = new TxInterceptor();
        Service proxy = AcceleratedProxy.intercept(Service.class, interceptor);

        assertEquals("saved:a", proxy.save("a"));
        assertEquals(1, proxy.load());
        assertEquals("pong", proxy.ping());
        assertEquals(2, interceptor.calls.get());
    }

    @Intercept
    public static class RegexInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(regex = "get[A-Z].*")
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void regexMatchesMethodName() {
        RegexInterceptor interceptor = new RegexInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());
        assertEquals(1, interceptor.calls.get());
    }

    @Intercept
    public static class AndInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(value = "get*", annotatedWith = Tx.class)
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    public static class MixedService {
        @Tx public String getTagged() { return "tagged"; }
        public String getPlain() { return "plain"; }
    }

    @Test
    void globAndAnnotatedWithCombineWithAnd() {
        AndInterceptor interceptor = new AndInterceptor();
        MixedService proxy = AcceleratedProxy.intercept(MixedService.class, interceptor);

        assertEquals("tagged", proxy.getTagged());
        assertEquals("plain", proxy.getPlain());
        assertEquals(1, interceptor.calls.get());
    }

    @Intercept
    public static class MultiGlobInterceptor {
        final AtomicReference<String> last = new AtomicReference<>();

        @Around(glob = {"get*", "is*"})
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            last.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    public static class HasGetterAndIsser {
        public String getName() { return "n"; }
        public boolean isReady() { return true; }
        public String ping() { return "p"; }
    }

    @Test
    void multipleGlobsOrWithinDimension() {
        MultiGlobInterceptor interceptor = new MultiGlobInterceptor();
        HasGetterAndIsser proxy = AcceleratedProxy.intercept(HasGetterAndIsser.class, interceptor);

        assertEquals("n", proxy.getName());
        assertTrue(proxy.isReady());
        assertEquals("p", proxy.ping());
        assertEquals("isReady", interceptor.last.get());
    }

    @Intercept
    public static class BadRegexInterceptor {
        @Around(regex = "[")
        public Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void invalidRegexFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class,
                        new BadRegexInterceptor()));
    }

    @Intercept
    public static class ArgsCapturingInterceptor {
        final Object[] captured = new Object[3];
        final AtomicBoolean sawDispatchTarget = new AtomicBoolean();

        @Around("get*")
        public Object capture(Object proxy, Method method, Object[] args)
                throws Throwable {
            captured[0] = proxy;
            captured[1] = method;
            captured[2] = args;
            sawDispatchTarget.set(proxy instanceof DispatchTarget);
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void adapterPassesProxyMethodArgs() {
        ArgsCapturingInterceptor interceptor = new ArgsCapturingInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());

        assertSame(proxy, interceptor.captured[0]);
        assertEquals("getGreeting", ((Method) interceptor.captured[1]).getName());
        assertEquals(0, ((Object[]) interceptor.captured[2]).length);
        assertTrue(interceptor.sawDispatchTarget.get());
    }

    @Intercept
    public static class StringReturnInterceptor {
        @Around("get*")
        public String shorten(Object proxy, Method method, Object[] args)
                throws Throwable {
            return "[" + AcceleratedProxy.invokeSuper(proxy, method, args) + "]";
        }
    }

    @Test
    void subtypeReturnIsWidenedToObject() {
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class,
                new StringReturnInterceptor());
        assertEquals("[hello]", proxy.getGreeting());
    }

    @Test
    void nullTargetFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(null, new GetterInterceptor()));
    }

    @Test
    void nullInterceptorFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, null));
    }

    public static class NotAnnotated {
        @Around("get*")
        public Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void nonInterceptClassFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new NotAnnotated()));
    }

    @Intercept
    public static class NoAroundMethod { }

    @Test
    void noAroundMethodFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new NoAroundMethod()));
    }

    @Intercept
    public static class WrongParamCount {
        @Around("get*")
        public Object handle(Object proxy, Method method) {
            return null;
        }
    }

    @Test
    void wrongParameterCountFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new WrongParamCount()));
    }

    @Intercept
    public static class VoidReturn {
        @Around("get*")
        public void handle(Object proxy, Method method, Object[] args) { }
    }

    @Test
    void voidReturnFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new VoidReturn()));
    }

    @Intercept
    public static class StaticAround {
        @Around("get*")
        public static Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void staticAroundFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new StaticAround()));
    }
}
