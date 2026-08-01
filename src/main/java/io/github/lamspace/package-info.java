/**
 * APS (Accelerated Proxy Solution) — a high-performance, MethodHandle-based
 * dynamic proxy library for Java.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 *   MyClass proxy = APS.create(MyClass.class, (obj, method, superHandle, args) -> {
 *       System.out.println("before " + method.getName());
 *       return superHandle.invoke(args);
 *   });
 * }</pre>
 *
 * @see io.github.lamspace.APS
 * @see io.github.lamspace.Callback
 * @see io.github.lamspace.ClassFilter
 */
package io.github.lamspace;
