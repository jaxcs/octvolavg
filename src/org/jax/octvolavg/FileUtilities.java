/*
 * Copyright (c) 2010 The Jackson Laboratory
 * 
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jax.octvolavg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * a group of utility function for working with files
 * @author <A HREF="mailto:keith.sheppard@jax.org">Keith Sheppard</A>
 */
public class FileUtilities
{
    /**
     * our logger
     */
    private static final Logger LOG = Logger.getLogger(
            FileUtilities.class.getName());
    
    /**
     * Create a new temporary directory. Use something like
     * {@link #recursiveDelete(File)} to clean this directory up since it isn't
     * deleted automatically
     * @return  the new directory
     * @throws IOException if there is an error creating the temporary directory
     */
    public static File createTempDir() throws IOException
    {
        final File sysTempDir = new File(System.getProperty("java.io.tmpdir"));
        File newTempDir;
        final int maxAttempts = 9;
        int attemptCount = 0;
        do
        {
            attemptCount++;
            if(attemptCount > maxAttempts)
            {
                throw new IOException(
                        "The highly improbable has occurred! Failed to " +
                        "create a unique temporary directory after " +
                        maxAttempts + " attempts.");
            }
            String dirName = UUID.randomUUID().toString();
            newTempDir = new File(sysTempDir, dirName);
        } while(newTempDir.exists());
        
        if(newTempDir.mkdirs())
        {
            return newTempDir;
        }
        else
        {
            throw new IOException(
                    "Failed to create temp dir named " +
                    newTempDir.getAbsolutePath());
        }
    }
    
    /**
     * Recursively delete file or directory
     * @param fileOrDir
     *          the file or dir to delete
     * @return
     *          true iff all files are successfully deleted
     */
    public static boolean recursiveDelete(File fileOrDir)
    {
        if(fileOrDir.isDirectory())
        {
            // recursively delete contents
            for(File innerFile: fileOrDir.listFiles())
            {
                if(!FileUtilities.recursiveDelete(innerFile))
                {
                    return false;
                }
            }
        }
        
        return fileOrDir.delete();
    }
    
    /**
     * Pipe from one stream to the other
     * @param source
     *          the source stream
     * @param sink
     *          the stream to write to
     * @throws IOException
     *          if the write fails
     */
    public static void writeSourceToSink(
            InputStream source,
            OutputStream sink)
    throws
            IOException
    {
        for(int currByte = source.read();
            currByte != -1;
            currByte = source.read())
        {
            sink.write(currByte);
        }
    }
    
    /**
     * Compress the given file or directory to a zip file.
     * @param fileOrDirectoryToCompress
     *          the directory to compress
     * @param zipOutStream
     *          the output stream
     * @throws IOException
     *          if the compression fails
     */
    public static void zipRecursive(
            File fileOrDirectoryToCompress,
            ZipOutputStream zipOutStream)
    throws
            IOException
    {
        FileUtilities.zipRecursive(
                fileOrDirectoryToCompress,
                zipOutStream,
                "");
    }
    
    /**
     * Compress the given file or directory to a zip file.
     * @param fileOrDirectoryToCompress
     *          the directory to compress
     * @param zipOutStream
     *          the output stream
     * @param fileNamePrefix
     *          a relative directory for the zip entries. should be empty
     *          or end in a "/"
     * @throws IOException
     *          if the compression fails
     */
    public static void zipRecursive(
            File fileOrDirectoryToCompress,
            ZipOutputStream zipOutStream,
            String fileNamePrefix)
    throws
            IOException
    {
        if(!fileOrDirectoryToCompress.exists())
        {
            throw new IOException(
                    fileOrDirectoryToCompress + " does not exist");
        }
        else
        {
            if(fileOrDirectoryToCompress.isFile())
            {
                ZipEntry currEntry = new ZipEntry(
                        fileNamePrefix + fileOrDirectoryToCompress.getName());
                zipOutStream.putNextEntry(currEntry);
                BufferedInputStream buffFileIn = new BufferedInputStream(
                        new FileInputStream(fileOrDirectoryToCompress));
                BufferedOutputStream buffZipOut = new BufferedOutputStream(
                        zipOutStream);
                FileUtilities.writeSourceToSink(
                        buffFileIn,
                        buffZipOut);
                buffZipOut.flush();
                zipOutStream.closeEntry();
            }
            else if(fileOrDirectoryToCompress.isDirectory())
            {
                for(File currListing: fileOrDirectoryToCompress.listFiles())
                {
                    if(currListing.isDirectory())
                    {
                        String currDirNamePrefix =
                            fileNamePrefix + currListing.getName() + "/";
                        ZipEntry currEntry = new ZipEntry(
                                currDirNamePrefix);
                        zipOutStream.putNextEntry(currEntry);
                        zipOutStream.closeEntry();
                        
                        FileUtilities.zipRecursive(
                                currListing,
                                zipOutStream,
                                currDirNamePrefix);
                    }
                    else if(currListing.isFile())
                    {
                        FileUtilities.zipRecursive(
                                currListing,
                                zipOutStream,
                                fileNamePrefix);
                    }
                }
            }
            else
            {
                throw new IOException(
                        "failed to compress: " +
                        fileOrDirectoryToCompress.getAbsolutePath());
            }
        }
    }
    
    /**
     * Compress the given directory to a zip file. Directory recursion is
     * not in this implementation. For recursion use
     * {@link #zipRecursive(File, ZipOutputStream)}.
     * @param directoryToCompress
     *          the directory to compress
     * @param zipOutStream
     *          the output stream
     * @throws IOException
     *          if the compression fails
     */
    public static void compressDirectoryToZip(
            File directoryToCompress,
            ZipOutputStream zipOutStream)
    throws
            IOException
    {
        if(!directoryToCompress.isDirectory())
        {
            throw new IOException(
                    directoryToCompress + " is not an existing directory");
        }
        else
        {
            for(File currListing: directoryToCompress.listFiles())
            {
                if(currListing.isDirectory())
                {
                    if(LOG.isLoggable(Level.FINE))
                    {
                        LOG.fine(
                                "skipping " + currListing + " because " +
                                "directory recursion is not supported");
                    }
                }
                else if(currListing.isFile())
                {
                    ZipEntry currEntry = new ZipEntry(currListing.getName());
                    zipOutStream.putNextEntry(currEntry);
                    BufferedInputStream buffFileIn = new BufferedInputStream(
                            new FileInputStream(currListing));
                    BufferedOutputStream buffZipOut = new BufferedOutputStream(
                            zipOutStream);
                    FileUtilities.writeSourceToSink(
                            buffFileIn,
                            buffZipOut);
                    buffZipOut.flush();
                    zipOutStream.closeEntry();
                }
                else
                {
                    throw new IOException(
                            "failed to compress: " + currListing);
                }
            }
        }
    }
    
    /**
     * Expand the given zip. This function throws an IO exception if
     * the target directory doesn't already exist
     * @param zipInStream
     *          the zip configuration input stream
     * @param targetDirectory
     *          the directory that we're expanding to
     * @throws IOException
     *          if we get an {@link IOException} while trying
     *          to expand the zip file, or if the target directory doesn't
     *          exist yet
     */
    public static void unzipToDirectory(
            ZipInputStream zipInStream,
            File targetDirectory)
    throws
            IOException
    {
        if(!targetDirectory.isDirectory())
        {
            throw new IOException(
                    targetDirectory + " is not an existing directory");
        }
        else
        {
            for(ZipEntry currEntry = zipInStream.getNextEntry();
                currEntry != null;
                currEntry = zipInStream.getNextEntry())
            {
                File currFile = new File(targetDirectory, currEntry.getName());
                
                if(currEntry.isDirectory())
                {
                    currFile.mkdirs();
                }
                else
                {
                    currFile.getParentFile().mkdirs();
                    BufferedOutputStream buffFileOut = new BufferedOutputStream(
                            new FileOutputStream(currFile));
                    BufferedInputStream buffZipIn = new BufferedInputStream(
                            zipInStream);
                    FileUtilities.writeSourceToSink(
                            buffZipIn,
                            buffFileOut);
                    buffFileOut.close();
                }
            }
        }
    }
    
    /**
     * Unzip the contents of the given input stream to the given output stream
     * @param zipInStream
     *          the stream we're unzipping
     * @param zipOutStream
     *          the stream we're zipping
     * @throws IOException
     *          if the underlying streams throw an exception
     */
    public static void unzipToZip(
            ZipInputStream zipInStream,
            ZipOutputStream zipOutStream)
    throws
            IOException
    {
        for(ZipEntry currEntry = zipInStream.getNextEntry();
        currEntry != null;
        currEntry = zipInStream.getNextEntry())
        {
            ZipEntry newEntry = new ZipEntry(currEntry.getName());
            zipOutStream.putNextEntry(newEntry);
            if(!currEntry.isDirectory())
            {
                BufferedInputStream buffZipIn = new BufferedInputStream(
                        zipInStream);
                BufferedOutputStream buffZipOut = new BufferedOutputStream(
                        zipOutStream);
                FileUtilities.writeSourceToSink(buffZipIn, buffZipOut);
                buffZipOut.flush();
            }
            zipOutStream.closeEntry();
        }
    }
    
    /**
     * A tester main for zipping and unzipping
     * @param args  the args
     * @throws FileNotFoundException
     *          if we cant find the file
     * @throws IOException
     *          if we get an io exception
     */
    public static void main(String[] args) throws FileNotFoundException, IOException
    {
        if(args[0].equals("zip"))
        {
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(args[1]));
            FileUtilities.zipRecursive(
                    new File(args[2]),
                    zipOut);
            zipOut.flush();
            zipOut.close();
        }
        else if(args[0].equals("unzip"))
        {
            FileUtilities.unzipToDirectory(
                    new ZipInputStream(new FileInputStream(args[1])),
                    new File("."));
        }
        else if(args[0].equals("ziptozip"))
        {
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(args[1]));
            FileUtilities.unzipToZip(
                    new ZipInputStream(new FileInputStream(args[2])),
                    zipOut);
            zipOut.flush();
            zipOut.close();
        }
        else
        {
            System.err.println("expected arguments: zip-or-unzip-or-ziptozip zipfilename [infilename-or-dir]");
        }
    }
}