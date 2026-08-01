package io.github.lamspace;

/**
 * Internal interface implemented by all generated class proxy subclasses.
 * Provides index-based super-method dispatch.
 *
 * <p>Users should prefer the static helper
 * {@link APS#invokeSuper(Object, int, Object[])} over casting to this
 * interface directly.
 */
public interface SuperDispatcher {

    /**
     * Invokes the superclass method at the given index in the dispatch table.
     *
     * @param index zero-based index of the method in the proxy's dispatch table
     * @param args  boxed arguments
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable from the superclass method
     */
    Object invokeSuper(int index, Object[] args) throws Throwable;
}
