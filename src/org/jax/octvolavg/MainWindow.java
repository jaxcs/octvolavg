package org.jax.octvolavg;

import ij.ImageJ;
import ij.ImagePlus;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyledDocument;

public class MainWindow extends javax.swing.JFrame
{
    private static final long serialVersionUID = -2172427952669612520L;
    
    // we create an instance so that the ImageJ internals are properly initialized
    public static final ImageJ ijInstance = new ImageJ(ImageJ.NO_SHOW);

    private volatile boolean running = false;
    private AtomicBoolean canceling = new AtomicBoolean(false);
    
    private JComponent[] componentsToDisableWhileRunning;
    private Configuration conf;
    private DocumentLogger docLogger;
    
    public MainWindow() throws IOException {
        this.initComponents();
        this.setTitle("OCT Volume Averager (" + MainDriver.VERSION + ")");
        
        this.componentsToDisableWhileRunning = new JComponent[] {
                this.inDirLabel,
                this.inDirText,
                this.inDirButton,
                this.outDirLabel,
                this.outDirText,
                this.outDirButton,
                this.invertedImageStackCheckBox,
                this.keepIntermediateCheckbox,
                this.pixelsClipFromTopLabel,
                this.pixelsClipFromTopText,
                this.pixelsClipFromBottomLabel,
                this.pixelsClipFromBottomText,
                this.okButton,
                this.closeButton
        };
        
        this.conf = new Configuration();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    MainWindow.this.initUIFromConfig();
                } catch(Exception ex) {
                    System.err.println("initialization failed");
                    ex.printStackTrace();
                }
            }
        });
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
        
        StyledDocument logDoc = this.logTextPane.getStyledDocument();
        logDoc.addDocumentListener(new DocumentListener() {
            public void removeUpdate(DocumentEvent docEvent) {
                // Don't care
            }
            
            public void insertUpdate(DocumentEvent docEvent) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        MainWindow.this.logTextPane.scrollRectToVisible(new Rectangle(
                                0,
                                MainWindow.this.logTextPane.getHeight(),
                                0,
                                0));
                    }
                });
            }
            
            public void changedUpdate(DocumentEvent docEvent) {
                // Don't care
            }
        });
        this.docLogger = new DocumentLogger(logDoc);
        
        this.updateUI();
    }

    private void updateUI() {
        SafeAWTInvoker.safeInvokeNowOrLater(new Runnable() {
            public void run() {
                for(JComponent c : MainWindow.this.componentsToDisableWhileRunning) {
                    c.setEnabled(!MainWindow.this.running);
                }
                
                /*
                if(!MainWindow.this.running) {
                    MainWindow.this.useROICheckbox.setEnabled(anyOpt);
                    MainWindow.this.annoDirLabel.setEnabled(useROI && anyOpt);
                    MainWindow.this.annoDirText.setEnabled(useROI && anyOpt);
                    MainWindow.this.annoDirButton.setEnabled(useROI && anyOpt);
                }
                */
                
                MainWindow.this.cancelButton.setEnabled(MainWindow.this.running && !MainWindow.this.canceling.get());
            }
        });
    }
    
    private void setStatusText(final String text, final boolean isError) {
        SafeAWTInvoker.safeInvokeNowOrLater(new Runnable() {
            public void run() {
                MainWindow.this.statusMessageLabel.setForeground(isError ? Color.RED : null);
                MainWindow.this.statusMessageLabel.setText(text);
            }
        });
    }
    
    private void ok() {
        this.statusBar.setIndeterminate(true);
        this.setStatusText("Working...", false);
        try {
            if(this.validateSettings()) {
                // first grab the UI state while we're still in the AWT thread
                this.running = true;
                this.updateUI();
                
                final String inBaseDirStr = this.inDirText.getText().trim();
                final String outBasetDirStr = this.outDirText.getText().trim();
                final boolean invertedImageStack = this.invertedImageStackCheckBox.isSelected();
                final boolean keepIntermediate = this.keepIntermediateCheckbox.isSelected();
                final int clipFromTopPixels = ((Number)this.pixelsClipFromTopText.getValue()).intValue();
                final int clipFromBottomPixels = ((Number)this.pixelsClipFromBottomText.getValue()).intValue();
                
                // now launch a worker thread to free up the UI
                new Thread() {
                    @Override public void run() {
                        try {
                            MainWindow.this.conf.setInputDir(inBaseDirStr);
                            MainWindow.this.conf.setOutputDir(outBasetDirStr);
                            MainWindow.this.conf.setImageStackInverted(invertedImageStack);
                            MainWindow.this.conf.setKeepIntermediate(keepIntermediate);
                            MainWindow.this.conf.setCropFromTopPixels(clipFromTopPixels);
                            MainWindow.this.conf.setCropFromBottomPixels(clipFromBottomPixels);
                            
                            MainWindow.this.conf.saveChanges();
                            
                            // initialize the image groups by reading either TIFFs
                            // or OCT images
                            Map<String, List<File>> imgFileGroups;
                            Map<String, Iterator<ImagePlus>> imgGroups = new HashMap<String, Iterator<ImagePlus>>();
                            imgFileGroups = Utilities.imgGroupsIn(
                                    new File(inBaseDirStr),
                                    Utilities.TIFF_IMAGE_PATTERN);
                            if(imgFileGroups.isEmpty()) {
                                // there are no TIFFs so we'll see if we can read OCT
                                imgFileGroups = Utilities.imgGroupsIn(
                                        new File(inBaseDirStr),
                                        Utilities.OCT_IMAGE_PATTERN);
                                if(imgFileGroups.isEmpty()) {
                                    MainWindow.this.errorOccurred("could not find any TIFF or OCT images to register");
                                    return;
                                }

                                if(Utilities.isOctReaderInClasspath()) {
                                    for(Map.Entry<String, List<File>> imgStackGroup : imgFileGroups.entrySet()) {
                                        Iterator<File> fileIter = imgStackGroup.getValue().iterator();
                                        imgGroups.put(
                                                imgStackGroup.getKey(),
                                                new LazyOCTReader(fileIter, MainWindow.this.docLogger, true));
                                    }
                                } else {
                                    MainWindow.this.errorOccurred("could not register OCT file since the OCT_Reader is missing");
                                    return;
                                }
                            } else {
                                // we found TIFFs so we won't even bother looking for OCTs
                                for(Map.Entry<String, List<File>> imgStackGroup : imgFileGroups.entrySet()) {
                                    Iterator<File> fileIter = imgStackGroup.getValue().iterator();
                                    imgGroups.put(
                                            imgStackGroup.getKey(),
                                            new LazyTIFFReader(fileIter, MainWindow.this.docLogger));
                                }
                            }
                            
                            MainDriver_ToEnFace driver = new MainDriver_ToEnFace(
                                    new File(outBasetDirStr),
                                    clipFromTopPixels,
                                    clipFromBottomPixels,
                                    invertedImageStack,
                                    keepIntermediate,
                                    MainWindow.this.docLogger,
                                    MainWindow.this.canceling);
                            for(Map.Entry<String, Iterator<ImagePlus>> imgGroup : imgGroups.entrySet()) {
                                if(MainWindow.this.canceling.get()) {
                                    break;
                                }
                                driver.turboProcessImages(imgGroup.getKey(), imgGroup.getValue());
                            }
                            
                            MainWindow.this.resetStatus();
                        } catch(final Throwable ex) {
                            MainWindow.this.errorOccurred(ex);
                        }
                    }
                }.start();
            } else {
                this.resetStatus();
            }
        } catch(final Exception ex) {
            MainWindow.this.errorOccurred(ex);
        }
    }
    
    private void errorOccurred(final Throwable ex) {
        this.resetStatus();
        this.setStatusText(ex.getLocalizedMessage(), true);
        this.updateUI();
        
        this.docLogger.printThrowable(ex);
    }
    
    private void errorOccurred(final String s) {
        this.resetStatus();
        this.setStatusText(s, true);
        this.updateUI();
        
        this.docLogger.println(s);
    }
    
    private void resetStatus() {
        this.running = false;
        this.canceling.set(false);
        SafeAWTInvoker.safeInvokeNowOrLater(new Runnable() {
            public void run() {
                MainWindow.this.statusBar.setIndeterminate(false);
                MainWindow.this.statusBar.setMaximum(0);
                MainWindow.this.statusBar.setValue(0);
                MainWindow.this.setStatusText("Idle", false);
                MainWindow.this.updateUI();
            }
        });
        
        this.docLogger.println("Idle");
    }
    
    /**
     * Validate the UI settings. This function assumes that we're running in
     * the AWT thread
     * @return  true if the settings are valid
     */
    private boolean validateSettings() {
        if(!Utilities.isValidDir(this.inDirText.getText(), false)) {
            JOptionPane.showMessageDialog(
                    this,
                    "The input directory must be an existing directory",
                    "Missing Input Directory",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        if(!Utilities.isValidDir(this.outDirText.getText(), true)) {
            JOptionPane.showMessageDialog(
                    this,
                    "You must specify an output directory",
                    "Output Directory Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        return true;
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

        outDirLabel = new javax.swing.JLabel();
        inDirButton = new javax.swing.JButton();
        perfTestingPanel = new javax.swing.JPanel();
        invertedImageStackCheckBox = new javax.swing.JCheckBox();
        keepIntermediateCheckbox = new javax.swing.JCheckBox();
        pixelsClipFromTopLabel = new javax.swing.JLabel();
        pixelsClipFromTopText = new javax.swing.JFormattedTextField();
        pixelsClipFromBottomLabel = new javax.swing.JLabel();
        pixelsClipFromBottomText = new javax.swing.JFormattedTextField();
        inDirText = new javax.swing.JTextField();
        javax.swing.JPanel okCancelPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        inDirLabel = new javax.swing.JLabel();
        logScrollPane = new javax.swing.JScrollPane();
        logTextPane = new javax.swing.JTextPane();
        logLabel = new javax.swing.JLabel();
        outDirButton = new javax.swing.JButton();
        outDirText = new javax.swing.JTextField();
        statusLabel = new javax.swing.JLabel();
        statusBar = new javax.swing.JProgressBar();
        statusMessageLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu mainMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem licenseMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator separator1 = new javax.swing.JPopupMenu.Separator();
        javax.swing.JMenuItem quitMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        outDirLabel.setText("Output Dir:");

        inDirButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/action/browse-16x16.png"))); // NOI18N
        inDirButton.setText("Browse Dirs");
        inDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inDirButtonActionPerformed(evt);
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
                        .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(keepIntermediateCheckbox)
                            .addComponent(pixelsClipFromTopLabel)
                            .addComponent(pixelsClipFromBottomLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pixelsClipFromBottomText, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(pixelsClipFromTopText, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        perfTestingPanelLayout.setVerticalGroup(
            perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(perfTestingPanelLayout.createSequentialGroup()
                .addComponent(invertedImageStackCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(keepIntermediateCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pixelsClipFromTopLabel)
                    .addComponent(pixelsClipFromTopText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(perfTestingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pixelsClipFromBottomLabel)
                    .addComponent(pixelsClipFromBottomText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        okButton.setText("Run");
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

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });
        okCancelPanel.add(closeButton);

        inDirLabel.setText("Input Dir:");

        logTextPane.setEditable(false);
        logScrollPane.setViewportView(logTextPane);

        logLabel.setText("Session Log:");

        outDirButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/action/browse-16x16.png"))); // NOI18N
        outDirButton.setText("Browse Dirs");
        outDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                outDirButtonActionPerformed(evt);
            }
        });

        statusLabel.setText("Status:");

        statusMessageLabel.setText("Idle");

        mainMenu.setText("Main Menu");

        licenseMenuItem.setText("About and License ...");
        licenseMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                licenseMenuItemActionPerformed(evt);
            }
        });
        mainMenu.add(licenseMenuItem);
        mainMenu.add(separator1);

        quitMenuItem.setText("Quit");
        quitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quitMenuItemActionPerformed(evt);
            }
        });
        mainMenu.add(quitMenuItem);

        menuBar.add(mainMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(okCancelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 603, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(perfTestingPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(statusBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inDirLabel)
                            .addComponent(outDirLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(outDirText)
                            .addComponent(inDirText))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inDirButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(outDirButton, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addComponent(logScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(statusLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(statusMessageLabel))
                            .addComponent(logLabel))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(inDirLabel)
                    .addComponent(inDirButton)
                    .addComponent(inDirText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(outDirLabel)
                    .addComponent(outDirText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(outDirButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(perfTestingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(logLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(logScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 149, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(statusLabel)
                    .addComponent(statusMessageLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(okCancelPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void inDirButtonActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_inDirButtonActionPerformed
        File selectedFile = Utilities.getDir(
                "Select Input Directory",
                this.inDirText.getText().trim(),
                false,
                this);
        if(selectedFile != null){
            this.inDirText.setText(selectedFile.getAbsolutePath());
        }
    }//GEN-LAST:event_inDirButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_okButtonActionPerformed
        this.ok();
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_cancelButtonActionPerformed
        if(this.running){
            this.canceling.set(true);
            this.docLogger.println("Canceling current operation...");
            this.updateUI();
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_closeButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void outDirButtonActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_outDirButtonActionPerformed
        File selectedFile = Utilities.getDir(
                "Select Output Directory",
                this.outDirText.getText().trim(),
                false,
                this);
        if(selectedFile != null){
            this.outDirText.setText(selectedFile.getAbsolutePath());
        }
    }//GEN-LAST:event_outDirButtonActionPerformed

    private void licenseMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_licenseMenuItemActionPerformed
        LicenseDialog licenseDialog = new LicenseDialog(this, true);
        licenseDialog.setVisible(true);
    }//GEN-LAST:event_licenseMenuItemActionPerformed

    private void quitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quitMenuItemActionPerformed
        this.canceling.set(true);
        this.dispose();
    }//GEN-LAST:event_quitMenuItemActionPerformed

    public static void main(String args[]) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    new MainWindow().setVisible(true);
                } catch(Exception ex) {
                    System.err.println("failed to create main window");
                    ex.printStackTrace();
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JButton inDirButton;
    private javax.swing.JLabel inDirLabel;
    private javax.swing.JTextField inDirText;
    private javax.swing.JCheckBox invertedImageStackCheckBox;
    private javax.swing.JCheckBox keepIntermediateCheckbox;
    private javax.swing.JLabel logLabel;
    private javax.swing.JScrollPane logScrollPane;
    private javax.swing.JTextPane logTextPane;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JButton okButton;
    private javax.swing.JButton outDirButton;
    private javax.swing.JLabel outDirLabel;
    private javax.swing.JTextField outDirText;
    private javax.swing.JPanel perfTestingPanel;
    private javax.swing.JLabel pixelsClipFromBottomLabel;
    private javax.swing.JFormattedTextField pixelsClipFromBottomText;
    private javax.swing.JLabel pixelsClipFromTopLabel;
    private javax.swing.JFormattedTextField pixelsClipFromTopText;
    private javax.swing.JProgressBar statusBar;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel statusMessageLabel;
    // End of variables declaration//GEN-END:variables
}
