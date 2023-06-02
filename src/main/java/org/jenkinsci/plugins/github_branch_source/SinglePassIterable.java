package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.jcip.annotations.GuardedBy;

/**
 * Takes either an {@link Iterable} or an {@link Iterator} and converts it into a {@link Iterable}
 * that will walk the backing {@link Iterator} once only but can be walked repeatedly itself.
 *
 * @param <V>
 */
class SinglePassIterable<V> implements Iterable<V> {
    /** The delegate. */
    @GuardedBy("items")
    @CheckForNull
    private Iterator<V> delegate;
    /** The items we have seen so far. */
    private final List<V> items;

    /**
     * Constructor.
     *
     * @param delegate the {@link Iterable}.
     */
    public SinglePassIterable(@NonNull Iterable<V> delegate) {
        this(delegate.iterator());
    }

    /**
     * Constructor.
     *
     * @param delegate the {@link Iterator}.
     */
    public SinglePassIterable(@NonNull Iterator<V> delegate) {
        this.delegate = delegate;
        items = new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public final Iterator<V> iterator() {
        synchronized (items) {
            if (delegate == null || !delegate.hasNext()) {
                // we have walked the iterator once, so now items is complete
                return Collections.unmodifiableList(items).iterator();
            }
        }
        return new Iterator<V>() {
            int index = 0;

            /** {@inheritDoc} */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            /** {@inheritDoc} */
            @Override
            public boolean hasNext() {
                synchronized (items) {
                    if (index < items.size()) {
                        return true;
                    }
                    if (delegate != null) {
                        if (delegate.hasNext()) {
                            return true;
                        }
                        delegate = null;
                        completed();
                    }
                    return false;
                }
            }

            /** {@inheritDoc} */
            @Override
            public V next() {
                synchronized (items) {
                    if (index < items.size()) {
                        return items.get(index++);
                    }
                    try {
                        if (delegate != null && delegate.hasNext()) {
                            V element = delegate.next();
                            observe(element);
                            items.add(element);
                            // Index needs to be incremented
                            index++;
                            return element;
                        } else {
                            throw new NoSuchElementException();
                        }
                    } catch (NoSuchElementException e) {
                        if (delegate != null) {
                            delegate = null;
                            completed();
                        }
                        throw e;
                    }
                }
            }
        };
    }

    /**
     * Callback for each element observed from the delegate.
     *
     * @param v the element.
     */
    protected void observe(V v) {}

    /** Callback for when the delegate has reached the end. */
    protected void completed() {}
}
