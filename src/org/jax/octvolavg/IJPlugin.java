package org.jax.octvolavg;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import java.awt.Desktop;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;

import javax.swing.SwingUtilities;

/**
 * @author <A HREF="mailto:keith.sheppard@jax.org">Keith Sheppard</A>
 */
public class IJPlugin implements PlugIn {
    private static final String HELP_ZIP_RESOURCE = "/help.zip";
    private static final String HELP_FILE = "using-octvolavg.html";
    
    /**
     * Mailbox is just for super simple blocking put/take style communication
     * between threads. It seems like there should be a java library class
     * that does this but if there is I couldn't find it.
     */
    private static class Mailbox<T> {
        
        // we need this ref class be cause ArrayBlockingQueue doesn't like nulls
        private class Ref {
            public T value;
            public Ref(T value) {
                this.value = value;
            }
        }
        
        // pretty much all of the hard work is done by the queue
        private ArrayBlockingQueue<Ref> q = new ArrayBlockingQueue<Ref>(1);
        
        /**
         * Put the given value in the mailbox. This will block if there is
         * already something in the mailbox
         * @param value the value to put
         * @throws InterruptedException
         */
        public synchronized void put(T value) throws InterruptedException {
            this.q.put(new Ref(value));
        }
        
        /**
         * Get the value in the mailbox or block if there is none
         * @return  the value in the mailbox
         * @throws InterruptedException
         */
        public T take() throws InterruptedException {
            return this.q.take().value;
        }
        
        /**
         * Puts a null value if the mailbox is empty. This could be useful
         * as a poison pill
         * @throws InterruptedException
         */
        public synchronized void putNullIfEmpty() throws InterruptedException {
            if(this.q.isEmpty()) {
                this.q.put(new Ref(null));
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void run(String arg) {
        try {
            if("run".equals(arg)) {
                IJ.showStatus("performing stack registration and averaging");
                
                final Mailbox<Boolean> okClickedMailbox = new Mailbox<Boolean>();
                final Mailbox<File> outputDirMailbox = new Mailbox<File>();
                final Mailbox<Integer> cropFromTopMailbox = new Mailbox<Integer>();
                final Mailbox<Integer> cropFromBottomMailbox = new Mailbox<Integer>();
                final Mailbox<Boolean> isInvertedMailbox = new Mailbox<Boolean>();
                final Mailbox<Boolean> keepIntermediateMailbox = new Mailbox<Boolean>();
                final Mailbox<Map<String, Iterator<ImagePlus>>> imgIterMapMailbox = new Mailbox<Map<String, Iterator<ImagePlus>>>();
                
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        try {
                            final IJPluginOptions ijPluginOptions = new IJPluginOptions(WindowManager.getFrontWindow(), true);
                            ijPluginOptions.setVisible(true);
                            boolean okClicked = ijPluginOptions.getUserClickedOK();

                            okClickedMailbox.put(okClicked);
                            if(okClicked) {
                                outputDirMailbox.put(ijPluginOptions.getOutputDir());
                                cropFromTopMailbox.put(ijPluginOptions.getCropFromTopPixels());
                                cropFromBottomMailbox.put(ijPluginOptions.getCropFromBottomPixels());
                                isInvertedMailbox.put(ijPluginOptions.isInvertedImageStack());
                                keepIntermediateMailbox.put(ijPluginOptions.getKeepIntermediate());
                                imgIterMapMailbox.put(ijPluginOptions.getImageIteratorMap());
                            }
                        } catch(Throwable ex) {
                            dealWithException(ex);
                        } finally {
                            try {
                                // this is to prevent a deadlock from occurring
                                // in case we fail to do a 'put'
                                okClickedMailbox.putNullIfEmpty();
                                outputDirMailbox.putNullIfEmpty();
                                cropFromTopMailbox.putNullIfEmpty();
                                cropFromBottomMailbox.putNullIfEmpty();
                                isInvertedMailbox.putNullIfEmpty();
                                keepIntermediateMailbox.putNullIfEmpty();
                                imgIterMapMailbox.putNullIfEmpty();
                            } catch(InterruptedException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                });
                
                if(okClickedMailbox.take()) {
                   //meixiao final MainDriver driver = new MainDriver(
                	final MainDriver_ToEnFace driver = new MainDriver_ToEnFace(
                            outputDirMailbox.take(),
                            cropFromTopMailbox.take(),
                            cropFromBottomMailbox.take(),
                            isInvertedMailbox.take(),
                            keepIntermediateMailbox.take(),
                            new DocumentLogger(null),
                            new AtomicBoolean(false));
                    Map<String, Iterator<ImagePlus>> imageIterMap = imgIterMapMailbox.take();
                    for(Map.Entry<String, Iterator<ImagePlus>> entry : imageIterMap.entrySet()) {
                        driver.turboProcessImages(
                                entry.getKey(),
                                entry.getValue());
                    }
                    IJ.showStatus("done registering and averaging image stacks");
                }
                
            } else if("showLicense".equals(arg)) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        LicenseDialog licenseDialog = new LicenseDialog(WindowManager.getFrontWindow(), true);
                        licenseDialog.setVisible(true);
                    }
                });
            } else if("help".equals(arg)) {
                File tmpDir = FileUtilities.createTempDir();
                ZipInputStream zipIn = new ZipInputStream(
                        this.getClass().getResourceAsStream(HELP_ZIP_RESOURCE));
                FileUtilities.unzipToDirectory(zipIn, tmpDir);
                Desktop.getDesktop().open(new File(tmpDir, HELP_FILE));
            }
        } catch(Throwable ex) {
            dealWithException(ex);
        }
    }
    
    private static void dealWithException(Throwable ex) {
        ex.printStackTrace();
        IJ.error("Error occured in OCT Volume Averager plugin", ex.getLocalizedMessage());
    }
}
