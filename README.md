The OCT Volume Averager plugin registers and averages optical coherence
tomography (OCT) volume datasets that are acquired by repeated scanning
from a single eye to improve the detection of fine detail. The plugin relies
on the TurboReg and StackReg plugins to perform image registration. OCT
Volume Averager is most useful for efficient processing of input data that
are organized to include repeated scans from one or more eyes in a single
folder. However, it also accepts data opened in ImageJ/Fiji. Currently the
plugin has been tested on OCT volumes acquired with a Bioptigen Envisu R2200
UHR-SDOIS instrument and includes the Bioptigen OCT Reader plugin for ImageJ
to convert Bioptigen *.oct files to *.tif format. OCT Volume Averager may be
applied in principle to repeated OCT volume scans obtained in *.tif format
from other instruments.

The TurboReg and StackReg plugins were developed by P. Thévenaz and
coworkers.  See the following websites for more information:
http://bigwww.epfl.ch/thevenaz/turboreg/
http://bigwww.epfl.ch/thevenaz/stackreg/

The OCT_Reader plugin for ImageJ was developed by Bioptigen:
Copyright 2010 Bioptigen, Inc.
Author: Bradley Bower

All other code was developed at The Jackson Laboratory:
Copyright 2013-2014 The Jackson Laboratory.
Authors: Keith Sheppard, Mei Xiao and Mark Krebs

# License

Consult the license.txt file included with this repository for licensing terms.

# Building OCT Volume Averager as an ImageJ/Fiji Plugin

Note: OCT Volume Averager has only been tested on OS X 10.10 and Windows 7 (64-bit JVM)

In order to build OCT Volume Averager you will need a copy of eclipse installed then you can follow these steps:

* import the eclipse project which you can find in the root directory of this repository
* within eclipse's project explorer you will find a file called `buildjarplugin.jardesc`. Double click this file which will open a "JAR Export" dialog
* build the jar by clicking the "Finish" button at the bottom of the dialog

You should now have a `OCT_Vol_Avg_Plugin.jar` file in the project's root directory.

# Building OCT Volume Averager as a Stand-Alone Application

This works much the same as building a plugin except that you use `buildjar.jardesc`
rather than `buildjarplugin.jardesc`. In order to build OCT Volume Averager plugin
you will need a copy of eclipse installed then you can follow these steps:

* import the eclipse project which you can find in the root directory of this repository
* within eclipse's project explorer you will find a file called `buildjar.jardesc`. Double click this file which will open a "JAR Export" dialog
* build the jar by clicking the "Finish" button at the bottom of the dialog

You can now run OCT Volume Averager using the octvolavg.bash script in the root of this repository.

## Bundling the Application into a zip file

* create a directory like octvolavg-<version>
* copy the following into this directory
    * lib/
    * OCT_Vol_Avg.jar
    * octvolavg.bat (Windows users should double click this file to start the application)
    * octvolavg.bash (OS X and Linux users should start the application using this bash script)
* zip up the directory like: `zip -r octvolavg-<version>.zip octvolavg-win-<version>`

Running OCT Volume Averager requires a 64-bit JVM which you can download for windows at:
http://www.java.com/en/download/manual.jsp
