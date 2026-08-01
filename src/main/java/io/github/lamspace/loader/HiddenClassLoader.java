package io.github.lamspace.loader;

import io.github.lamspace.internal.LookupManager;

import java.lang.invoke.MethodHandles;

/**
 * Loads generated proxy class bytecode using
 * {@link MethodHandles.Lookup#defineHiddenClass(byte[], boolean, MethodHandles.Lookup.ClassOption...)}.
 * <p>
 * Hidden classes are not discoverable by name, do not leak via ClassLoader
 * references, and are eligible for GC when no longer referenced — avoiding
 * the permgen/metaspace leaks common with custom ClassLoader approaches.
 */
public class HiddenClassLoader {

    /**
     * Creates a new HiddenClassLoader.
     */
    public HiddenClassLoader() {
    }

    /**
     * Defines a hidden class from the given bytecode, using a Lookup
     * obtained from the target class to place the hidden class in the
     * target class's runtime package.
     *
     * @param targetClass the class being proxied (used to obtain the right Lookup)
     * @param bytecode    valid JVM classfile bytes
     * @return the defined hidden class
     * @throws IllegalAccessException if the Lookup cannot define the class
     */
    public Class<?> defineClass(Class<?> targetClass, byte[] bytecode)
            throws IllegalAccessException {
        MethodHandles.Lookup lookup = LookupManager.getLookup(targetClass);
        MethodHandles.Lookup definedLookup = lookup.defineHiddenClass(bytecode, true);
        return definedLookup.lookupClass();
    }
}
