package org.jax.octvolavg;

import ij.ImagePlus;
import ij.WindowManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

public class IJPluginOptions extends javax.swing.JDialog
{
    private static final long serialVersionUID = -621824132724328069L;
    private volatile boolean userClickedOK = false;
    private Configuration conf;
    private volatile boolean uiInitComplete = false;
    
    public IJPluginOptions(java.awt.Frame parent, boolean modal) throws IOException
    {
        super(parent, modal);
        this.initComponents();
        this.setTitle("OCT Volume Averager (" + MainDriver.VERSION + ")");
        this.conf = new Configuration();
        this.initUIFromConfig();
    }
    
    /**
     * Must be called from the AWT thread
     */
    private void initUIFromConfig() {
        this.inDirText.setText(this.conf.getInputDir());
        this.outDirText.setText(this.conf.getOutputDir());
        this.invertedImageStackCheckBox.setSelected(this.conf.getImageStackInverted());
        this.keepIntermediateCheckbox.setSelected(this.conf.getKeepIntermediate());
        this.pixelsClipFromTopText.setValue(this.conf.getCropFromTopPixels());
        this.pixelsClipFromBottomText.setValue(this.conf.getCropFromBottomPixels());
        
        boolean inFromDir = this.conf.getUseDirectoryForInput();
        this.inputReadTIFFRadioButton.setSelected(inFromDir);
        this.inputUseOpenRadioButton.setSelected(!inFromDir);
        
        boolean outToDir = this.conf.getUseDirectoryForOutput();
        this.outputSaveTIFFRadioButton.setSelected(outToDir);
        this.outputOpenRadioButton.setSelected(!outToDir);
        
        this.uiInitComplete = true;
        this.updateUI();
    }
    
    /**
     * Determine if the user has clicked OK. Can be called from any thread
     * @return true if OK was clicked and false if cancel was clicked
     */
    public boolean getUserClickedOK() {
        return this.userClickedOK;
    }
    
    private static File[] listFilesWithExt(final File dir, final String... exts) {
        return dir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                String fNameLower = f.getName().toLowerCase();
                if(!f.isFile()) {
                    return false;
                } else {
                    for(String ext : exts) {
                        if(fNameLower.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        });
    }
    
    public Map<String, Iterator<ImagePlus>> getImageIteratorMap() {
        if(this.inputUseOpenRadioButton.isSelected()) {
            int[] ids = WindowManager.getIDList();
            if(ids.length == 0) {
                // we didn't find any input files so there is nothing to do
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to find any open image stacks",
                        "No Open Image Stacks Found",
                        JOptionPane.WARNING_MESSAGE);
                return Collections.emptyMap();
            }
            
            ArrayList<ImagePlus> images = new ArrayList<ImagePlus>(ids.length);
            for(int id : ids) {
                images.add(WindowManager.getImage(id));
            }
            return Collections.singletonMap(null, images.iterator());
        } else {
            final String inBaseDirStr = this.inDirText.getText().trim();
            File dir = new File(inBaseDirStr);
            
            // first try to find grouped TIFFs
            Map<String, List<File>> imgGrps = Utilities.imgGroupsIn(dir, Utilities.TIFF_IMAGE_PATTERN);
            if(imgGrps.isEmpty()) {
                // failed to get grouped TIFFs so try for grouped OCT
                imgGrps = Utilities.imgGroupsIn(dir, Utilities.OCT_IMAGE_PATTERN);
                if(imgGrps.isEmpty()) {
                    // the groupings failed. Try to get the files ungrouped
                    return Collections.singletonMap(null, this.getUngroupedImageIterator());
                } else {
                    HashMap<String, Iterator<ImagePlus>> imgGroupMap =
                            new HashMap<String, Iterator<ImagePlus>>();
                    for(Map.Entry<String, List<File>> entry: imgGrps.entrySet()) {
                        Iterator<File> fileIter = entry.getValue().iterator();
                        imgGroupMap.put(
                                entry.getKey(),
                                new LazyOCTReader(fileIter, new DocumentLogger(null), false));
                    }
                    return imgGroupMap;
                }
            } else {
                HashMap<String, Iterator<ImagePlus>> imgGroupMap =
                        new HashMap<String, Iterator<ImagePlus>>();
                for(Map.Entry<String, List<File>> entry: imgGrps.entrySet()) {
                    Iterator<File> fileIter = entry.getValue().iterator();
                    imgGroupMap.put(
                            entry.getKey(),
                            new LazyTIFFReader(fileIter, new DocumentLogger(null)));
                }
                return imgGroupMap;
            }
        }
    }
    
    public Iterator<ImagePlus> getUngroupedImageIterator() {
        final String inBaseDirStr = this.inDirText.getText().trim();
        File dir = new File(inBaseDirStr);
        File[] tiffFiles = listFilesWithExt(dir, ".tiff", ".tif");
        
        if(tiffFiles.length >= 2) {
            // if there are any TIFFs we'll use a TIFF reader
            return new LazyTIFFReader(
                    Arrays.asList(tiffFiles).iterator(),
                    new DocumentLogger(null));
        } else {
            // otherwise we should try to use the OCT reader
            if(Utilities.isOctReaderInClasspath()) {
                File[] octFiles = listFilesWithExt(dir, ".oct");
                if(octFiles.length >= 2) {
                    return new LazyOCTReader(
                            Arrays.asList(octFiles).iterator(),
                            new DocumentLogger(null),
                            false);
                } else {
                    // we didn't find any input files so there is nothing to do
                    JOptionPane.showMessageDialog(
                            this,
                            "Failed to find enough *.tif or *.oct input files.",
                            "No Input Files Found",
                            JOptionPane.WARNING_MESSAGE);
                    return Arrays.asList(new ImagePlus[0]).iterator();
                }
            } else {
                // we didn't find any input files so there is nothing to do
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to find enough *.tif input files.",
                        "No Input Files Found",
                        JOptionPane.WARNING_MESSAGE);
                return Arrays.asList(new ImagePlus[0]).iterator();
            }
        }
    }
    
    public File getOutputDir() {
        if(this.outputSaveTIFFRadioButton.isSelected()) {
            final String outBaseDirStr = this.outDirText.getText().trim();
            return new File(outBaseDirStr);
        } else {
            return null;
        }
    }
    
    public boolean isInvertedImageStack() {
        return this.invertedImageStackCheckBox.isSelected();
    }
    
    public boolean getKeepIntermediate() {
        return
                this.keepIntermediateCheckbox.isSelected()
                && this.outputSaveTIFFRadioButton.isSelected();
    }
    
    public int getCropFromTopPixels() {
        return ((Number)this.pixelsClipFromTopText.getValue()).intValue();
    }
    
    public int getCropFromBottomPixels() {
        return ((Number)this.pixelsClipFromBottomText.getValue()).intValue();
    }

    /**
     * Validate the UI settings. This function assumes that we're running in
     * the AWT thread
     * @return  true if the settings are valid
     */
    private boolean validateSettings() {
        if(this.inputReadTIFFRadioButton.isSelected() && !Utilities.isValidDir(this.inDirText.getText(), false)) {
            JOptionPane.showMessageDialog(
                    this,
                    "The input directory must be an existing directory",
                    "Missing Input Directory",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        if(this.outputSaveTIFFRadioButton.isSelected() && !Utilities.isValidDir(this.outDirText.getText(), true)) {
            JOptionPane.showMessageDialog(
                    this,
                    "You must specify an output directory",
                    "Output Directory Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        if(this.inputUseOpenRadioButton.isSelected()) {
            int[] ids = WindowManager.getIDList();
            if(ids == null || ids.length < 2) {
                JOptionPane.showMessageDialog(
                        this,
                        "There must be at least two images for registration to work",
                        "Too Few Images",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            final String inBaseDirStr = this.inDirText.getText().trim();
            File dir = new File(inBaseDirStr);
            File[] tiffFiles = listFilesWithExt(dir, ".tiff", ".tif");
            if(tiffFiles == null || tiffFiles.length < 2) {
                File[] octFiles = listFilesWithExt(dir, ".oct");
                if(octFiles == null || octFiles.length < 2) {
                    JOptionPane.showMessageDialog(
                            this,
                            "There must be at least two images for registration to work",
                            "Too Few Images",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private void updateUI() {
        if(this.uiInitComplete) {
            boolean useInDir = this.inputReadTIFFRadioButton.isSelected();
            this.inDirText.setEnabled(useInDir);
            this.inDirButton.setEnabled(useInDir);

            boolean useOutDir = this.outputSaveTIFFRadioButton.isSelected();
            this.outDirText.setEnabled(useOutDir);
            this.outDirButton.setEnabled(useOutDir);
            this.keepIntermediateCheckbox.setEnabled(useOutDir);
            if(!useOutDir) {
                this.keepIntermediateCheckbox.setSelected(false);
            }
        }
    }
    
    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("all")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.ButtonGroup inputButtonGroup = new javax.swing.ButtonGroup();
        javax.swing.ButtonGroup outputButtonGroup = new javax.swing.ButtonGroup();
        contentPanel = new javax.swing.JPanel();
        inputUseOpenRadioButton = new javax.swing.JRadioButton();
        inputReadTIFFRadioButton = new javax.swing.JRadioButton();
        inDirText = new javax.swing.JTextField();
        inDirButton = new javax.swing.JButton();
        outputOpenRadioButton = new javax.swing.JRadioButton();
        outputSaveTIFFRadioButton = new javax.swing.JRadioButton();
        outDirText = new javax.swing.JTextField();
        outDirButton = new javax.swing.JButton();
        perfTestingPanel = new javax.swing.JPanel();
        invertedImageStackCheckBox = new javax.swing.JCheckBox();
        keepIntermediateCheckbox = new javax.swing.JCheckBox();
        pixelsClipFromTopLabel = new javax.swing.JLabel();
        pixelsClipFromTopText = new javax.swing.JFormattedTextField();
        pixelsClipFromBottomLabel = new javax.swing.JLabel();
        pixelsClipFromBottomText = new javax.swing.JFormattedTextField();
        okCancelPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        inputButtonGroup.add(inputUseOpenRadioButton);
        inputUseOpenRadioButton.setSelected(true);
        inputUseOpenRadioButton.setText("Use Currently Open Stacks as Input");
        inputUseOpenRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                inputUseOpenRadioButtonItemStateChanged(evt);
            }
        });

        inputButtonGroup.add(inputReadTIFFRadioButton);
        inputReadTIFFRadioButton.setText("Read TIFF or OCT Image Stacks from the Following Directory as Input:");
        inputReadTIFFRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                inputReadTIFFRadioButtonItemStateChanged(evt);
            }
        });

        inDirButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/action/browse-16x16.png"))); // NOI18N
        inDirButton.setText("Browse Dirs");
        inDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inDirButtonActionPerformed(evt);
            }
        });

        outputButtonGroup.add(outputOpenRadioButton);
        outputOpenRadioButton.setSelected(true);
        outputOpenRadioButton.setText("Open Resulting Averaged Stacks in ImageJ/Fiji");
        outputOpenRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                outputOpenRadioButtonItemStateChanged(evt);
            }
        });

        outputButtonGroup.add(outputSaveTIFFRadioButton);
        outputSaveTIFFRadioButton.setText("Save Resulting Averaged Stack to the Following Directory:");
        outputSaveTIFFRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                outputSaveTIFFRadioButtonItemStateChanged(evt);
            }
        });

        outDirButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/action/browse-16x16.png"))); // NOI18N
        outDirButton.setText("Browse Dirs");
        outDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                outDirButtonActionPerformed(evt);
            }
        });

        perfTestingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Options"));

        invertedImageStackCheckBox.setText("Inverted Image Stack (EDI)");

        keepIntermediateCheckbox.setText("Keep Intermediate *.tiff Files");

        pixelsClipFromTopLabel.setText("Number of Pixels to Crop from Top:");

        pixelsClipFromTopText.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(java.text.NumberFormat.getIntegerInstance())));
        pixelsClipFromTopText.setText("0");

        pixelsClipFromBottomLabel.setText("Number of Pixels to Crop from Bottom:");

        pixelsClipFromBottomText.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(java.text.NumberFormat.getIntegerInstance())));
        pixelsClipFromBottomText.setText("0");

        javax.swing.GroupLayout perfTestingPanelLayout = new javax.swing.GroupLayout(perfTestingPanel);
        perfTestingPanel.setLayout(perfTestingPanelLayout);
        perfTestingPanelLayout.setHorizontalGroup(
            perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(perfTestingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(invertedImageStackCheckBox)
                    .addGroup(perfTestingPanelLayout.createSequentialGroup()
                        .addComponent(pixelsClipFromTopLabel)
                        .addGap(27, 27, 27)
                        .addComponent(pixelsClipFromTopText, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(perfTestingPanelLayout.createSequentialGroup()
                        .addComponent(pixelsClipFromBottomLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pixelsClipFromBottomText, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(keepIntermediateCheckbox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        perfTestingPanelLayout.setVerticalGroup(
            perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(perfTestingPanelLayout.createSequentialGroup()
                .addComponent(invertedImageStackCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(keepIntermediateCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pixelsClipFromTopLabel)
                    .addComponent(pixelsClipFromTopText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pixelsClipFromBottomLabel)
                    .addComponent(pixelsClipFromBottomText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(24, 24, 24))
        );

        javax.swing.GroupLayout contentPanelLayout = new javax.swing.GroupLayout(contentPanel);
        contentPanel.setLayout(contentPanelLayout);
        contentPanelLayout.setHorizontalGroup(
            contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(contentPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(perfTestingPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(contentPanelLayout.createSequentialGroup()
                        .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(outDirText)
                            .addComponent(inDirText))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inDirButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(outDirButton, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(contentPanelLayout.createSequentialGroup()
                        .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inputUseOpenRadioButton)
                            .addComponent(inputReadTIFFRadioButton)
                            .addComponent(outputOpenRadioButton)
                            .addComponent(outputSaveTIFFRadioButton))
                        .addGap(0, 122, Short.MAX_VALUE)))
                .addContainerGap())
        );
        contentPanelLayout.setVerticalGroup(
            contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, contentPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(inputUseOpenRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(inputReadTIFFRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(inDirButton)
                    .addComponent(inDirText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(outputOpenRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(outputSaveTIFFRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(contentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(outDirText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(outDirButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(perfTestingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(33, 33, 33))
        );

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        okCancelPanel.add(okButton);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        okCancelPanel.add(cancelButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(contentPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(okCancelPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(contentPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 385, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(okCancelPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void inDirButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inDirButtonActionPerformed
        File selectedFile = Utilities.getDir(
                "Select Input Directory",
                this.inDirText.getText().trim(),
                false,
                this);
        if(selectedFile != null){
            this.inDirText.setText(selectedFile.getAbsolutePath());
        }
    }//GEN-LAST:event_inDirButtonActionPerformed

    private void outDirButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_outDirButtonActionPerformed
        File selectedFile = Utilities.getDir(
                "Select Output Directory",
                this.outDirText.getText().trim(),
                false,
                this);
        if(selectedFile != null){
            this.outDirText.setText(selectedFile.getAbsolutePath());
        }
    }//GEN-LAST:event_outDirButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        if(this.validateSettings()) {
            boolean useDirIn = this.inputReadTIFFRadioButton.isSelected();
            this.conf.setUseDirectoryForInput(useDirIn);
            if(useDirIn) {
                String inDir = this.inDirText.getText().trim();
                this.conf.setInputDir(inDir);
            }
            
            boolean useDirOut = this.outputSaveTIFFRadioButton.isSelected();
            this.conf.setUseDirectoryForOutput(useDirOut);
            if(useDirOut) {
                String outDir = this.outDirText.getText().trim();
                this.conf.setOutputDir(outDir);
            }
            
            this.conf.setCropFromTopPixels(this.getCropFromTopPixels());
            this.conf.setCropFromBottomPixels(this.getCropFromBottomPixels());
            this.conf.setImageStackInverted(this.isInvertedImageStack());
            this.conf.setKeepIntermediate(this.getKeepIntermediate());
            
            try {
                this.conf.saveChanges();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
            
            this.userClickedOK = true;
            this.dispose();
        }
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.userClickedOK = false;
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void inputUseOpenRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_inputUseOpenRadioButtonItemStateChanged
        this.updateUI();
    }//GEN-LAST:event_inputUseOpenRadioButtonItemStateChanged

    private void inputReadTIFFRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_inputReadTIFFRadioButtonItemStateChanged
        this.updateUI();
    }//GEN-LAST:event_inputReadTIFFRadioButtonItemStateChanged

    private void outputOpenRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_outputOpenRadioButtonItemStateChanged
        this.updateUI();
    }//GEN-LAST:event_outputOpenRadioButtonItemStateChanged

    private void outputSaveTIFFRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_outputSaveTIFFRadioButtonItemStateChanged
        this.updateUI();
    }//GEN-LAST:event_outputSaveTIFFRadioButtonItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel contentPanel;
    private javax.swing.JButton inDirButton;
    private javax.swing.JTextField inDirText;
    private javax.swing.JRadioButton inputReadTIFFRadioButton;
    private javax.swing.JRadioButton inputUseOpenRadioButton;
    private javax.swing.JCheckBox invertedImageStackCheckBox;
    private javax.swing.JCheckBox keepIntermediateCheckbox;
    private javax.swing.JButton okButton;
    private javax.swing.JPanel okCancelPanel;
    private javax.swing.JButton outDirButton;
    private javax.swing.JTextField outDirText;
    private javax.swing.JRadioButton outputOpenRadioButton;
    private javax.swing.JRadioButton outputSaveTIFFRadioButton;
    private javax.swing.JPanel perfTestingPanel;
    private javax.swing.JLabel pixelsClipFromBottomLabel;
    private javax.swing.JFormattedTextField pixelsClipFromBottomText;
    private javax.swing.JLabel pixelsClipFromTopLabel;
    private javax.swing.JFormattedTextField pixelsClipFromTopText;
    // End of variables declaration//GEN-END:variables

}
