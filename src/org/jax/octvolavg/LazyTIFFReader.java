package org.jax.octvolavg;

import ij.ImagePlus;
import ij.io.Opener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * @author <A HREF="mailto:keith.sheppard@jax.org">Keith Sheppard</A>
 */
public class LazyTIFFReader implements Iterator<ImagePlus> {

    private final Iterator<File> fileIter;
    private final DocumentLogger docLogger;
    
    /**
     * Construct a lazy reader from a file iterator
     * @param fileIter  the file iterator to use
     * @param docLogger the document logger
     */
    public LazyTIFFReader(Iterator<File> fileIter, DocumentLogger docLogger) {
        this.fileIter = fileIter;
        this.docLogger = docLogger;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNext() {
        return this.fileIter.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    public ImagePlus next() {
        File tiffFileIn = this.fileIter.next();
        this.docLogger.println("Reading " + tiffFileIn.getAbsolutePath());
        try {
            InputStream tiffIn = new BufferedInputStream(new FileInputStream(tiffFileIn));
            Opener opener = new Opener();
            ImagePlus tiffImg = opener.openTiff(tiffIn, Utilities.removeExtension(tiffFileIn.getName()));
            tiffIn.close();
            
            return tiffImg;
        } catch(IOException ex) {
            // sneak the IOException out as a runtime exception
            throw new RuntimeException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
