/**
 * Hidden-class loading support.
 *
 * <p>Uses {@code MethodHandles.Lookup.defineHiddenClass(byte[], boolean)}
 * to load generated proxy bytecode without custom ClassLoaders, avoiding
 * permgen/metaspace leaks.
 */
package io.github.lamspace.loader;
