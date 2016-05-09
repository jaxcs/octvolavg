package com.bioptigen;
/* 	Oct_Reader 
 * 
 * 	File Information
 * 	Created By: Bradley Bower
 * 				Bioptigen, Inc. 
 * 				bbower@bioptigen.com  
 *	Copyright Bioptigen, Inc. 2010 	
 *
 *  Edited by Keith Sheppard to enable code to be callable as an API
 */

// Support Files 
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.Checkbox;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// Plugin 
public class OCT_Reader implements PlugIn {
    // frame key names
    private final static String strFrameHeader      = "FRAMEHEADER"; 
    private final static String strFrameCount       = "FRAMECOUNT";
    private final static String strLineCount        = "LINECOUNT";
    private final static String strLineLength       = "LINELENGTH";
    private final static String strSampleFormat     = "SAMPLEFORMAT";
    private final static String strDescription      = "DESCRIPTION";
    private final static String strXMin             = "XMIN";
    private final static String strXMax             = "XMAX";
    private final static String strXCaption         = "XCAPTION";
    private final static String strYMin             = "YMIN";
    private final static String strYMax             = "YMAX";
    private final static String strYCaption         = "YCAPTION";
    private final static String strScanType         = "SCANTYPE";
    private final static String strScanDepth        = "SCANDEPTH";
    private final static String strScanLength       = "SCANLENGTH";
    private final static String strAzScanLength     = "AZSCANLENGTH";
    private final static String strElScanLength     = "ELSCANLENGTH";
    private final static String strObjectDistance   = "OBJECTDISTANCE";
    private final static String strScanAngle        = "SCANANGLE";
    private final static String strFramesPerVolume  = "FRAMESPERVOLUME";
    private final static String strScans            = "SCANS" ; // v1.7 version of FRAMESPERVOLUME
    private final static String strFrames           = "FRAMES"; // v1.7 update to include multiple frames/scan
    private final static String strDopplerFlag      = "DOPPLERFLAG";
    private final static String strSubFramesFlag    = "SUBFRAMESFLAG"; 
    private final static String strSubFrames        = "SUBFRAMES"; 
    private final static String strSubFrameLines    = "SUBFRAMELINES"; 
    private final static String strSubFrameOffsets  = "SUBFRAMEOFFSETS"; 
    private final static String strSubFrameRadii    = "SUBFRAMERADII"; 
    private final static String strConfig           = "CONFIG"; // "Obfuscated ASCII String"

    // frame header keynames
    private final static String strFrameData        = "FRAMEDATA"; 
    private final static String strFrameDateTime    = "FRAMEDATETIME";
    private final static String strFrameTimeStamp   = "FRAMETIMESTAMP";
    private final static String strFrameLines       = "FRAMELINES";  
    private final static String strFrameSamples     = "FRAMESAMPLES";
    private final static String strDopplerSamples   = "DOPPLERSAMPLES";

    // oct reader information
    final String strOctReaderVersion    = "1.0"; 
    final String strInVivoVueVersion    = "1.7"; 
    final String strOctFileFormat       = "x010A";

    // GUI for processing selection
    /** Constants **/
    boolean bCrop           = false;
    boolean bSVP            = false; 
    boolean bRegister       = false;
    boolean bMontage        = false;
    boolean bScaleBar       = false;
    boolean bHeader         = false; 
    boolean bStripMontage   = false; 
    boolean bBatch          = false;  

    /** Creates a new instance of Oct_Reader_GUI*/
    public void run (String arg)
    {
            String statusString = ""; // Extra status info passed to loadOctFile(...)
            String directory;
            String name;
            String octFile;
            
            // batch processing constants
            String sBatchText       = "0";
            int iBatchCount         = 0;  
            int iCurrentBatch       = 0; 
            int nameLength          = 0;
            String batchFilename    = "";
            String rootFilename     = "";

            /** Create Dialog **/
            GenericDialog gd        = new GenericDialog("OCT Reader");
            Panel infoPanel         = new Panel(); 
            Panel dataPanel         = new Panel();
            Label lTitle            = new Label("OCT Reader " + strOctReaderVersion); 
            Label lCompatibility    = new Label("Oct File Format " + strOctFileFormat); 
            Checkbox cbCrop         = new Checkbox("Crop");
            Checkbox cbSVP          = new Checkbox("SVP"); 
            Checkbox cbRegister     = new Checkbox("Register"); 
            Checkbox cbMontage      = new Checkbox("Montage");
            Checkbox cbWolfMontage  = new Checkbox("Strip Montage"); 
            Checkbox cbScaleBar     = new Checkbox("Scale Bar"); 
            Checkbox cbHeader       = new Checkbox("Header Info Only");
            Checkbox cbBatch        = new Checkbox("Batch Processing"); 
            Label lProcessOption    = new Label("Processing Options: ");
            Label lDisplayOption    = new Label("Display Options: "); 
            TextField tBatch        = new TextField(sBatchText);

            infoPanel.setLayout(new GridLayout(2,1)); 
            infoPanel.add(lTitle);
            infoPanel.add(lCompatibility); 

            GridBagLayout gridbag	= new GridBagLayout(); 
            GridBagConstraints c	= new GridBagConstraints(); 
            c.gridx		= 0; 
            c.gridy		= 0;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(lDisplayOption,c);
            c.gridx		= 0; 
            c.gridy		= 1;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbCrop,c); 
            c.gridx		= 1;
            c.gridy		= 1;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbSVP,c); 
            c.gridx		= 2; 
            c.gridy		= 1;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbRegister,c);
            c.gridx		= 0; 
            c.gridy		= 2;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbMontage,c);
            c.gridx		= 1; 
            c.gridy		= 2;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbWolfMontage,c);
            c.gridx		= 0; 
            c.gridy		= 3;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbScaleBar,c);
            c.gridx		= 0; 
            c.gridy		= 4;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(lProcessOption,c);
            c.gridx		= 0; 
            c.gridy		= 5;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(cbBatch,c);
            c.gridx		= 1; 
            c.gridy		= 5;
            c.anchor	= GridBagConstraints.WEST; 
            gridbag.addLayoutComponent(tBatch,c);

            dataPanel.setLayout(gridbag); 

            dataPanel.add(lDisplayOption); 
            dataPanel.add(cbCrop); 
            dataPanel.add(cbSVP); 
            dataPanel.add(cbRegister); 
            dataPanel.add(cbMontage);
            dataPanel.add(cbWolfMontage); 
            dataPanel.add(cbScaleBar);
            dataPanel.add(lProcessOption); 
            dataPanel.add(cbBatch);
            dataPanel.add(tBatch); 

            gd.addPanel(infoPanel); 
            gd.addPanel(dataPanel); 
            gd.showDialog(); 

            if (gd.wasCanceled()) 
            { 
                    return; 
            }

            /** Get User Selected Options **/
            // Cycles through boolean checkboxes by creation order.
            bCrop 		= cbCrop.getState(); 
            bSVP 		= cbSVP.getState(); 
            bRegister 	= cbRegister.getState(); 
            bMontage 	= cbMontage.getState(); 
            bScaleBar 	= cbScaleBar.getState(); 
            bStripMontage= cbWolfMontage.getState(); 
            // bHeader 	= cbHeader.getState();  
            bBatch		= cbBatch.getState(); 
            sBatchText	= tBatch.getText(); 
            iBatchCount = Integer.parseInt(sBatchText); // pase string for integer

            OpenDialog od               = new OpenDialog("Select .oct file to load...", arg); 
            directory                   = od.getDirectory(); 
            name                        = od.getFileName(); 
            if (name == null)
            {
                    return; 
            }
            rootFilename	= name; 

            try {
                if (bBatch == true) 
                {
                        for (int i = 1; i <= iBatchCount; i++) 
                        {
                                iCurrentBatch	= i-2; // starts incrementing at i = 2, needs to be = 0 when i = 2
                                nameLength		= rootFilename.length(); 
                                statusString	= "Batch processing: " + i + " of " + iBatchCount + "; ";  
    
                                // Generate filename; first file is name, second file is name + "_00.oct"
                                if (i > 1) 
                                {
                                        batchFilename 	= ""; // reset batchFilename 
                                        batchFilename	= rootFilename.substring(0,nameLength-4); // 0-index and remove 3 characters for '.oct'
                                        if (i < 10)
                                        {
                                                batchFilename	= batchFilename + "_0" + iCurrentBatch + ".oct";
                                        }
                                        else 
                                        {
                                                batchFilename	= batchFilename + "_" + iCurrentBatch + ".oct"; 
                                        }
                                }
                                else 
                                {
                                        batchFilename	= rootFilename; 
                                }
                                name			= batchFilename; 
                                octFile			= directory + batchFilename; 
    
                                loadOctFile(octFile, statusString, true, true); 
                        } // for iBatchCount
                }
                else 
                {
                        octFile		= directory + rootFilename; 
                        loadOctFile(octFile, statusString, true, true); 
                } // 
            } catch(IOException ex) {
                IJ.showStatus("Error loading file");
            }

    } // run(...) 

	// Load OCT file
    public ImagePlus loadOctFile(String octFile, String statusString, boolean show, boolean use8Bit) throws IOException {
        String currentSlice = "";
        String description  = ""; 
        String xCaption     = ""; 
        String yCaption     = ""; 
        String key          = "";
        String config       = ""; 
        int frameCount      = 0; 
        int lineCount       = 0; 
        int lineLength      = 0; 
        int dataLength      = 0; 
        int keyLen          = 0; 
        int dopplerFlag     = 0; 
        int scanType        = 0; 
        int version         = 0; 
        int headerFlag      = 0;
        int frameFlag       = 0; 
        int currentFrame    = 1;
        int sliceCount      = 0;
        double xScalePixels = 0; 
        double yScalePixels = 0;
        double xScaleMax    = 0; 
        double yScaleMax    = 0; 
        double xMin         = 0; 
        double xMax         = 0; 
        double yMin         = 0; 
        double yMax         = 0; 
        double scanLength   = 0; 
        double azScanLength = 0; 
        double elScanLength = 0; 
        double scanDepth    = 0; 
        double scanAngle    = 0;
        byte[] byteData     = new byte[32];
        short pixel; 
        
        // Buffer constants
        long fileSize    = 0; 
        int bufferSize  = 0; 

        /* It should be possible to read data in as an array of pixels and then store those
         * to the ip structure for ImageJ to display. This needs to be implemented to speed up 
         * the reader. */
        File octFileInformation     = new File(octFile);               // file variable for file information
        fileSize                    = octFileInformation.length();     // file size in bytes stored in a long 
        bufferSize                     = (int) fileSize;               // cast long to integer value
        FileInputStream inputFile	= new FileInputStream(octFile); 
        BufferedInputStream inputData   = new BufferedInputStream(inputFile,bufferSize); 
        
        // Magic Number
        inputData.read(byteData,0,4);

        // Software Version
        inputData.read(byteData,0,2); 
        version 		            = byteToShort(byteData); 

        inputData.read(byteData,0,4); 
        keyLen 			= byteToInt(byteData);
        inputData.read(byteData,0,keyLen); 
        key 			= new String(byteData); 
        key 			= key.trim(); 
        inputData.read(byteData,0,4);

        if (!key.equals(strFrameHeader))
        {
                IJ.showStatus("Error Loading Frame Header"); 
        }
        else {IJ.showStatus("");}

        headerFlag 		= 0; 
        while (headerFlag == 0)
        {
                byteData 	= new byte[32]; 	// Clear array 
                inputData.read(byteData,0,4); 
                keyLen 		= byteToInt(byteData);
                inputData.read(byteData,0,keyLen);
                key 		= new String(byteData); 
                key 		= key.trim();

                // Check format for if/else if and string compare in Java
                if (key.equals(strFrameCount))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); 
                        frameCount 		= byteToInt(byteData);}
                else if (key.equals(strLineCount))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        lineCount 		= byteToInt(byteData); }
                else if (key.equals(strLineLength))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        lineLength 		= byteToInt(byteData); }
                else if (key.equals(strSampleFormat))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }
                else if (key.equals(strDescription))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        byteData        = new byte[dataLength];
                        inputData.read(byteData,0,dataLength); 
                        description 	= new String(byteData);
                        description 	= description.trim(); }
                else if (key.equals(strXMin))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        xMin 			= byteToDouble(byteData);} 
                else if (key.equals(strXMax))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        xMax 			= byteToDouble(byteData); }
                else if (key.equals(strXCaption))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        byteData        = new byte[dataLength];
                        inputData.read(byteData,0,dataLength); 
                        xCaption 		= new String(byteData); 
                        xCaption 		= xCaption.trim(); }
                else if (key.equals(strYMin))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        yMin 			= byteToDouble(byteData); }
                else if (key.equals(strYMax))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        yMax 			= byteToDouble(byteData); }
                else if (key.equals(strYCaption))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        byteData        = new byte[dataLength];
                        inputData.read(byteData,0,dataLength); 
                        yCaption 		= new String(byteData);
                        yCaption 		= yCaption.trim(); }
                else if (key.equals(strScanType))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); 
                        scanType 		= byteToInt(byteData); }
                else if (key.equals(strScanDepth))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        scanDepth 		= byteToDouble(byteData); }
                else if (key.equals(strScanLength))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        scanLength 		= byteToDouble(byteData); }
                else if (key.equals(strAzScanLength))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        azScanLength 	= byteToDouble(byteData); }
                else if (key.equals(strElScanLength))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        elScanLength 	= byteToDouble(byteData); }
                else if (key.equals(strObjectDistance))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); }
                else if (key.equals(strScanAngle))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); 
                        scanAngle 		= byteToDouble(byteData); }
                else if (key.equals(strFramesPerVolume))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }					
                else if (key.equals(strScans))
                {   	// v1.7 and later
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }
                else if (key.equals(strFrames))
                { 	// v1.7 and later
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }
                else if (key.equals(strDopplerFlag))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); 
                        dopplerFlag = byteToInt(byteData); }
                else if (key.equals(strConfig))
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        byteData        = new byte[dataLength];
                        inputData.read(byteData,0,dataLength); 
                        config 		= new String(byteData);
                        config		= config.trim(); }
                else if (key.equals(strSubFrames) && version == 105)
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }
                else if (key.equals(strSubFrameLines) && version == 105)
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,4); }
                else if (key.equals(strSubFrameOffsets) && version == 105)
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); }
                else if (key.equals(strSubFrameRadii) && version== 105)
                {
                        inputData.read(byteData,0,4); 
                        dataLength 	= byteToInt(byteData);
                        inputData.read(byteData,0,dataLength); }
                else 
                {
                        headerFlag 	= 1;
                }				
        }
        
        ImagePlus impIntensity = null;

        // bHeader = true; 
        // Dialog Box to Indicate Header Values
        //  For Debugging Purposes
        if (bHeader) 
        { 	// only show if want header information only 
                GenericDialog gd1 = new GenericDialog("Header Information");
                gd1.addMessage("Target Key: " + strFrameCount); 
                gd1.addMessage("Frame Count: " + frameCount); 
                gd1.addMessage("Line Count: " + lineCount);
                gd1.addMessage("Line Length: " + lineLength);
                gd1.addMessage("Description: " + description);
                gd1.addMessage("Scan Type: " + scanType); 
                gd1.addMessage("XMin: " + xMin);
                gd1.addMessage("XMax: " + xMax);
                gd1.addMessage("X Caption: " + xCaption); 
                gd1.addMessage("YMin: " + yMin);
                gd1.addMessage("YMax: " + yMax);
                gd1.addMessage("Y Caption: " + yCaption);
                gd1.addMessage("Scan Depth: " + scanDepth); 
                gd1.addMessage("Scan Length: " + Double.toString(scanLength)); 
                gd1.addMessage("Az Scan Length: " + Double.toString(azScanLength)); 
                gd1.addMessage("El Scan Length: " + Double.toString(elScanLength)); 
                gd1.addMessage("Scan Angle: " + scanAngle);
                gd1.addMessage("Doppler Flag: " + dopplerFlag); 
                gd1.showDialog();			
                if (gd1.wasCanceled()) return null;
        }
        else
        { 	
                // don't show if only want frame header information
                // Initialize Intensity Data
                ImageStack stackIntensity = new ImageStack(lineCount,lineLength); // modify below as well
                if(use8Bit) {
                    impIntensity = NewImage.createByteImage("OCT Intensity Image", lineLength, lineCount, frameCount, NewImage.FILL_BLACK);
                } else {
                    impIntensity = NewImage.createShortImage("OCT Intensity Image", lineLength, lineCount, frameCount, NewImage.FILL_BLACK);
                }
                ImageProcessor ipIntensity = impIntensity.getProcessor();

                // Initialize Doppler Data
                ImageStack stackDoppler = new ImageStack(lineCount, lineLength);
                ImagePlus impDoppler;
                if(use8Bit) {
                    impDoppler = NewImage.createByteImage("OCT Doppler Image", lineLength, lineCount, frameCount, NewImage.FILL_BLACK);
                } else {
                    impDoppler = NewImage.createShortImage("OCT Doppler Image", lineLength, lineCount, frameCount, NewImage.FILL_BLACK);
                }
                ImageProcessor ipDoppler = impDoppler.getProcessor(); 

                // frameDateTime information
                short [] frameYear 			= new short[frameCount]; 
                short [] frameMonth			= new short[frameCount]; 
                short [] frameDayOfWeek		= new short[frameCount]; 
                short [] frameDay			= new short[frameCount]; 
                short [] frameHour			= new short[frameCount]; 
                short [] frameMinute 		= new short[frameCount]; 
                short [] frameSecond		= new short[frameCount]; 
                short [] frameMillisecond 	= new short[frameCount];
                int [] frameDifference 		= new int[frameCount]; 
                int frameDuration 			= 0;  	// time difference between frames in milliseconds

                while (currentFrame <= frameCount)
                {        	// lower value of frameCount for debugging purposes
                        IJ.showProgress(currentFrame,frameCount); 
                        IJ.showStatus(statusString + "Loading frame " + currentFrame + " of " + frameCount);
                        frameFlag 	= 0; 						// reset frameFlag for next frame read 
                        impIntensity.setSlice(currentFrame);
                        impDoppler.setSlice(currentFrame);
                        sliceCount 							= currentFrame - 1; 
                        currentSlice                        = Integer.toString(sliceCount);

                        byteData 	= new byte[32]; 	// Clear array 
                        inputData.read(byteData,0,4); 
                        keyLen 		= byteToInt(byteData);
                        byteData 	= new byte[32]; 	// Clear array 
                        inputData.read(byteData,0,keyLen);
                        key 		= new String(byteData); 
                        key 		= key.trim();
                        byteData 	= new byte[32]; 	// Clear array 
                        inputData.read(byteData,0,4);

                        if (key.equals(strFrameData)) 
                        {
                                while (frameFlag == 0) 
                                {
                                        byteData 	= new byte[32]; 	// Clear array 
                                        inputData.read(byteData,0,4); 
                                        keyLen 		= byteToInt(byteData);
                                        inputData.read(byteData,0,keyLen);
                                        key 		= new String(byteData); 
                                        key 		= key.trim();

                                        if (key.equals(strFrameDateTime)) 
                                        {
                                                byteData 	= new byte[32]; 
                                                inputData.read(byteData,0,4); 
                                                dataLength 	= byteToInt(byteData); 
                                                short frameValueTemp; 
                                                short frameValue;
                                                for (int i = 0; i < 8; i++) 
                                                {
                                                        inputData.read(byteData,0,2);
                                                        frameValueTemp 	= byteToShort(byteData);// inputData.readShort();									
                                                        frameValue 		= Short.reverseBytes(frameValueTemp); 
                                                        switch(i) 
                                                        {
                                                                case 0: frameYear[sliceCount]	 	= frameValue; 
                                                                case 1: frameMonth[sliceCount] 		= frameValue; 
                                                                case 2: frameDayOfWeek[sliceCount] 	= frameValue;
                                                                case 3: frameDay[sliceCount]	 	= frameValue; 
                                                                case 4: frameHour[sliceCount] 		= frameValue; 
                                                                case 5: frameMinute[sliceCount]	 	= frameValue; 
                                                                case 6: frameSecond[sliceCount] 	= frameValue; 
                                                                case 7: frameMillisecond[sliceCount]= frameValue; 
                                                        } // switch 									
                                                } // for 8 WORDS in SYSTEMTIME structure
                                                inputData.skip(dataLength-16); 
                                        } // if key.equals(strFrameDateTime)
                                        else if (key.equals(strFrameTimeStamp)) 
                                        {
                                                byteData 	= new byte[32]; 	// Clear array 
                                                inputData.read(byteData,0,4); 
                                                dataLength 	= byteToInt(byteData);
                                                inputData.skip(dataLength); 
                                        } // if key.equals(strFrameTimeStamp)
                                        else if (key.equals(strFrameLines)) 
                                        {
                                                byteData 	= new byte[32]; 	// Clear array 
                                                inputData.read(byteData,0,4); 
                                                dataLength 	= byteToInt(byteData);
                                                byteData 	= new byte[32]; 	// Clear array 
                                                inputData.read(byteData,0,4);
                                        } // if key.equals(strFrameLines)
                                        else if (key.equals(strFrameSamples)) 
                                        {
                                                byteData 	= new byte[32]; 	// Clear array 
                                                inputData.read(byteData,0,4); 
                                                dataLength 	= byteToInt(byteData);
                                                /* The below may be better implemented by using ipIntensity.putRow() 
                                                 * or ipIntensity.putColumn()--the only problem is that we're reading in a byte
                                                 * array and the values need to be shorts. */
                                                for (int j = 0; j < lineCount; j++)
                                                {
                                                        for (int k = 0; k < lineLength; k++)
                                                        {
                                                                inputData.read(byteData, 0, 2);
                                                                pixel               = byteToShort(byteData);
                                                                ipIntensity.putPixel(k,j,pixel); 
                                                        } // for k = ... lineLength
                                                } // for j = ... lineCount 
                                                stackIntensity.addSlice(currentSlice,ipIntensity.rotateLeft(),sliceCount);
                                        } // if key.equals(strFrameSamples)
                                        else if (key.equals(strDopplerSamples)) 
                                        {
                                                byteData 	= new byte[32]; 	// Clear array 
                                                inputData.read(byteData,0,4); 
                                                dataLength 	= byteToInt(byteData);
                                                inputData.skip(dataLength);	// read Doppler data 
                                                for (int j = 0; j < lineCount; j++)
                                                {
                                                        for (int k = 0; k < lineLength; k++)
                                                        { 
                                                                inputData.read(byteData, 1, 2);
                                                                pixel               = byteToShort(byteData);
                                                                ipDoppler.putPixel(k,j,pixel); 
                                                        }
                                                }
                                                stackDoppler.addSlice(currentSlice,ipDoppler.rotateLeft(),sliceCount); 
                                        } // if key.equals(strDopplerSamples) 
                                        else 
                                        { 
                                                frameFlag = 1; 
                                        } // else for key.equals() for per-frame data
                                } // while frameFlag == 0
                        } // if key.equals(strFrameData) 
                        currentFrame++; 
                        if (sliceCount > 0) 
                        {
                                if (frameMillisecond[sliceCount] < frameMillisecond[sliceCount-1]) 
                                {
                                        frameDifference[sliceCount-1] = 1000 - frameMillisecond[sliceCount-1]; 
                                } // if millisecond value rolls over into seconds 
                                else 
                                {
                                        frameDifference[sliceCount-1] = frameMillisecond[sliceCount] - frameMillisecond[sliceCount-1];
                                }
                        } // if currentFrame > 2, assumes frame duration is under 1 s 	
                        if (currentFrame > frameCount) 
                        { 
                                int temp 	= 0; 
                                for (int i = 0; i<frameCount; i++) 
                                {
                                        temp += frameDifference[i]; 
                                } // sum of differences
                                frameDuration 	= temp/(frameCount-1);
                        } // frame duration calculated from last 2 frames 
                } // while currentFrame < frameCount

                // Show Stacks
                impIntensity.setSlice(0);                        
                impIntensity.setStack("Oct Intensity Stack", stackIntensity);
                if(show) {
                    impIntensity.show("Oct File Loaded");
                }

                // Crop stack to only show positive or negative frequency data			
                if (bCrop) 
                {
                        IJ.showStatus("Adjusting Image Size"); 
                        IJ.run("Canvas Size...", "width=" + lineCount + " height=" + lineLength/2 + " position=Top-Center zero");
                }

                // Run StackReg plugin (http://bigwww.epfl.ch/thevenaz/stackreg/)
                if (bRegister) 
                {
                        IJ.showStatus("Registering Frames"); 
                        IJ.run("StackReg ", "transformation=[Rigid Body]");
                }

                // Make montage
                if (bMontage) 
                {
                        IJ.showStatus("Making Montage"); 
                        IJ.run("Make Montage...", "columns=" + frameCount + " rows=1 scale=1 first=1 last=10 increment=1 border=0");
                }

                if (bStripMontage) 
                {
                        IJ.showStatus("Making Wolf Montage"); 
                        IJ.run("Canvas Size...", "width=" + lineCount + " height=" + lineLength/2 + " position=Top-Center zero");
                        IJ.run("Canvas Size...", "width=" + lineCount + " height=" + lineLength/8 + " position=Bottom-Center zero");
                        IJ.run("Canvas Size...", "width=" + lineCount + " height=" + lineLength/16 + " position=Top-Center zero");
                        IJ.run("Make Montage...", "columns=" + frameCount + " rows=1 scale=1 first=1 last=" + frameCount + " increment=1 border=0");
                        IJ.run("Size...", "width=" + lineCount/2 + " height=" + lineLength/16 + " interpolate"); 
                }

                if (bScaleBar) 
                { 	// only for m-mode and linear now
                        IJ.showStatus("Creating Scale Bar");
                        IJ.showStatus("Frame Duration: " + frameDuration); 
                        xScaleMax 		= 10.0; // time scale extent in milliseconds
                        yScaleMax 		= 1.0; // depth scale extent in millimeters
                        yScalePixels 	= lineLength / scanDepth;
                        if (scanType == 5)  
                        { // m-mode 
                                if (bStripMontage) 
                                {
                                        xScaleMax 		= 1000.0; // time scale extent in milliseconds
                                        xScalePixels 	= ((double)lineCount/2)/((double)frameCount * (double)frameDuration); 
                                }
                                else 
                                {						
                                        xScalePixels 	= lineCount / frameDuration; 	// lines per millisecond 	
                                } // if/else for bWolfMontage
                        }
                        else if (scanType == 0) 
                        { 	// linear scan 
                                if (scanLength == 0.0) 
                                { 
                                        if (bStripMontage) 
                                        {
                                                xScaleMax 		= 1000.0; // time scale extent in milliseconds
                                                xScalePixels 	= ((double)lineCount/2)/((double)frameCount * (double)frameDuration); 
                                        }
                                        else 
                                        {						
                                                xScalePixels 	= lineCount / frameDuration; 	// lines per millisecond 	
                                        } // if/else for bWolfMontage
                                } // m-mode acquired with linear scan mode 
                                else 
                                { // actually a linear scan 
                                        xScalePixels 	= lineCount/scanLength; 
                                }
                        }
                        IJ.showStatus("scale width = " + (xScaleMax*xScalePixels) ); 
                        IJ.run("Scale Bar...", "width=" + (xScaleMax*xScalePixels) +" height=5 font=12 color=White location=[Upper Left] hide");
                        IJ.run("Scale Bar...", "width=5 height=" + (yScaleMax*yScalePixels) + " font=12 color=White location=[Upper Left] hide");					
                }

                // Create Summed Voxel Projection 
                if (bSVP) 
                {
                        IJ.showStatus("Creating SVP"); 
                        IJ.run("Reslice [/]...", "input=1.000 output=1.000 start=Top"); 		 				// rotate volume 
                        for (int i = 0; i < 5; i++) 
                        { 	// remove frames affected by DC term
                                IJ.run("Delete Slice");    
                        }
                        if (!bCrop) 
                        {
                                IJ.run("Z Project...", "start=1 stop" + (lineLength - 5) + " projection=[Sum Slices]");// create SVP 
                        }
                        else 
                        { 
                                IJ.run("Z Project...", "start=1 stop" + (lineLength/2 - 5) + " projection=[Sum Slices]");// create SVP 
                        }
                        IJ.run("Size...", "width=" + lineCount + " height=" + lineCount + " interpolate"); 	// adjust SVP size to be square
                }

                if (dopplerFlag == 1)
                {
                        impDoppler.setSlice(0);                        
                        impDoppler.setStack("Oct Doppler Stack", stackDoppler);
                        if(show) {
                            impDoppler.show("Oct File Loaded");
                            IJ.run("Canvas Size...", "width=" + lineCount + " height=" + lineLength/2 + " position=Bottom-Center zero");
                        }
                } // if dopplerFlag == 1

                // Close references
                inputData.close(); // close data stream
                inputFile.close(); // close input file stream
        } // if (!bHeaderOnly)
        
        return impIntensity;
    } // Oct function definition

	// Supplementary function definitions
	// byteToDouble uses the same algorithm as below for byteToInt
    public double byteToDouble(byte[] byteArray) {
        byte b1 	= byteArray[0]; 
        byte b2     = byteArray[1]; 
        byte b3     = byteArray[2]; 
        byte b4     = byteArray[3];
        byte b5 	= byteArray[4]; 
        byte b6     = byteArray[5]; 
        byte b7     = byteArray[6];
        byte b8 	= byteArray[7]; 		

        long accum = 0; 
        accum = ((long)((b8 & 0xff)<<56)) | ((long)((b7 & 0xff)<<48)) | ((long)((b6 & 0xff)<<40)) | ((long)((b5 & 0xff)<<32)) | ((long)((b4 & 0xff)<<24)) | ((long)((b3 & 0xff)<<16)) | ((long)((b2 & 0xff)<<8)) | ((long)(b1 & 0xff));	
        return Double.longBitsToDouble(accum);		
    }
	
    public int byteToInt(byte[] byteArray) {
        byte b1      = byteArray[0]; 
        byte b2      = byteArray[1]; 
        byte b3      = byteArray[2]; 
        byte b4      = byteArray[3]; 

        return( ((b4 & 0xff)<<24) | ((b3 & 0xff)<<16) | ((b2 & 0xff)<<8) | (b1 & 0xff));
    }
    
    public short byteToShort(byte[] byteArray){
        byte b1    = byteArray[0]; 
        byte b2    = byteArray[1];

        return ((short) ((b1<<8 & 0xff) | (b2 & 0xff) ));
    }
}