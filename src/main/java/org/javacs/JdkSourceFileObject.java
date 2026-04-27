package org.javacs;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.zip.ZipInputStream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class JdkSourceFileObject implements JavaFileObject {
    final JavaFileObject javaFileObject;
    File file;
    InputStream inputStream;

    private static Map<URI, File> files = new ConcurrentHashMap<>();

    public JdkSourceFileObject(JavaFileObject javaFileObject) throws IOException {
        this.javaFileObject = javaFileObject;
        var uri = javaFileObject.toUri();
        file = files.get(uri);
        if (file == null) {
            var url = uri.toURL();
            var urlConnection = (JarURLConnection) url.openConnection();

            String contents = new String(urlConnection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            file = File.createTempFile(urlConnection.getEntryName(), ".java");
            file.deleteOnExit();
            Files.writeString(file.toPath(), contents);
            files.put(uri, file);
            // try (JarInputStream jis = new JarInputStream(urlConnection.getInputStream())) {
            //     JarEntry jarEntry;
            //     while ((jarEntry = jis.getNextJarEntry()) != null) {
            //         // Check if the current entry is the file we want to extract
            //         if (jarEntry.getName().equals(fileNameToExtract)) {
            //             File newFile = new File(destDir, jarEntry.getName());
            //
            //             // Create parent directories if needed (for nested files)
            //             File parent = newFile.getParentFile();
            //             if (!parent.isDirectory() && !parent.mkdirs()) {
            //                 throw new IOException("Failed to create directory " + parent);
            //             }
            //
            //             // Write the extracted file
            //             try (FileOutputStream fos = new FileOutputStream(newFile)) {
            //                 int len;
            //                 while ((len = jis.read(buffer)) > 0) {
            //                     fos.write(buffer, 0, len);
            //                 }
            //             }
            //             System.out.println("Extracted: " + newFile.getAbsolutePath());
            //             break; // Stop after extracting the desired file
            //         }
            //         jis.closeEntry();
            //     }
            // }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other.getClass() != JdkSourceFileObject.class) return false;
        var that = (JdkSourceFileObject) other;
        return this.javaFileObject.equals(that.javaFileObject);
    }

    @Override
    public int hashCode() {
        return this.javaFileObject.hashCode();
    }

    @Override
    public Kind getKind() {
        return javaFileObject.getKind();
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return javaFileObject.isNameCompatible(simpleName, kind);
    }

    @Override
    public NestingKind getNestingKind() {
        return javaFileObject.getNestingKind();
    }

    @Override
    public Modifier getAccessLevel() {
        return javaFileObject.getAccessLevel();
    }

    @Override
    public URI toUri() {
        return javaFileObject.toUri();
    }

    @Override
    public String getName() {
        return javaFileObject.getName();
    }

    @Override
    public InputStream openInputStream() {
        return FileStore.inputStream(file.toPath());
    }

    @Override
    public OutputStream openOutputStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader openReader(boolean _ignoreEncodingErrors) {
        return FileStore.bufferedReader(file.toPath());
    }

    @Override
    public CharSequence getCharContent(boolean _ignoreEncodingErrors) {
        return FileStore.contents(file.toPath());
    }

    @Override
    public Writer openWriter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return javaFileObject.toString();
    }
}
