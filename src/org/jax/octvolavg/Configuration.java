package org.jax.octvolavg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Configuration {
    
    private static final String PROPS_FILE_NAME = "octvolavg.properties";
    
    private final Properties props;
    private volatile boolean unsavedChanges = false;
    
    public Configuration() throws IOException {
        InputStream resourcePropsIn = Configuration.class.getResourceAsStream(PROPS_FILE_NAME);
        Properties resourceProps = new Properties();
        resourceProps.load(resourcePropsIn);
        
        if(getPropertiesFile().isFile()) {
            this.props = new Properties(resourceProps);
            FileInputStream propsIn = new FileInputStream(getPropertiesFile());
            this.props.load(propsIn);
            propsIn.close();
        } else {
            this.props = resourceProps;
        }
    }
    
    private static File getPropertiesFile() {
        File userHome = new File(System.getProperty("user.home"));
        return new File(userHome, "." + PROPS_FILE_NAME);
    }
    
    /*
     * UI options below
     */
    
    public String getInputDir() {
        return this.props.getProperty("inputDir");
    }
    
    public void setInputDir(String inDir) {
        this.props.setProperty("inputDir", inDir);
        this.unsavedChanges = true;
    }
    
    public String getOutputDir() {
        return this.props.getProperty("outputDir");
    }
    
    public void setOutputDir(String inDir) {
        this.props.setProperty("outputDir", inDir);
        this.unsavedChanges = true;
    }
    
    public boolean getImageStackInverted() {
        return this.getBoolNamed("imageStackInverted");
    }
    
    public void setImageStackInverted(boolean inverted) {
        this.setBoolNamed("imageStackInverted", inverted);
        this.unsavedChanges = true;
    }
    
    public boolean getKeepIntermediate() {
        return this.getBoolNamed("keepIntermediate");
    }
    
    public void setKeepIntermediate(boolean keepIntermediate) {
        this.setBoolNamed("keepIntermediate", keepIntermediate);
        this.unsavedChanges = true;
    }
    
    public int getCropFromTopPixels() {
        return Integer.parseInt(this.props.getProperty("cropFromTopPixels"));
    }
    
    public void setCropFromTopPixels(int cropFromTopPixels) {
        this.props.setProperty("cropFromTopPixels", Integer.toString(cropFromTopPixels));
        this.unsavedChanges = true;
    }
    
    public int getCropFromBottomPixels() {
        return Integer.parseInt(this.props.getProperty("cropFromBottomPixels"));
    }
    
    public void setCropFromBottomPixels(int cropFromBottomPixels) {
        this.props.setProperty("cropFromBottomPixels", Integer.toString(cropFromBottomPixels));
        this.unsavedChanges = true;
    }
    
    public boolean getUseDirectoryForInput() {
        return this.getBoolNamed("useDirectoryForInput");
    }
    
    public void setUseDirectoryForInput(boolean b) {
        this.setBoolNamed("useDirectoryForInput", b);
        this.unsavedChanges = true;
    }
    
    public boolean getUseDirectoryForOutput() {
        return this.getBoolNamed("useDirectoryForOutput");
    }
    
    public void setUseDirectoryForOutput(boolean b) {
        this.setBoolNamed("useDirectoryForOutput", b);
        this.unsavedChanges = true;
    }
    
    private boolean getBoolNamed(String name) {
        String strVal = this.props.getProperty(name);
        return strVal != null && strVal.trim().toLowerCase().equals("true");
    }
    
    private void setBoolNamed(String name, boolean val) {
        this.props.setProperty(name, Boolean.toString(val));
    }
    
    public void saveChanges() throws IOException {
        if(this.unsavedChanges) {
            FileOutputStream propsOut = new FileOutputStream(getPropertiesFile());
            this.props.store(propsOut, "");
            propsOut.close();
            this.unsavedChanges = false;
        }
    }
}
