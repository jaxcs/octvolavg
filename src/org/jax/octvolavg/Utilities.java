package org.jax.octvolavg;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.TiffEncoder;
import ij.plugin.ZProjector;
import ij.plugin.filter.AVI_Writer;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;

public class Utilities {
    
    public static final Pattern  TIFF_IMAGE_PATTERN = Pattern.compile(
            "^(.+_O[DS])_V_.+.TIFF?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    public static final Pattern OCT_IMAGE_PATTERN = Pattern.compile(
            "^(.+_O[DS])_V_.+.OCT$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    
    public static final double EPSILON = 1e-12;
    public static boolean nearZero(double x) {
        return x <= EPSILON && x >= -EPSILON;
    }
    
    public static void safeRenderAsAVI(ImagePlus img, File aviOutFile, DocumentLogger docLogger) throws IOException {
        if(aviOutFile.exists()) {
            docLogger.println(
                    aviOutFile.getAbsolutePath() +
                    " already exists. Refusing to overwrite.");
        } else {
            File parentDir = aviOutFile.getParentFile();
            if(parentDir != null) {
                parentDir.mkdirs();
            }
            AVI_Writer aviWriter = new AVI_Writer();
            aviWriter.writeImage(
                    img,
                    aviOutFile.getPath(),
                    AVI_Writer.PNG_COMPRESSION,
                    //AVI_Writer.NO_COMPRESSION,
                    0);
        }
    }
    
    public static void safeRenderAsTIFF(ImagePlus img, File tiffOutFile, DocumentLogger docLogger) throws IOException {
        if(tiffOutFile.exists()) {
            docLogger.println(
                    tiffOutFile.getAbsolutePath() +
                    " already exists. Refusing to overwrite.");
        } else {
            File parentDir = tiffOutFile.getParentFile();
            if(parentDir != null) {
                parentDir.mkdirs();
            }
            TiffEncoder te = new TiffEncoder(img.getFileInfo());
            FileOutputStream fos = new FileOutputStream(tiffOutFile);
            te.write(fos);
            fos.close();
        }
    }
    
    public static String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if(lastDot == -1) {
            return fileName;
        } else {
            return fileName.substring(0, lastDot);
        }
    }
    
    public static ImagePlus zProjectMean(ArrayList<ImageProcessor> ips, int sizeX, int sizeY) {
        float[][] floatVals = new float[sizeX][sizeY];
        int[][] sumCounts = new int[sizeX][sizeY];
        int sizeZ = ips.size();

        // sum all then divide
        for(int z = 0; z < sizeZ; z++) {
            ImageProcessor currIP = ips.get(z);
            for(int x = 0; x < sizeX; x++) {
                for(int y = 0; y < sizeY; y++) {
                    float currVal = currIP.getf(x, y);
                    if(currVal != 0.0f) {
                        floatVals[x][y] += currVal;
                        sumCounts[x][y]++;
                    }
                }
            }
        }
        for(int x = 0; x < sizeX; x++) {
            for(int y = 0; y < sizeY; y++) {
                floatVals[x][y] /= sumCounts[x][y];
            }
        }
        FloatProcessor projIP = new FloatProcessor(floatVals);

        return new ImagePlus("", projIP);
    }
    
    public static ImagePlus projectBrightestInterval(int windowSizePx, ImagePlus img) {
        int index = maxIntensityIndex(windowSizePx, img);
        ImagePlus enFaceImg = Utilities.toEnFace(img, true);
        int sizeX = enFaceImg.getWidth();
        int sizeY = enFaceImg.getHeight();
        
        ImageStack enFaceStack = enFaceImg.getImageStack();
        float[][] maxIntens = new float[sizeX][sizeY];
        for(int z = index; z < index + windowSizePx; z++) {
            float[][] currIntens = enFaceStack.getProcessor(z + 1).getFloatArray();
            for(int x = 0; x < sizeX; x++) {
                for(int y = 0; y < sizeY; y++) {
                    if(currIntens[x][y] > maxIntens[x][y]) {
                        maxIntens[x][y] = currIntens[x][y];
                    }
                }
            }
        }
        
        return new ImagePlus("", new FloatProcessor(maxIntens));
    }
    
    public static int maxIntensityIndex(int windowHightPx, ImagePlus img) {
        float[] intenSums = widthIntensitySums(windowHightPx, img);
        float maxIntens = intenSums[0];
        int maxIndex = 0;
        
        for(int i = 1; i < intenSums.length; i++) {
            if(intenSums[i] > maxIntens) {
                maxIndex = i;
                maxIntens = intenSums[i];
            }
        }
        
        return maxIndex;
    }

    public static float[] widthIntensitySums(int windowHightPx, ImagePlus img) {
        // Z project the image to get the average of all stacks
        ZProjector zProj = new ZProjector(img);
        zProj.setMethod(ZProjector.AVG_METHOD);
        zProj.setStartSlice(1);
        zProj.setStopSlice(img.getImageStackSize());
        zProj.doProjection();
        float[][] maxProj = zProj.getProjection().getProcessor().getFloatArray();
        
        int sizeX = img.getWidth();
        int sizeY = img.getHeight();
        
        // now get the avg of all of the image rows
        float[] yAvgs = new float[sizeY];
        for(int y = 0; y < sizeY; y++) {
            for(int x = 0; x < sizeX; x++) {
                yAvgs[y] += maxProj[x][y];
            }
            yAvgs[y] /= sizeX;
        }
        
        // now we use a window
        float[] windowedAvgs = new float[1 + sizeY - windowHightPx];
        
        for(int yStart = 0; yStart < windowedAvgs.length; yStart++) {
            for(int y = yStart; y < yStart + windowHightPx; y++) {
                windowedAvgs[yStart] += yAvgs[y];
            }
            windowedAvgs[yStart] /= windowHightPx;
        }
        
        return windowedAvgs;
    }
    
    /**
     * Rotate and scale the given stack in order to produce the "en face" view
     * @param img the image to transform
     * @param invertImageStack indicates if the image stack should be inverted
     * @return the en face image
     */
    public static ImagePlus toEnFace(ImagePlus img, boolean invertImageStack) {

        ImageStack is = img.getImageStack();
        
        int sizeX = is.getWidth();
        int sizeY = is.getHeight();
        int sizeZ = is.getSize();
        
        // read intens
        float[][][] imgIntensZXY = new float[sizeZ][][];
        for(int z = 0; z < sizeZ; z++) {
            ImageProcessor imgProc = is.getProcessor(z + 1);
            imgIntensZXY[z] = new float[sizeX][];
            for(int x = 0; x < sizeX; x++) {
                imgIntensZXY[z][x] = new float[sizeY];
                for(int y = 0; y < sizeY; y++) {
                    imgIntensZXY[z][x][y] = imgProc.getf(x, y);
                }
            }
        }
        
        // do the rotation
        int sizeMaxXZ = Math.max(sizeX, sizeZ);
        ImageStack rotStack = new ImageStack(sizeMaxXZ, sizeMaxXZ);
        if(invertImageStack) {
            for(int y = 0; y < sizeY; y++) {
                rotStack.addSlice("" + y, rotateSlice(y, imgIntensZXY, sizeX, sizeY, sizeZ));
            }
        } else {
            for(int y = sizeY - 1; y >= 0; y--) {
                rotStack.addSlice("" + y, rotateSlice(y, imgIntensZXY, sizeX, sizeY, sizeZ));
            }
        }
        
        img = IJ.createImage(
                "rotImg",
                "32-bit",
                rotStack.getWidth(),
                rotStack.getHeight(),
                0);
        img.setStack(rotStack);
        
        return img;
    }
    
    private static ImageProcessor rotateSlice(
            int y,
            float[][][] imgIntensZXY,
            int sizeX, int sizeY, int sizeZ) {
        
        int sizeMaxXZ = Math.max(sizeX, sizeZ);
        float[][] currSlice = new float[sizeX][sizeZ];
        for(int x = 0; x < sizeX; x++) {
            for(int z = 0; z < sizeZ; z++) {
                currSlice[x][z] = imgIntensZXY[(sizeZ - z) - 1][x][y];
            }
        }
        
        // scale
        ImageProcessor fp = new FloatProcessor(currSlice);
        fp.setInterpolationMethod(ImageProcessor.BILINEAR);
        fp = fp.resize(sizeMaxXZ, sizeMaxXZ, true);
        
        return fp;
    }
    
    public static void writeObjectTo(Object o, File f) throws IOException {
        if(o == null) {
            throw new NullPointerException("internal error: trying to write a null object");
        }
        
        ObjectOutputStream oos =
                new ObjectOutputStream(
                        new BufferedOutputStream(new FileOutputStream(f)));
        oos.writeObject(o);
        oos.close();
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T readObjectFrom(File f) throws IOException, ClassNotFoundException {
        ObjectInputStream ois =
                new ObjectInputStream(
                        new BufferedInputStream(new FileInputStream(f)));
        T t = (T)ois.readObject();
        ois.close();
        return t;
    }
    
    public static boolean isValidDir(String dirName, boolean allowMissing) {
        if(dirName == null || dirName.trim().isEmpty()) {
            return false;
        }
        
        dirName = dirName.trim();
        File dir = new File(dirName);
        
        return dir.isDirectory() || (allowMissing && !dir.exists());
    }
    
    /**
     * Prompt the user to select a directory (or file). This function should
     * only be called from the AWT thread
     * @param title the dialog title
     * @param initDir the initial directory
     * @param allowFiles should we allow non-directory files
     * @param parent the parent component to use for the JFileChooser
     * @return the selected file or null if there is no valid selection
     */
    public static File getDir(String title, String initDir, boolean allowFiles, Component parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(
                allowFiles ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        
        if(initDir != null && !initDir.isEmpty()) {
            File initDirFile = new File(initDir);
            if(initDirFile.isDirectory() || (allowFiles && initDirFile.isFile())) {
                fileChooser.setSelectedFile(initDirFile);
            }
        }
        
        int response = fileChooser.showOpenDialog(parent);
        if(response == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        } else {
            return null;
        }
    }
    
    public static boolean isOctReaderInClasspath() {
        try {
            // we're counting on this to throw an exception if
            // the OCT reader is not present
            Class.forName("com.bioptigen.OCT_Reader", false, Utilities.class.getClassLoader());
            return true;
        } catch(ClassNotFoundException ex) {
            return false;
        }
    }
    
    public static Map<String, List<File>> imgGroupsIn(File inDir, Pattern p) {
        Map<String, List<File>> groups = new LinkedHashMap<String, List<File>>();
        imgGroupsIn(inDir, groups, p);
        
        return groups;
    }
    private static void imgGroupsIn(File inDir, Map<String, List<File>> groupMap, Pattern p) {
        for(File f : inDir.listFiles()) {
            if(f.isDirectory()) {
                imgGroupsIn(f, groupMap, p);
            } else {
                Matcher matcher = p.matcher(f.getName());
                if(matcher.matches()) {
                    String groupID = matcher.group(1);
                    List<File> groupFiles = groupMap.get(groupID);
                    if(groupFiles == null) {
                        groupFiles = new ArrayList<File>();
                        groupMap.put(groupID, groupFiles);
                    }
                    groupFiles.add(f);
                }
            }
        }
    }
}
