/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package com.oAT.agent.jacoco;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Internal wrapper for the weird Pack200 Java API to allow usage with streams.
 */
public final class Pack200Streams {

    /**
     * Unpack a stream in Pack200 format into a stream in JAR/ZIP format.
     *
     * @param input
     *            stream in Pack200 format
     * @return stream in JAR/ZIP format
     * @throws IOException
     *             in case of errors with the streams
     */
    @SuppressWarnings("resource")
    public static InputStream unpack(final InputStream input)
            throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final JarOutputStream jar = new JarOutputStream(buffer);
        try {
            final Object unpacker = Class.forName("java.util.jar.Pack200")
                    .getMethod("newUnpacker").invoke(null);
            Class.forName("java.util.jar.Pack200$Unpacker")
                    .getMethod("unpack", InputStream.class,
                            JarOutputStream.class)
                    .invoke(unpacker, new NoCloseInput(input), jar);
        } catch (ClassNotFoundException e) {
            throw new IOException(e.getMessage());
        } catch (InvocationTargetException e) {
            throw new IOException(e.getMessage());
        } catch (NoSuchMethodException e) {
            throw new IOException(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IOException(e.getMessage());
        }
        jar.finish();
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    /**
     * Packs a buffer in JAR/ZIP format into a stream in Pack200 format.
     *
     * @param source
     *            source in JAR/ZIP format
     * @param output
     *            stream in Pack200 format
     * @throws IOException
     *             in case of errors with the streams
     */
    @SuppressWarnings("resource")
    public static void pack(final byte[] source, final OutputStream output)
            throws IOException {
        final JarInputStream jar = new JarInputStream(
                new ByteArrayInputStream(source));
        try {
            final Object packer = Class.forName("java.util.jar.Pack200")
                    .getMethod("newPacker").invoke(null);
            Class.forName("java.util.jar.Pack200$Packer")
                    .getMethod("pack", JarInputStream.class, OutputStream.class)
                    .invoke(packer, jar, output);
        } catch (ClassNotFoundException e) {
            throw newIOException(e);
        } catch (IllegalAccessException e) {
            throw newIOException(e);
        } catch (InvocationTargetException e) {
            throw newIOException(e);
        } catch (NoSuchMethodException e) {
            throw newIOException(e);
        }
    }

    private static IOException newIOException(final Throwable cause) {
        return new IOException(cause);
    }

    private static class NoCloseInput extends FilterInputStream {
        protected NoCloseInput(final InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            // do not close the underlying stream
        }
    }

    private Pack200Streams() {
    }

}
