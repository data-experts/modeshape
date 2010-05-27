/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors. 
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.modeshape.sequencer.zip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.modeshape.graph.JcrLexicon;
import org.modeshape.graph.JcrNtLexicon;
import org.modeshape.graph.ModeShapeLexicon;
import org.modeshape.graph.property.BinaryFactory;
import org.modeshape.graph.property.DateTimeFactory;
import org.modeshape.graph.property.NameFactory;
import org.modeshape.graph.property.Path;
import org.modeshape.graph.property.PathFactory;
import org.modeshape.graph.sequencer.SequencerOutput;
import org.modeshape.graph.sequencer.StreamSequencer;
import org.modeshape.graph.sequencer.StreamSequencerContext;

/**
 * A sequencer that processes and extract metadata from ZIP files.
 */
public class ZipSequencer implements StreamSequencer {

    /**
     * {@inheritDoc}
     * 
     * @see org.modeshape.graph.sequencer.StreamSequencer#sequence(java.io.InputStream,
     *      org.modeshape.graph.sequencer.SequencerOutput, org.modeshape.graph.sequencer.StreamSequencerContext)
     */
    public void sequence( InputStream stream,
                          SequencerOutput output,
                          StreamSequencerContext context ) {
        BinaryFactory binaryFactory = context.getValueFactories().getBinaryFactory();
        DateTimeFactory dateFactory = context.getValueFactories().getDateFactory();
        PathFactory pathFactory = context.getValueFactories().getPathFactory();
        NameFactory nameFactory = context.getValueFactories().getNameFactory();

        try {
            ZipInputStream in = new ZipInputStream(stream);
            ZipEntry entry = in.getNextEntry();
            byte[] buf = new byte[1024];

            // Create top-level node
            Path zipPath = pathFactory.createRelativePath(ZipLexicon.CONTENT);
            output.setProperty(zipPath, JcrLexicon.PRIMARY_TYPE, ZipLexicon.CONTENT);
            while (entry != null) {
                Path entryPath = zipPath;
                String entryName = entry.getName();
                for (String segment : entryName.split(File.separator)) {
                    entryPath = pathFactory.create(entryPath, nameFactory.create(segment));
                }

                if (entry.isDirectory()) { // If entry is directory, create nt:folder node
                    output.setProperty(entryPath, JcrLexicon.PRIMARY_TYPE, JcrNtLexicon.FOLDER);
                } else { // If entry is File, create nt:file
                    output.setProperty(entryPath, JcrLexicon.PRIMARY_TYPE, JcrNtLexicon.FILE);

                    Path contentPath = pathFactory.create(entryPath, JcrLexicon.CONTENT);
                    output.setProperty(contentPath, JcrLexicon.PRIMARY_TYPE, ModeShapeLexicon.RESOURCE);
                    int n;
                    ByteArrayOutputStream baout = new ByteArrayOutputStream();
                    while ((n = in.read(buf, 0, 1024)) > -1) {
                        baout.write(buf, 0, n);
                    }
                    byte[] bytes = baout.toByteArray();
                    output.setProperty(contentPath, JcrLexicon.DATA, binaryFactory.create(bytes));
                    // all other nt:file properties should be generated by other sequencers (mimetype, encoding,...) but we'll
                    // default them here
                    output.setProperty(contentPath, JcrLexicon.ENCODED, "binary");
                    output.setProperty(contentPath, JcrLexicon.LAST_MODIFIED,
                                       dateFactory.create(entry.getTime()).toString());
                    output.setProperty(contentPath, JcrLexicon.MIMETYPE,
                                       "application/octet-stream");

                }
                in.closeEntry();
                entry = in.getNextEntry();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
