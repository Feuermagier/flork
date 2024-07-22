package de.firemage.flork.compiler;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class VirtualFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, ByteArrayOutputStream> outStreams = new HashMap<>();

    protected VirtualFileManager(StandardJavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind) {
            @Override
            public OutputStream openOutputStream() {
                ByteArrayOutputStream outStream = outStreams.get(className);
                if (outStream == null) {
                    outStream = new ByteArrayOutputStream();
                    outStreams.put(className, outStream);
                }
                return outStream;
            }
        };
    }

    public Map<String, byte[]> getCompiledClasses() {
        Map<String, byte[]> compiledClasses = new HashMap<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : outStreams.entrySet()) {
            compiledClasses.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return compiledClasses;
    }
}
