package org.hps.crawler;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.hps.datacat.client.DatasetFileFormat;

/**
 * Visitor which creates a {@link FileSet} from walking a directory tree.
 * <p>
 * Any number of {@link java.io.FileFilter} objects can be registered with this visitor to restrict which files are
 * accepted.
 *
 * @author Jeremy McCormick, SLAC
 */
final class CrawlerFileVisitor extends SimpleFileVisitor<Path> {

    /**
     * The run log containing information about files from each run.
     */
    private final FileSet fileSet = new FileSet();

    /**
     * A list of file filters to apply.
     */
    private final List<FileFilter> filters = new ArrayList<FileFilter>();

    /**
     * Run the filters on the file to tell whether it should be accepted or not.
     *
     * @param file the EVIO file
     * @return <code>true</code> if file should be accepted
     */
    private boolean accept(final File file) {
        boolean accept = true;
        for (final FileFilter filter : this.filters) {
            accept = filter.accept(file);
            if (!accept) {
                break;
            }
        }
        return accept;
    }

    /**
     * Add a file filter.
     *
     * @param filter the file filter
     */
    void addFilter(final FileFilter filter) {
        this.filters.add(filter);
    }

    /**
     * Get the file set created by visiting the directory tree.
     *
     * @return the file set from visiting the directory tree
     */
    FileSet getFileSet() {
        return this.fileSet;
    }

    /**
     * Visit a single file.
     *
     * @param path the file to visit
     * @param attrs the file attributes
     */
    @Override
    public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) {
        final File file = path.toFile();
        if (this.accept(file)) {
            final DatasetFileFormat format = DatacatUtilities.getFileFormat(file);
            fileSet.addFile(format, file);
        }
        return FileVisitResult.CONTINUE;
    }
}
