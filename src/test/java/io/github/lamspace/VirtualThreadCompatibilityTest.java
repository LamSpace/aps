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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualThreadCompatibilityTest {

    // ---- Test fixtures ----

    interface Greeter {
        String hello(String name);
    }

    static class GreeterImpl {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    // ---- Correctness under virtual-thread concurrency ----

    @Test
    void manyVirtualThreadsInvokeProxyCorrectly() throws Exception {
        // Interface proxy: interceptor computes the reply directly.
        Greeter iface = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> "Hello, " + args[0]);
        runConcurrently(5_000, i -> iface.hello("t" + i), i -> "Hello, t" + i);

        // Class proxy: interceptor routes through the INVOKESPECIAL super path.
        GreeterImpl impl = AcceleratedProxy.proxy(GreeterImpl.class,
                (obj, method, args) ->
                        AcceleratedProxy.invokeSuper(obj, method, args));
        runConcurrently(5_000, i -> impl.hello("t" + i), i -> "Hello, t" + i);
    }

    // ---- Carrier-thread pinning on the hot path ----

    @Test
    void noCarrierPinningOnHotPath() throws Exception {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> "Hello, " + args[0]);

        // Warm the dispatch path so one-time class definition and JIT
        // compilation fall outside the measured steady-state window.
        proxy.hello("warmup");

        IntFunction<String> task = i -> proxy.hello("t" + i);

        // Also warm the task lambda's string-concat invokedynamic: it is
        // lazily linked on first use and would otherwise pin the carrier
        // thread once inside the measured window.
        task.apply(0);

        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned").withStackTrace();
            recording.start();
            try {
                runConcurrently(10_000, task, i -> "Hello, t" + i);
            } finally {
                recording.stop();
            }

            Path dump = Files.createTempFile("openproxy-pinning", ".jfr");
            try {
                recording.dump(dump);
                long pinned = 0;
                try (RecordingFile rf = new RecordingFile(dump)) {
                    while (rf.hasMoreEvents()) {
                        RecordedEvent event = rf.readEvent();
                        if ("jdk.VirtualThreadPinned".equals(
                                event.getEventType().getName())) {
                            pinned++;
                        }
                    }
                }
                assertEquals(0, pinned,
                        "proxy hot path must not pin the carrier thread");
            } finally {
                Files.deleteIfExists(dump);
            }
        }
    }

    // ---- Proxy creation inside a virtual thread ----

    @Test
    void proxyCreationInsideVirtualThreadWorks() throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> {
                Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                        (obj, method, args) -> "Hello, " + args[0]);
                return proxy.hello("vt");
            });
            assertEquals("Hello, vt", future.get(30, TimeUnit.SECONDS));
        }
    }

    // ---- Helpers ----

    private static void runConcurrently(int threads,
                                        IntFunction<String> task,
                                        IntFunction<String> expected)
            throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                final int id = i;
                futures.add(executor.submit(() -> task.apply(id)));
            }
            for (int i = 0; i < threads; i++) {
                assertEquals(expected.apply(i),
                        futures.get(i).get(30, TimeUnit.SECONDS));
            }
        }
    }
}
