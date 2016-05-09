package org.jax.octvolavg;

import ij.IJ;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

public class DocumentLogger {
    private final StyledDocument logDoc;

    public DocumentLogger(StyledDocument logDoc) {
        this.logDoc = logDoc;
    }
    
    /**
     * This will print to the {@link StyledDocument} that was given to the
     * constructor along with stdout. This function is thread-safe.
     * @param line the line to print
     */
    public void println(String line) {
        System.out.println(line);
        IJ.showStatus(line);
        if(this.logDoc != null) {
            try {
                this.logDoc.insertString(this.logDoc.getLength(), line + "\n", null);
            } catch(BadLocationException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Prints the stack-trace of the given throw
     * @param t the throwable who's stack-trace we will log
     */
    public void printThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        this.println(sw.toString());
    }
}
