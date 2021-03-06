/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   20.11.2010 (meinl): created
 */
package org.knime.testing.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.eclipse.osgi.internal.loader.ModuleClassLoader;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.junit.Test;
import org.knime.core.node.NodeLogger;

import junit.framework.TestCase;

/**
 * This class collects all classes in the same classpath entry as itself. This can either be a directory (if started
 * from within the IDE) or a JAR file (if started from an Eclipse installation).
 *
 * @author Thorsten Meinl, University of Konstanz
 */
@SuppressWarnings("restriction")
public abstract class AbstractTestcaseCollector {
    private final List<String> m_excludedTestcases = new ArrayList<String>();

    /**
     * Creates a new collector.
     */
    protected AbstractTestcaseCollector() {
    }

    /**
     * Creates new collector. A list of classes that should be excluded can be given.
     *
     * @param excludedTestcases a list with classes to be excluded
     */
    protected AbstractTestcaseCollector(final Class<?>... excludedTestcases) {
        for (Class<?> c : excludedTestcases) {
            m_excludedTestcases.add(c.getName());
        }
    }


    /**
     * Returns a list with classes that contain JUnit test methods or are old-style (JUnit3) testcases.
     *
     * @return a list with classes
     * @throws IOException if an I/O error occurs while collecting the classes
     * @throws ClassNotFoundException if a class could not be found (which would be strange strange)
     */
    public List<Class<?>> getUnittestsClasses() throws IOException, ClassNotFoundException {
        List<String> classnames = getUnittestsClassNames();
        List<Class<?>> classes = new ArrayList<Class<?>>(classnames.size());

        for (String className : classnames) {
            classes.add(getClass().getClassLoader().loadClass(className));
        }

        return classes;
    }


    /**
     * Returns a list with class names that contain JUnit test methods or are old-style (JUnit3) testcases.
     *
     * @return a list with class names
     * @throws IOException if an I/O error occurs while collecting the classes
     */
    public List<String> getUnittestsClassNames() throws IOException {
        ModuleClassLoader cl = (ModuleClassLoader)getClass().getClassLoader();
        ClasspathManager cpm = cl.getClasspathManager();
        String classPath = this.getClass().getName().replace(".", "/") + ".class";

        BundleEntry be = cpm.findLocalEntry(classPath);
        URL localUrl = be.getLocalURL();

        List<String> classNames = new ArrayList<String>();
        if ("file".equals(localUrl.getProtocol())) {
            String path = localUrl.getPath();
            path = path.replaceFirst(Pattern.quote(classPath) + "$", "");
            collectInDirectory(new File(path), "", classNames);
        } else if ("jar".equals(localUrl.getProtocol())) {
            String path = localUrl.getPath().replaceFirst("^file:", "").replaceFirst("\\!.+$", "");
            collectInJar(new JarFile(path), classNames);
        } else {
            throw new IllegalStateException("Cannot read from protocol '" + localUrl.getProtocol() + "'");
        }

        classNames.remove(this.getClass().getName());
        filterClasses(classNames);
        return classNames;
    }

    private void filterClasses(final List<String> classNames) {
        Iterator<String> it = classNames.iterator();
        while (it.hasNext()) {
            String className = it.next();
            if (m_excludedTestcases.contains(className)) {
                it.remove();
            } else {
                try {
                    Class<?> c = Class.forName(className, true, getClass().getClassLoader());
                    // remove abstract and non-public classes, they
                    // cannot be JUnit testcases
                    if (((c.getModifiers() & Modifier.ABSTRACT) != 0) || ((c.getModifiers() & Modifier.PUBLIC) == 0)) {
                        it.remove();
                    } else if (!TestCase.class.isAssignableFrom(c)) {
                        // check if there is at least one test method in the class
                        // if it is not a JUnit3-style testcase
                        boolean testMethodFound = false;
                        for (Method m : c.getMethods()) {
                            if (m.getAnnotation(Test.class) != null) {
                                testMethodFound = true;
                                break;
                            }
                        }
                        if (!testMethodFound) {
                            it.remove();
                        }
                    }
                } catch (ExceptionInInitializerError ex) {
                    it.remove();
                    NodeLogger.getLogger(getClass()).error("Could not load class " + className + ": " + ex.getMessage(),
                        ex);
                } catch (ClassNotFoundException ex) {
                    // strange?!
                    it.remove();
                }
            }
        }
    }

    /**
     * Recursively Collects and returns all classes (excluding inner classes) that are in the specified directory.
     *
     * @param directory the directory
     * @param packageName the package name, initially the empty string
     * @param classNames a list that is filled with class names
     */
    private void collectInDirectory(final File directory, final String packageName, final List<String> classNames) {
        for (File f : directory.listFiles()) {
            if (f.isDirectory()) {
                collectInDirectory(f, packageName + f.getName() + ".", classNames);
            } else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String s = packageName + f.getName().replaceFirst("\\.class$", "");
                classNames.add(s);
            }
        }
    }

    /**
     * Collects and returns all classes inside the given JAR file (excluding inner classes).
     *
     * @param jar the jar file
     * @param classNames a list that is filled with class names
     * @throws IOException if an I/O error occurs
     */
    private void collectInJar(final JarFile jar, final List<String> classNames) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            String s = e.getName();
            if (s.endsWith(".class") && !s.contains("$")) {
                s = s.replaceFirst("\\.class$", "");
                classNames.add(s.replace('/', '.'));
            }
        }
        jar.close();
    }
}
