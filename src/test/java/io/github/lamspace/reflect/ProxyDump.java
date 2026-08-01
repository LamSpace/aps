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

package io.github.lamspace.reflect;

import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Dumps generated proxy classes for manual inspection with javap.
 *
 * Run with:
 *   mvn -s /home/lam/repo/settings.xml test-compile exec:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.lamspace.reflect.ProxyDump
 *   -Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true
 *
 * Then inspect with:
 *   javap -c -p /tmp/proxy-dump/...
 */
public class ProxyDump {

    // ----- Interface + Impl -----
    public interface StringOp {
        String call(String input);
    }

    static class StringOpImpl implements StringOp {
        public String call(String input) {
            return "Hello, " + input;
        }
    }

    // ----- Concrete class (no interface) -----
    static class Greeter {
        public String hello(String name) {
            return "Hello, " + name;
        }

        public int add(int a, int b) {
            return a + b;
        }
    }

    public static void main(String[] args) throws Exception {
        Path proxyDir = Paths.get("/tmp/proxy-dump");
        Files.createDirectories(proxyDir);

        // ================================================================
        // 1. Java Proxy (interface-based) — dump via system property
        //    jdk.proxy.ProxyGenerator.saveGeneratedFiles=true is set at
        //    JVM launch. After creating the proxy, the class file lands
        //    in the current working directory (or a subdirectory matching
        //    the proxy's package).
        // ================================================================
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return "Hello, " + args[0];
            }
        };

        StringOp javaProxy = (StringOp) Proxy.newProxyInstance(
                StringOp.class.getClassLoader(),
                new Class<?>[]{StringOp.class},
                handler);

        System.out.println("JavaProxy class: " + javaProxy.getClass().getName());
        System.out.println("JavaProxy classloader: " + javaProxy.getClass().getClassLoader());
        System.out.println("JavaProxy result: " + javaProxy.call("World"));

        // Find and copy the JDK-dumped proxy class (saved by system property)
        // JDK 8: saves to com/sun/proxy/$Proxy0.class in CWD
        // JDK 9+: saves to jdk/proxy1/$Proxy1.class or similar in CWD
        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        System.out.println("Looking for JDK proxy dump in: " + cwd);

        // Try common locations
        String[] searchDirs = {"com/sun/proxy", "jdk/proxy1", "jdk/proxy2",
                                "com/sun/proxy/$Proxy0.class", "jdk"};
        for (String dir : searchDirs) {
            Path search = cwd.resolve(dir);
            if (Files.exists(search)) {
                System.out.println("Found: " + search);
            }
        }

        // Walk the CWD for any .class files created recently (the generated proxy)
        System.out.println("Searching for generated proxy class files...");
        try (Stream<Path> walk = Files.walk(cwd, 3)) {
            walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                .filter(p -> p.toString().contains("Proxy") || p.toString().contains("proxy")
                          || p.toString().contains("jdk"))
                .forEach(p -> {
                    System.out.println("  Found .class: " + p);
                    try {
                        Path dest = proxyDir.resolve(p.getFileName());
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("    Copied to: " + dest);
                    } catch (IOException e) {
                        // skip
                    }
                });
        }

        // ================================================================
        // 2. CGLib (class-based) — dump via DebuggingClassWriter
        // ================================================================
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY,
                proxyDir.toAbsolutePath().toString());

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(Greeter.class);
        enhancer.setCallback(new MethodInterceptor() {
            public Object intercept(Object obj, Method method, Object[] args,
                                    net.sf.cglib.proxy.MethodProxy proxy) throws Throwable {
                return proxy.invokeSuper(obj, args);
            }
        });
        Greeter cglibProxy = (Greeter) enhancer.create();

        System.out.println("\nCGLib proxy class: " + cglibProxy.getClass().getName());
        System.out.println("CGLib result: " + cglibProxy.hello("World"));
        System.out.println("CGLib add(3,4): " + cglibProxy.add(3, 4));

        // ================================================================
        // List all dumped files
        // ================================================================
        System.out.println("\n=== Files in " + proxyDir + " ===");
        try (Stream<Path> walk = Files.walk(proxyDir, 5)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> System.out.println("  " + proxyDir.relativize(p)));
        }
    }
}
