package org.jax.octvolavg;

import ij.ImagePlus;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import com.bioptigen.OCT_Reader;

/**
 * @author <A HREF="mailto:keith.sheppard@jax.org">Keith Sheppard</A>
 */
public class LazyOCTReader implements Iterator<ImagePlus> {

    private final OCT_Reader octReader;
    private final Iterator<File> fileIter;
    private final DocumentLogger docLogger;
    private final boolean use8Bit;
    
    /**
     * Construct a lazy reader from a file iterator
     * @param fileIter  the file iterator to use
     * @param docLogger the document logger
     * @param use8Bit   should we read OCT as an 8-bit image (otherwise use 16 bit)
     */
    public LazyOCTReader(Iterator<File> fileIter, DocumentLogger docLogger, boolean use8Bit) {
        this.octReader = new OCT_Reader();
        this.fileIter = fileIter;
        this.docLogger = docLogger;
        this.use8Bit = use8Bit;
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
        File inputOCT = this.fileIter.next();
        this.docLogger.println("Reading " + inputOCT.getAbsolutePath());
        try {
            ImagePlus img = this.octReader.loadOctFile(inputOCT.getAbsolutePath(), "", false, this.use8Bit);
            img.setTitle(Utilities.removeExtension(inputOCT.getName()));
            return img;
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
