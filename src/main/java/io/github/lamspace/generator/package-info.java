/**
 * Bytecode generation engine. Uses ASM to generate proxy subclasses at runtime.
 *
 * <p>The two main components are:
 * <ul>
 *   <li>{@link io.github.lamspace.generator.ClassGenerator} — orchestrates
 *       subclass bytecode generation (constructors, fields, methods, clinit)</li>
 *   <li>{@link io.github.lamspace.generator.MethodDispatcher} — generates
 *       per-method override bytecode with Callback delegation and
 *       MethodHandle super-call binding</li>
 * </ul>
 */
package io.github.lamspace.generator;
