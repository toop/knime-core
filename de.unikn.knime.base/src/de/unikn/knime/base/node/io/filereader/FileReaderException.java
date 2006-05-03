/* -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   11.01.2006 (ohl): created
 */
package de.unikn.knime.base.node.io.filereader;

import de.unikn.knime.core.data.DataRow;

/**
 * The exception the FileReader (more specificaly the FileRowIterator) throws if
 * something goes wrong.
 * 
 * This is a runtime exception for now.
 * 
 * @author ohl, University of Konstanz
 */
public class FileReaderException extends RuntimeException {

    private final DataRow m_row;

    private final int m_lineNumber;

    /**
     * Always provide a good user message why things go wrong.
     * 
     * @param msg the message to store in the exception.
     */
    FileReaderException(final String msg) {
        super(msg);
        m_row = null;
        m_lineNumber = -1;
    }

    /**
     * Constructor for an exception that stores the last (partial) row where
     * things went wrong.
     * 
     * @param msg the message what went wrong.
     * @param faultyRow the row as far as it got read.
     * @param lineNumber the lineNumber the error occured
     */
    FileReaderException(final String msg, final DataRow faultyRow,
            final int lineNumber) {
        super(msg);
        m_row = faultyRow;
        m_lineNumber = lineNumber;
    }

    /**
     * @return the row that was (possibly partially!) read before things went
     *         wrong. Could be null, if not set.
     */
    DataRow getErrorRow() {
        return m_row;
    }

    /**
     * @return the line number where the error occured in the file. Could be -1
     *         if not set.
     */
    int getErrorLineNumber() {
        return m_lineNumber;
    }
}
