package org.jax.octvolavg;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainDriver {
    public static final String VERSION = "1.3.6";
    
    private final File outputDir;
    private final int pixelsToCropFromTop;
    private final int pixelsToCropFromBottom;
    private final boolean invertedImageStack;
    private final boolean keepIntermediateTiffs;
    private final DocumentLogger docLogger;
    private final AtomicBoolean isCanceled;

    /**
     * Constructor.
     * @param outputDir result images will be written to this directory
     * @param pixelsToCropFromTop the number of pixels of the image that should be cropped from the top
     * @param pixelsToCropFromBottom the number of pixels of the image that should be cropped from the bottom
     * @param invertedImageStack is the image stack inverted (eg: EDI)
     * @param keepIntermediateTiffs should we keep intermediate TIFF files?
     * @param docLogger our logger
     * @param isCanceled will be set to true upon user cancellation
     */
    public MainDriver(
            File outputDir,
            int pixelsToCropFromTop,
            int pixelsToCropFromBottom,
            boolean invertedImageStack,
            boolean keepIntermediateTiffs,
            DocumentLogger docLogger,
            AtomicBoolean isCanceled) {
        this.outputDir = outputDir;
        this.pixelsToCropFromTop = pixelsToCropFromTop;
        this.pixelsToCropFromBottom = pixelsToCropFromBottom;
        this.invertedImageStack = invertedImageStack;
        this.keepIntermediateTiffs = keepIntermediateTiffs;
        this.docLogger = docLogger;
        this.isCanceled = isCanceled;
    }
    
    private ImageStack preProcessStack(String groupName, ImagePlus img) throws IOException {
        if(this.isCanceled.get()) {
            return null;
        }
        
        ImageStack is = img.getStack();
        
        int sizeX = is.getWidth();
        int sizeY = is.getHeight();
        int sizeZ = is.getSize();
        int sizeYCropped = sizeY - (this.pixelsToCropFromTop + this.pixelsToCropFromBottom);
        
        if(sizeY != sizeYCropped) {
            ImageStack croppedStack = new ImageStack(sizeX, sizeYCropped);
            for(int z = 0; z < sizeZ; z++) {
                if(this.isCanceled.get()) {
                    return null;
                }
                
                ImageProcessor ip = is.getProcessor(z + 1);
                ip.setRoi(new Rectangle(
                        0,
                        this.pixelsToCropFromTop,
                        sizeX,
                        sizeYCropped));
                croppedStack.addSlice("" + z, ip.crop());
            }
            is = croppedStack;
            img = new ImagePlus(img.getTitle(), is);
        }
        
        if(this.keepIntermediateTiffs && this.outputDir != null) {
            if(this.isCanceled.get()) {
                return null;
            }
            
            File outDir = groupName == null ? this.outputDir : new File(this.outputDir, groupName);
            outDir.mkdirs();
            
            String tifFileName = URLEncoder.encode(img.getTitle(), "UTF-8") + ".tif";
            Utilities.safeRenderAsTIFF(
                    img,
                    new File(new File(outDir, "raw-images"), tifFileName),
                    this.docLogger);
        }
        
        return is;
    }
    
    public void turboProcessImages(final String groupName, Iterator<ImagePlus> imgs) throws IOException {
        if(this.isCanceled.get()) {
            return;
        }
        
        try {
            Class.forName("TurboReg_", false, this.getClass().getClassLoader());
        } catch(ClassNotFoundException ex) {
            this.docLogger.println(
                    "Error: failed to load the TurboReg plugin. " +
            		"StackReg_.jar and TurboReg_.jar must be on the " +
            		"classpath for registration to work.");
            return;
        }
        
        try {
            Class.forName("StackReg_", false, this.getClass().getClassLoader());
        } catch(ClassNotFoundException ex) {
            this.docLogger.println(
                    "Error: failed to load the StackReg plugin. " +
                    "StackReg_.jar and TurboReg_.jar must be on the " +
                    "classpath for registration to work.");
            return;
        }
        
        int sizeX = -1;
        int sizeY = -1;
        int sizeZ = -1;
        
        ArrayList<ArrayList<File>> zSlices = new ArrayList<ArrayList<File>>();
        while(imgs.hasNext()) {
            if(this.isCanceled.get()) {
                return;
            }
            
            ImageStack currStack = this.preProcessStack(groupName, imgs.next());
            if(sizeX == -1) {
                sizeX = currStack.getWidth();
                sizeY = currStack.getHeight();
                sizeZ = currStack.getSize();
                for(int z = 0; z < sizeZ; z++) {
                    zSlices.add(new ArrayList<File>());
                }
            } else if(sizeX != currStack.getWidth()) {
                throw new IOException(
                        "the current stack width of " + currStack.getWidth() +
                        " doesn't match the previous of " + sizeX);
            } else if(sizeY != currStack.getHeight()) {
                throw new IOException(
                        "the current stack height of " + currStack.getHeight() +
                        " doesn't match the previous of " + sizeY);
            } else if(sizeZ != currStack.getSize()) {
                throw new IOException(
                        "the current stack size of " + currStack.getSize() +
                        " doesn't match the previous of " + sizeZ);
            }
            
            for(int z = 0; z < sizeZ; z++) {
                if(this.isCanceled.get()) {
                    return;
                }
                ImageProcessor ip = currStack.getProcessor(z + 1);
                File f = File.createTempFile("slice", ".tif");
                new FileSaver(new ImagePlus("", ip)).saveAsTiff(f.getAbsolutePath());
                zSlices.get(z).add(f);
            }
        }
        
        ImageStack regAvgStack = new ImageStack(sizeX, sizeY);
        for(int z = 0; z < sizeZ; z++) {
            if(this.isCanceled.get()) {
                return;
            }
            
            this.docLogger.println("registering slices at frame " + (z + 1));
            
            ArrayList<File> currSliceList = zSlices.get(z);
            ArrayList<ImageProcessor> ips = new ArrayList<ImageProcessor>(currSliceList.size());
            File fstImgFile = null;
            for(File f : currSliceList) {
                if(this.isCanceled.get()) {
                    return;
                }
                if(fstImgFile == null) {
                    fstImgFile = f;
                }
                ips.add(this.turboAlignSliceRigid(sizeX, sizeY, f, fstImgFile).getProcessor());
//                ips.add(this.turboAlignSliceAffine(sizeX, sizeY, f, fstImgFile).getProcessor());
            }
            
            if(fstImgFile != null) {
                fstImgFile.delete();
                ImagePlus proj = Utilities.zProjectMean(ips, sizeX, sizeY);
                regAvgStack.addSlice("" + z, proj.getProcessor());
            }
            
            for(File f : currSliceList) {
                f.delete();
            }
        }
        
        ImagePlus regAvgImg = new ImagePlus("", regAvgStack);
        
        // do the stack alignment
        if(this.isCanceled.get()) {
            return;
        }
        this.docLogger.println("registering image stack");
        regAvgImg.setSliceWithoutUpdate(regAvgImg.getStackSize() / 2);
        this.stackRegRigid(regAvgImg);
//        this.stackRegAffine(regAvgImg);
        
        // scale and rotate
        if(this.isCanceled.get()) {
            return;
        }
        this.docLogger.println("converting image to enface");
        ImagePlus rotImg = Utilities.toEnFace(regAvgImg, this.invertedImageStack);
        
        if(this.isCanceled.get()) {
            return;
        }
        this.docLogger.println("saving image stacks");
        String namePrefix = groupName == null ? "" : (groupName + "_");
        regAvgImg = to8BitDepth(regAvgImg);
        regAvgImg.setTitle(namePrefix + "regAvgImg");
        rotImg = to8BitDepth(rotImg);
        rotImg.setTitle(namePrefix + "rotatedRegAvgImg");
        
        if(this.outputDir != null) {
            File outDir = groupName == null ? this.outputDir : new File(this.outputDir, groupName);
            outDir.mkdirs();
            File regAvgImgFile = new File(outDir, namePrefix + "regAvgImg.tif");
            File rotImgFile = new File(outDir, namePrefix + "rotatedRegAvgImg.tif");
            
            Utilities.safeRenderAsTIFF(regAvgImg, regAvgImgFile, this.docLogger);
            if(this.isCanceled.get()) {
                return;
            }
            Utilities.safeRenderAsTIFF(rotImg, rotImgFile, this.docLogger);
        } else {
            regAvgImg.show();
            rotImg.show();
        }
    }

    /**
     * Convert the given image to 8-bit depth while trying to make the most of
     * the available color range.
     * @param img   the image to convert
     * @return  the 8-bit result
     */
    private ImagePlus to8BitDepth(ImagePlus img) {
        ImageStack is = img.getImageStack();
        
        int sizeX = is.getWidth();
        int sizeY = is.getHeight();
        int sizeZ = is.getSize();
        
        // we need to find the min and max values so that we can fill the
        // available intensity range
        float maxVal = 0.0f;
        float minVal = Float.MAX_VALUE;
        for(int z = 0; z < sizeZ; z++) {
            for(float[] fs : is.getProcessor(z + 1).getFloatArray()) {
                for(float f: fs) {
                    if(f < minVal && f > 0.0f) {
                        minVal = f;
                    } else if(f > maxVal) {
                        maxVal = f;
                    }
                }
            }
        }
        float multiplier = 0xFF / (maxVal - minVal);
        
        // now that we have the intensity range we can use it to convert
        // values into our 8-bit range
        ImageStack newIS = new ImageStack(sizeX, sizeY);
        for(int z = 0; z < sizeZ; z++) {
            ImageProcessor ip = is.getProcessor(z + 1);
            ByteProcessor bp = new ByteProcessor(sizeX, sizeY);
            
            for(int x = 0; x < sizeX; x++) {
                for(int y = 0; y < sizeY; y++) {
                    float currVal = ip.getf(x, y);
                    if(currVal == 0.0f) {
                        bp.set(x, y, 0);
                    } else {
                        bp.set(x, y, Math.round((currVal - minVal) * multiplier));
                    }
                }
            }
            
            newIS.addSlice(z + "", bp);
        }
        
        return new ImagePlus("", newIS);
    }

    @SuppressWarnings("unchecked")
    private void stackRegRigid(ImagePlus img) {
        img.setSlice(img.getStackSize() / 2);
        WindowManager.setTempCurrentImage(img);
        if(!Menus.getCommands().containsKey("StackReg")) {
            Menus.getCommands().put("StackReg", "StackReg_");
        }
        IJ.run("StackReg", "transformation=[Translation]");
        WindowManager.setTempCurrentImage(null);
    }
    
    @SuppressWarnings("unchecked")
    private void stackRegAffine(ImagePlus img) {
        img.setSlice(img.getStackSize() / 2);
        WindowManager.setTempCurrentImage(img);
        if(!Menus.getCommands().containsKey("StackReg")) {
            Menus.getCommands().put("StackReg", "StackReg_");
        }
        IJ.run("StackReg", "transformation=Affine");
        WindowManager.setTempCurrentImage(null);
    }
    
    private static final double GOLDEN_RATIO = 0.5 * (Math.sqrt(5.0) - 1.0);
    
    /**
     * Uses TurboReg rigid algorithm to align the given source TIFF against the
     * target, and returns the resulting image
     * @param source    the source TIFF
     * @param target    the target TIFF
     * @return  the resulting TurboReg aligned image
     */
    private ImagePlus turboAlignSliceRigid(int width, int height, File source, File target) {
        // build up the registration command that we will use as documented
        // here: http://bigwww.epfl.ch/thevenaz/turboreg/
        StringBuilder optionsStr = new StringBuilder();
        optionsStr.append("-align -file ");
        optionsStr.append(source.getAbsolutePath());
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(width - 1);
        optionsStr.append(' ');
        optionsStr.append(height - 1);
        optionsStr.append(' ');
        
        optionsStr.append("-file ");
        optionsStr.append(target.getAbsolutePath());
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(width - 1);
        optionsStr.append(' ');
        optionsStr.append(height - 1);
        optionsStr.append(' ');
        
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int grVal = (int)(0.25 * GOLDEN_RATIO * height);
        
        optionsStr.append("-rigidBody ");
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(halfHeight);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(halfHeight);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(grVal);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(grVal);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(height - grVal);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(height - grVal);
        
        optionsStr.append(' ');
        
        optionsStr.append("-hideOutput");
        Object turboReg = IJ.runPlugIn("TurboReg_", optionsStr.toString());
        
        // use introspection on the plugin result to pull out the image. In
        // doing so we wrap all exceptions as a runtime exception because the
        // caller shouldn't have to catch these
        try {
            Method method;
            
            method = turboReg.getClass().getMethod("getTransformedImage", (Class[])null);
            ImagePlus imgResult = (ImagePlus)method.invoke(turboReg);
            imgResult.getStack().deleteLastSlice();
            
            return imgResult;
        } catch(SecurityException ex) {
            throw new RuntimeException(ex);
        } catch(NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        } catch(IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch(IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch(InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Uses TurboReg affine algorithm to align the given source TIFF against the
     * target, and returns the resulting image
     * @param source    the source TIFF
     * @param target    the target TIFF
     * @return  the resulting TurboReg aligned image
     */
    private ImagePlus turboAlignSliceAffine(int width, int height, File source, File target) {
        // build up the registration command that we will use as documented
        // here: http://bigwww.epfl.ch/thevenaz/turboreg/
        StringBuilder optionsStr = new StringBuilder();
        optionsStr.append("-align -file ");
        optionsStr.append(source.getAbsolutePath());
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(width - 1);
        optionsStr.append(' ');
        optionsStr.append(height - 1);
        optionsStr.append(' ');
        
        optionsStr.append("-file ");
        optionsStr.append(target.getAbsolutePath());
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(0);
        optionsStr.append(' ');
        optionsStr.append(width - 1);
        optionsStr.append(' ');
        optionsStr.append(height - 1);
        optionsStr.append(' ');
        
        int halfWidth = width / 2;
        int grHeightVal = (int)(0.25 * GOLDEN_RATIO * height);
        int grWidthVal = (int)(0.25 * GOLDEN_RATIO * height);
        
        optionsStr.append("-affine ");
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(grHeightVal);
        
        optionsStr.append(' ');
        optionsStr.append(halfWidth);
        optionsStr.append(' ');
        optionsStr.append(grHeightVal);
        
        optionsStr.append(' ');
        optionsStr.append(grWidthVal);
        optionsStr.append(' ');
        optionsStr.append(height - grHeightVal);
        
        optionsStr.append(' ');
        optionsStr.append(grWidthVal);
        optionsStr.append(' ');
        optionsStr.append(height - grHeightVal);
        
        optionsStr.append(' ');
        optionsStr.append(width - grWidthVal);
        optionsStr.append(' ');
        optionsStr.append(height - grHeightVal);
        
        optionsStr.append(' ');
        optionsStr.append(width - grWidthVal);
        optionsStr.append(' ');
        optionsStr.append(height - grHeightVal);
        
        optionsStr.append(' ');
        
        optionsStr.append("-hideOutput");
        Object turboReg = IJ.runPlugIn("TurboReg_", optionsStr.toString());
        
        // use introspection on the plugin result to pull out the image. In
        // doing so we wrap all exceptions as a runtime exception because the
        // caller shouldn't have to catch these
        try {
            Method method;
            
            method = turboReg.getClass().getMethod("getTransformedImage", (Class[])null);
            ImagePlus imgResult = (ImagePlus)method.invoke(turboReg);
            imgResult.getStack().deleteLastSlice();
            
            return imgResult;
        } catch(SecurityException ex) {
            throw new RuntimeException(ex);
        } catch(NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        } catch(IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch(IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch(InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
}
