/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A set that delegates to another set, but will only instantiate the delegate if necessary.
 *
 * @param <E> the type of elements in the set.
 */
abstract class LazySet<E> extends AbstractSet<E> {
    /** The delegate. */
    @CheckForNull
    private Set<E> delegate;

    /**
     * Instantiates the delegate.
     *
     * @return the delegate.
     */
    @NonNull
    protected abstract Set<E> create();

    /**
     * Gets the delegate.
     *
     * @return the delegate.
     */
    @NonNull
    private synchronized Set<E> delegate() {
        if (delegate == null) {
            delegate = create();
        }
        return delegate;
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return delegate().size();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return delegate().isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(Object o) {
        return delegate().contains(o);
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<E> iterator() {
        return delegate().iterator();
    }

    /** {@inheritDoc} */
    @Override
    public <T> T[] toArray(T[] a) {
        return delegate().toArray(a);
    }

    /** {@inheritDoc} */
    @Override
    public boolean add(E e) {
        return delegate().add(e);
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(Object o) {
        return delegate().remove(o);
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate().containsAll(c);
    }

    /** {@inheritDoc} */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        return delegate().addAll(c);
    }

    /** {@inheritDoc} */
    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate().retainAll(c);
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate().removeAll(c);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        delegate().clear();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        return delegate().equals(o);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return delegate().hashCode();
    }
}
